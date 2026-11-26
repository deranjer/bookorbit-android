package com.bookorbit.feature.player

import android.content.Context
import android.os.Bundle
import android.os.PowerManager
import androidx.media3.common.MediaItem
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.bookorbit.core.auth.SecureStorage
import com.bookorbit.core.auth.SessionManager
import com.bookorbit.core.model.AuthUser
import com.bookorbit.core.network.AuthInterceptor
import com.bookorbit.core.network.BaseUrlInterceptor
import com.bookorbit.core.network.TokenAuthenticator
import com.bookorbit.core.network.TokenRefresher
import dagger.Lazy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Real HTTP Range + MediaSession seek with the screen asleep; not physical Bluetooth acceptance. */
@androidx.annotation.OptIn(UnstableApi::class)
class StreamingSeekTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun <T> main(block: () -> T): T {
        val result = AtomicReference<T>()
        instrumentation.runOnMainSync { result.set(block()) }
        return result.get()
    }
    private fun await(label: String, diagnostic: (() -> String)? = null, block: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        while (System.nanoTime() < deadline) {
            if (main(block)) return
            Thread.sleep(25)
        }
        fail("Timed out: $label ${main { diagnostic?.invoke().orEmpty() }}")
    }
    private fun key(name: String) {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand("input keyevent $name")
        ).use { it.readBytes() }
    }

    @Test fun wrapperDoesNotLoseSyntheticPreviousWhenUpstreamCommandsChange() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val raw = main { ExoPlayer.Builder(context).build() }
        val listeners = mutableListOf<Player.Listener>()
        val wrapper = main {
            val upstream = object : ForwardingPlayer(raw) {
                override fun addListener(listener: Player.Listener) { listeners.add(listener) }
                override fun removeListener(listener: Player.Listener) { listeners.remove(listener) }
                override fun getAvailableCommands(): Player.Commands = Player.Commands.EMPTY
            }
            BookAggregatingPlayer(upstream, MutableStateFlow(AudioSettings(previousTrackAction = PreviousTrackAction.SKIP_BACK)))
        }
        val received = mutableListOf<Player.Commands>()
        val listener = object : Player.Listener {
            override fun onAvailableCommandsChanged(availableCommands: Player.Commands) { received += availableCommands }
        }
        try {
            main { wrapper.addListener(listener) }
            repeat(3) {
                main { listeners.toList().forEach { it.onAvailableCommandsChanged(Player.Commands.EMPTY) } }
                instrumentation.waitForIdleSync()
                main {
                    assertTrue(received.last().contains(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM))
                    assertTrue(received.last().contains(Player.COMMAND_SEEK_TO_PREVIOUS))
                }
            }
            main {
                wrapper.removeListener(listener)
                val count = received.size
                listeners.toList().forEach { it.onAvailableCommandsChanged(Player.Commands.EMPTY) }
                assertEquals("Removed listeners must not receive commands", count, received.size)
            }
        } finally { main { wrapper.detach(); raw.release() } }
    }

    @Test fun screenOffRewindRefreshesExpiredAuthorizationAndRetainsQueueClock() {
        scenario { context, session, server, factory, expected, refreshes, ranges ->
            var raw: ExoPlayer? = null
            var wrapper: BookAggregatingPlayer? = null
            var media: MediaSession? = null
            var controller: MediaController? = null
            val item = MediaItem.Builder().setMediaId("file-84")
                .setUri(server.url("/prefix/api/v1/audiobooks/5/assets/84/content").toString())
                .setMediaMetadata(MediaMetadata.Builder().setTitle("Synthetic streamed book").setExtras(Bundle().apply {
                    putDouble(BookAggregatingPlayer.EXTRA_DURATION_SEC, 180.0)
                }).build()).build()
            try {
                val player = main {
                    ExoPlayer.Builder(context, audioRenderersFactory(context))
                        .setMediaSourceFactory(DefaultMediaSourceFactory(factory)).build().also {
                            raw = it
                            it.setMediaItem(item, 60_000)
                            it.prepare()
                            it.play()
                        }
                }
                val book = main {
                    BookAggregatingPlayer(player, MutableStateFlow(AudioSettings(previousTrackAction = PreviousTrackAction.SKIP_BACK)), { listOf(item) })
                        .also { wrapper = it }
                }
                val serviceSession = main { MediaSession.Builder(context, book).build().also { media = it } }
                val client = main { MediaController.Builder(context, serviceSession.token).buildAsync() }.get(10, TimeUnit.SECONDS)
                controller = client
                fun state() = "raw(state=${player.playbackState},play=${player.playWhenReady},playing=${player.isPlaying}," +
                    "position=${player.currentPosition},suppression=${player.playbackSuppressionReason}); " +
                    "client(state=${client.playbackState},playing=${client.isPlaying},position=${client.currentPosition}," +
                    "previous=${client.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)}); requests=${ranges.size}"
                await("stream ready at 60 seconds", ::state) {
                    player.playerError?.let { fail(it.errorCodeName) }
                    player.isPlaying && player.currentPosition >= 60_000 && client.playbackState == Player.STATE_READY
                }
                key("KEYCODE_SLEEP")
                await("screen off") { !context.getSystemService(PowerManager::class.java).isInteractive }
                val before = main { book.currentPosition }
                val requestsBefore = ranges.size
                expected.set("synthetic-rotated")
                main { client.seekBack() }
                await("Range reopened, token refreshed, playback continues without waking screen", ::state) {
                    player.playerError?.let { fail("screen-off seek: ${it.errorCodeName}") }
                    refreshes.get() == 1 && ranges.size >= requestsBefore + 2 && player.isPlaying &&
                        book.currentPosition in (before - 10_500)..(before - 7_000) && client.playbackState == Player.STATE_READY
                }
                val rejected = ranges[requestsBefore]
                val retried = ranges[requestsBefore + 1]
                assertNotNull(rejected)
                assertEquals("Authorization retry must preserve Range", rejected, retried)
                assertFalse(context.getSystemService(PowerManager::class.java).isInteractive)
                await("session retains the synthetic previous command after seek", ::state) {
                    client.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                }
                main {
                    assertEquals(1, client.mediaItemCount)
                    assertEquals("file-84", client.currentMediaItem!!.mediaId)
                    assertEquals(item.localConfiguration!!.uri, player.currentMediaItem!!.localConfiguration!!.uri)
                    client.seekToPreviousMediaItem()
                }
                await("headset previous configured as skip-back remains usable", ::state) {
                    player.playerError?.let { fail(it.errorCodeName) }
                    player.isPlaying && book.currentPosition < before - 17_000
                }
                assertEquals(1, refreshes.get())
                assertEquals("synthetic-rotated", session.accessToken)
            } finally {
                key("KEYCODE_WAKEUP")
                main { controller?.release(); media?.release(); wrapper?.detach(); raw?.release() }
            }
        }
    }

    @Test fun reusedDataSourceReadsLiveTokenAndRejectsAnAccountSwitch() {
        scenario { _, session, server, factory, expected, _, _ ->
            val source = factory.createDataSource()
            val uri = server.url("/prefix/api/v1/audiobooks/5/assets/84/content").toString()
            fun read(position: Long) {
                try {
                    source.open(DataSpec.Builder().setUri(uri).setPosition(position).setLength(32).build())
                    assertEquals(32, source.read(ByteArray(32), 0, 32))
                } finally { source.close() }
            }
            read(0)
            expected.set("synthetic-new-live")
            session.updateAccessToken("synthetic-new-live")
            read(44)
            session.signIn("synthetic-other-account", AuthUser(8, "other-audio-test"))
            assertThrows(java.io.IOException::class.java) { read(64) }
        }
    }

    private fun scenario(block: (Context, SessionManager, MockWebServer, androidx.media3.datasource.DataSource.Factory,
        AtomicReference<String>, AtomicInteger, CopyOnWriteArrayList<String?>) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "stream-seek-test-${UUID.randomUUID()}"
        val alias = "$name-key"
        try {
            val session = SessionManager(SecureStorage(context, name, alias, "$name-owner"), Json { ignoreUnknownKeys = true })
            MockWebServer().use { server ->
                session.setServerUrl(server.url("/prefix").toString())
                session.signIn("synthetic-old", AuthUser(7, "audio-test"))
                val expected = AtomicReference("synthetic-old")
                val refreshes = AtomicInteger()
                val ranges = CopyOnWriteArrayList<String?>()
                val bytes = wav()
                server.dispatcher = object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        if (request.path == "/prefix/api/v1/auth/refresh") {
                            refreshes.incrementAndGet()
                            return MockResponse().setBody("""{"accessToken":"${expected.get()}"}""")
                        }
                        if (request.path != "/prefix/api/v1/audiobooks/5/assets/84/content") return MockResponse().setResponseCode(404)
                        val range = request.getHeader("Range")
                        ranges += range
                        if (request.getHeader("Authorization") != "Bearer ${expected.get()}") return MockResponse().setResponseCode(401)
                        val match = range?.let { Regex("bytes=(\\d+)-(\\d*)").matchEntire(it) }
                        val start = match?.groupValues?.get(1)?.toInt() ?: 0
                        val end = match?.groupValues?.get(2)?.takeIf { it.isNotEmpty() }?.toInt() ?: bytes.lastIndex
                        val last = minOf(end, bytes.lastIndex)
                        return MockResponse().setResponseCode(if (match != null) 206 else 200)
                            .setHeader("Content-Type", "audio/wav").setHeader("Accept-Ranges", "bytes")
                            .apply { if (match != null) setHeader("Content-Range", "bytes $start-$last/${bytes.size}") }
                            .setBody(Buffer().write(bytes, start, last - start + 1))
                    }
                }
                val refresher = TokenRefresher(session, Lazy { OkHttpClient() }, Json { ignoreUnknownKeys = true })
                val main = OkHttpClient.Builder().addInterceptor(BaseUrlInterceptor(session)).addInterceptor(AuthInterceptor(session))
                    .authenticator(TokenAuthenticator(refresher)).build()
                block(context, session, server, audioStreamingFactory(main, session), expected, refreshes, ranges)
            }
        } finally {
            context.deleteSharedPreferences(name)
            context.deleteSharedPreferences("$name-owner")
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
        }
    }
    private fun wav(): ByteArray {
        val length = 8_000 * 2 * 180
        return ByteBuffer.allocate(44 + length).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()).putInt(36 + length).put("WAVEfmt ".toByteArray())
            putInt(16).putShort(1).putShort(1).putInt(8_000).putInt(16_000)
            putShort(2).putShort(16).put("data".toByteArray()).putInt(length).put(ByteArray(length))
        }.array()
    }
}
