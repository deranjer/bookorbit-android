package com.bookorbit.feature.reader

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.bookorbit.feature.player.audioRenderersFactory
import com.bookorbit.feature.player.SoftwareAudioFallback
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.bookorbit.core.auth.SecureStorage
import com.bookorbit.core.auth.SessionManager
import com.bookorbit.core.model.AuthUser
import com.bookorbit.core.storage.LocalRef
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@androidx.annotation.OptIn(UnstableApi::class)
class LocalNativeNarrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun <T> main(block: () -> T): T {
        val value = AtomicReference<T>()
        instrumentation.runOnMainSync { value.set(block()) }
        return value.get()
    }
    private fun await(label: String, block: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        while (System.nanoTime() < deadline) {
            if (main(block)) return
            Thread.sleep(25)
        }
        fail("Timed out: $label")
    }

    @Test fun localEpubPlaysAndRestoresExactClipsWithAnUnreachableServer() = runBlocking {
        fixture { context, account, epub, bytes ->
            val accountOwner = account.owner()!!
            val owner = ReadAlongOwner(accountOwner.serverUrl, accountOwner.userId, 84, sha(bytes))
            val loader = LocalNativeNarration(context, account)
            val source = loader.open(LocalRef.PlainFile(epub), 5, owner, accountOwner)
            var player: ExoPlayer? = null
            var wrapper: NativeNarrationPlayer? = null
            try {
                // Active audio is read from the verified snapshot, not the mutable original file.
                epub.writeText("replaced after opening")
                val queue = NativeNarrationQueue(source, "Offline SMIL")
                val raw = main {
                    val renderers = audioRenderersFactory(context)
                    val selector = DefaultTrackSelector(context)
                    ExoPlayer.Builder(context, renderers).setTrackSelector(selector).build().also {
                        player = it
                        it.addListener(SoftwareAudioFallback(it, selector, renderers))
                        it.setMediaSources(queue.items.map { item -> queue.sourceFactory().createMediaSource(item) }, 0, 1_250)
                        it.prepare()
                    }
                }
                val native = main { NativeNarrationPlayer(raw, queue, { account.owns(accountOwner) }, {}).also { wrapper = it } }
                await("offline clip ready") {
                    raw.playerError?.let { fail(it.errorCodeName) }
                    raw.playbackState == Player.STATE_READY
                }
                val saved = main { native.checkpoint()!! }
                assertEquals("OPS/ch.xhtml#a", saved.fragment)
                assertEquals(3.25, saved.audioSeconds, 0.01)
                val selected = source.timeline.checkpoint(1, 0, 1.0)!!
                main {
                    assertFalse(native.seekToSelection(selected.copy(audioSeconds = 5.2)))
                    assertTrue(native.seekToSelection(selected))
                    native.play()
                }
                await("selection starts on existing player") { native.isPlaying && native.currentMediaItemIndex == 1 }
                main { native.pause(); assertTrue(native.seekToSelection(source.timeline.checkpoint(0, 0, 1.0)!!)) }
                await("paused selection rewinds to exact fragment") { native.currentMediaItemIndex == 0 && native.playbackState == Player.STATE_READY }
                main { assertEquals(2.0, native.checkpoint()!!.audioSeconds, 0.01); assertEquals(queue.items.size, raw.mediaItemCount) }
                main { native.seekTo(0, 2_850); native.play() }
                await("offline natural SMIL transition") { native.currentMediaItemIndex == 1 && native.isPlaying }
                main { native.pause() }
                main { native.seekTo(1, 2_900); native.play() }
                await("offline book ends") { native.playbackState == Player.STATE_ENDED }
                main { native.seekTo(0, 0); native.play() }
                await("restart after ended retains audio") { native.isPlaying && native.currentMediaItemIndex == 0 }
                // Fresh source/queue restoration uses exact resource time, not chapter percentage.
                epub.writeBytes(bytes)
                val reopened = loader.open(LocalRef.PlainFile(epub), 5, owner, accountOwner)
                try {
                    val location = NativeNarrationQueue(reopened, "Restored").initialPosition(saved, 0)!!
                    assertEquals(0, location.index)
                    assertEquals(1_250L, location.clipOffsetMs)
                    assertEquals(3.25, location.assetSeconds, 0.01)
                } finally { reopened.close() }
            } finally {
                main { wrapper?.detach(); player?.release() }
                source.close()
            }
            assertTrue(File(context.cacheDir, "native-narration").listFiles().orEmpty().isEmpty())
        }
    }

    @Test fun revisionsResourceIdentityAndAccountChangesAreRejectedWithoutNetworkFallback() = runBlocking {
        fixture { context, account, epub, bytes ->
            val accountOwner = account.owner()!!
            val owner = ReadAlongOwner(accountOwner.serverUrl, accountOwner.userId, 84, sha(bytes))
            val loader = LocalNativeNarration(context, account)
            try {
                loader.open(LocalRef.PlainFile(epub), 5, owner.copy(fingerprint = "wrong"), accountOwner)
                fail("Changed revision accepted")
            } catch (_: IllegalArgumentException) { }
            val source = loader.open(LocalRef.PlainFile(epub), 5, owner, accountOwner)
            try {
                val data = source.dataSourceFactory().createDataSource()
                try {
                    val length = data.open(DataSpec.Builder().setUri(source.resourceUri("OPS/audio.wav")).setPosition(44).setLength(32).build())
                    assertEquals(32L, length)
                    assertEquals(32, data.read(ByteArray(32), 0, 32))
                    account.signOut()
                    assertThrows(java.io.IOException::class.java) { data.read(ByteArray(8), 0, 8) }
                } finally { data.close() }
            } finally { source.close() }
            assertTrue(File(context.cacheDir, "native-narration").listFiles().orEmpty().isEmpty())
        }
    }

    private suspend fun fixture(block: suspend (Context, SessionManager, File, ByteArray) -> Unit) {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val name = "offline-narration-test-${UUID.randomUUID()}"
        val root = File(base.cacheDir, name).apply { mkdirs() }
        val context = object : ContextWrapper(base) { override fun getCacheDir() = root }
        val alias = "$name-key"
        try {
            val account = SessionManager(SecureStorage(context, name, alias, "$name-owner"), Json { ignoreUnknownKeys = true })
            account.setServerUrl("http://127.0.0.1:1")
            account.signIn("synthetic-local-token", AuthUser(7, "offline-test"))
            val epub = File(root, "source.epub")
            ZipOutputStream(epub.outputStream()).use { zip ->
                fun entry(name: String, bytes: ByteArray) { zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() }
                fun xml(name: String, value: String) = entry(name, value.toByteArray())
                xml("META-INF/container.xml", """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OPS/package.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""")
                xml("OPS/package.opf", """<package xmlns="http://www.idpf.org/2007/opf"><manifest><item id="ch" href="ch.xhtml" media-type="application/xhtml+xml" media-overlay="sm"/><item id="sm" href="ch.smil" media-type="application/smil+xml"/><item id="audio" href="audio.wav" media-type="audio/wav"/></manifest><spine><itemref idref="ch"/></spine></package>""")
                xml("OPS/ch.xhtml", "<html/>")
                xml("OPS/ch.smil", """<smil xmlns="http://www.w3.org/ns/SMIL"><body><seq><par><text src="ch.xhtml#a"/><audio src="audio.wav" clipBegin="2s" clipEnd="5s"/></par><par><text src="ch.xhtml#b"/><audio src="audio.wav" clipBegin="5s" clipEnd="8s"/></par></seq></body></smil>""")
                val length = 8_000 * 2 * 10
                entry("OPS/audio.wav", ByteBuffer.allocate(44 + length).order(ByteOrder.LITTLE_ENDIAN).apply {
                    put("RIFF".toByteArray()).putInt(36 + length).put("WAVEfmt ".toByteArray())
                    putInt(16).putShort(1).putShort(1).putInt(8_000).putInt(16_000)
                    putShort(2).putShort(16).put("data".toByteArray()).putInt(length).put(ByteArray(length))
                }.array())
            }
            block(context, account, epub, epub.readBytes())
        } finally {
            root.walkBottomUp().forEach { it.delete() }
            base.deleteSharedPreferences(name)
            base.deleteSharedPreferences("$name-owner")
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
        }
    }
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
