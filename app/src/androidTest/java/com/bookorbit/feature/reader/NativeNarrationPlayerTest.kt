package com.bookorbit.feature.reader

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bookorbit.core.auth.SecureStorage
import com.bookorbit.core.auth.SessionManager
import com.bookorbit.core.model.AuthUser
import com.bookorbit.core.network.AuthInterceptor
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Real ExoPlayer clipping + MediaSession transport, synthetic silent PCM; not an audible-output test. */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(UnstableApi::class)
class NativeNarrationPlayerTest {
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

    @Test fun exactClipClockChapterTransportAndNaturalTransitionShareOnePlayer() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "native-player-test-${UUID.randomUUID()}"
        val alias = "$name-key"
        val json = Json { ignoreUnknownKeys = true }
        var exo: ExoPlayer? = null
        var session: MediaSession? = null
        var controller: MediaController? = null
        var wrapper: NativeNarrationPlayer? = null
        try {
            val account = SessionManager(SecureStorage(context, name, alias, "$name-owner"), json)
            MockWebServer().use { server ->
                account.setServerUrl(server.url("/").toString())
                account.signIn("synthetic-clip-token", AuthUser(7, "synthetic-player"))
                val owner = account.owner()!!
                val wav = wav(15)
                server.dispatcher = object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        if (request.getHeader("Authorization") != "Bearer synthetic-clip-token") return MockResponse().setResponseCode(401)
                        if (request.path != "/api/v1/epub/5/media-overlay/file/OPS/audio.wav?fileId=84")
                            return MockResponse().setResponseCode(404)
                        return MockResponse().setHeader("Content-Type", "audio/wav").setBody(Buffer().write(wav))
                    }
                }
                val clips = listOf(
                    NativeNarrationClip(0, 0, "OPS/one.smil", "OPS/one.xhtml", "a", "OPS/audio.wav", "audio/wav", 2.0, 5.0, 3.0),
                    NativeNarrationClip(1, 0, "OPS/one.smil", "OPS/one.xhtml", "b", "OPS/audio.wav", "audio/wav", 5.0, 8.0, 3.0),
                    NativeNarrationClip(2, 2, "OPS/two.smil", "OPS/two.xhtml", "c", "OPS/audio.wav", "audio/wav", 10.0, 13.0, 3.0),
                )
                val timeline = NativeNarrationTimeline(NativeNarrationPlaylist(5, 84, 9.0, clips,
                    listOf(NativeNarrationSection(0, "OPS/one.xhtml", "One", "OPS/one.smil", 0.0, 6.0),
                        NativeNarrationSection(2, "OPS/two.xhtml", "Two", "OPS/two.smil", 6.0, 3.0)),
                    listOf(NativeNarrationResource("OPS/audio.wav", "audio/wav", wav.size.toLong()))), 5, 84)
                val plan = NativeNarrationPlan(timeline, owner, account,
                    OkHttpClient.Builder().addInterceptor(AuthInterceptor(account)).build())
                val queue = NativeNarrationQueue(plan, "Synthetic SMIL")
                var foregroundStarts = 0
                val player = main {
                    ExoPlayer.Builder(context).build().also { raw ->
                        exo = raw
                        raw.setMediaSources(queue.items.map { queue.sourceFactory().createMediaSource(it) }, 0, 1_250)
                        raw.prepare()
                    }
                }
                val native = main {
                    NativeNarrationPlayer(player, queue, { account.owns(owner) }, { foregroundStarts++ }).also { wrapper = it }
                }
                val media = main { MediaSession.Builder(context, native).build().also { session = it } }
                controller = main { MediaController.Builder(context, media.token).buildAsync() }.get(10, TimeUnit.SECONDS)
                val client = controller!!
                await("first clipped item ready") { player.playbackState == Player.STATE_READY && client.mediaItemCount == 3 }
                main {
                    assertEquals(3_000L, native.duration)
                    val checkpoint = native.checkpoint()!!
                    assertEquals("OPS/one.xhtml#a", checkpoint.fragment)
                    assertEquals(3.25, checkpoint.audioSeconds, 0.01)
                    client.seekToNextMediaItem()
                }
                await("system next skips to next narrated chapter") { native.currentMediaItemIndex == 2 && player.playbackState == Player.STATE_READY }
                main { client.seekToPreviousMediaItem() }
                await("system previous returns to chapter start, not last fragment") { native.currentMediaItemIndex == 0 && player.playbackState == Player.STATE_READY }
                main { client.seekTo(0, 2_850); client.play() }
                await("natural clipped transition") { native.currentMediaItemIndex == 1 && native.isPlaying }
                main { client.pause() }
                await("session pause") { !native.playWhenReady }
                main {
                    val checkpoint = native.checkpoint()!!
                    assertEquals("OPS/one.xhtml#b", checkpoint.fragment)
                    assertTrue(checkpoint.audioSeconds in 5.0..8.0)
                    assertTrue("Foreground hook must run before native playback", foregroundStarts > 0)
                    native.detach()
                    assertNull(native.checkpoint())
                    native.play()
                    assertFalse(player.playWhenReady)
                    // Detaching the wrapper must not release the shared service-owned player.
                    player.seekTo(2, 0)
                }
                await("delegate survives wrapper detach") { player.currentMediaItemIndex == 2 && player.playbackState == Player.STATE_READY }
                main {
                    val guarded = NativeNarrationPlayer(player, queue, { account.owns(owner) }, { foregroundStarts++ })
                    account.signIn("another-synthetic-token", AuthUser(8, "another-synthetic-player"))
                    val startsBefore = foregroundStarts
                    guarded.play()
                    guarded.playWhenReady = true
                    assertFalse("Old account cannot resume buffered native audio", player.playWhenReady)
                    assertEquals(startsBefore, foregroundStarts)
                    guarded.detach()
                }
            }
        } finally {
            main { controller?.release(); wrapper?.detach(); session?.release(); exo?.release() }
            context.deleteSharedPreferences(name)
            context.deleteSharedPreferences("$name-owner")
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
        }
    }

    private fun wav(seconds: Int): ByteArray {
        val length = 8_000 * 2 * seconds
        return ByteBuffer.allocate(44 + length).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()).putInt(36 + length).put("WAVEfmt ".toByteArray())
            putInt(16).putShort(1).putShort(1).putInt(8_000).putInt(16_000)
            putShort(2).putShort(16).put("data".toByteArray()).putInt(length)
            put(ByteArray(length))
        }.array()
    }
}
