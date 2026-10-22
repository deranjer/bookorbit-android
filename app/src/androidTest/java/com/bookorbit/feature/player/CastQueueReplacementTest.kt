package com.bookorbit.feature.player

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import android.os.Bundle
import com.google.common.util.concurrent.Futures
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** A real controller/session with a delayed queue load, as on a physical Cast receiver. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class CastQueueReplacementTest {
    private class DelayedQueuePlayer(val exo: ExoPlayer) : ForwardingPlayer(exo) {
        var pending: Triple<List<MediaItem>, Int, Long>? = null
        override fun setMediaItems(items: List<MediaItem>, startIndex: Int, startPositionMs: Long) {
            pending = Triple(items, startIndex, startPositionMs)
        }
        fun complete() {
            val load = checkNotNull(pending)
            pending = null
            exo.setMediaItems(load.first, load.second, load.third)
        }
    }

    @Test fun switchingFrom127FilesToOneSurvivesOldQueuePositionUpdates() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val wav = File.createTempFile("cast-switch", ".wav", context.cacheDir)
        val size = 8_000 * 2 * 10
        val buffer = ByteBuffer.allocate(44 + size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()).putInt(36 + size).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(8_000).putInt(16_000)
            .putShort(2).putShort(16).put("data".toByteArray()).putInt(size)
        wav.writeBytes(buffer.array())
        lateinit var exo: ExoPlayer
        lateinit var delayed: DelayedQueuePlayer
        lateinit var wrapper: BookAggregatingPlayer
        lateinit var session: MediaSession
        lateinit var connecting: ListenableFuture<MediaController>
        var controller: MediaController? = null
        var created = false
        try {
            instrumentation.runOnMainSync {
                exo = ExoPlayer.Builder(context).build()
                exo.setMediaItems((0 until 127).map {
                    MediaItem.Builder().setMediaId("old-$it").setUri(wav.toURI().toString()).build()
                }, 40, 1_000)
                delayed = DelayedQueuePlayer(exo)
                wrapper = BookAggregatingPlayer(delayed, MutableStateFlow(AudioSettings()))
                session = MediaSession.Builder(context, wrapper).setId("queue-switch-${System.nanoTime()}")
                    .setCallback(object : MediaSession.Callback {
                        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                            val base = super.onConnect(session, controller)
                            return MediaSession.ConnectionResult.accept(
                                base.availableSessionCommands.buildUpon().add(BookQueueCommand.command).build(), base.availablePlayerCommands)
                        }
                        override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo,
                            command: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
                            BookQueueCommand.apply(session.player, BookQueueCommand.decode(args))
                            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                        }
                    }).build()
                created = true
                connecting = MediaController.Builder(context, session.token).buildAsync()
            }
            val connected = connecting.get(5, TimeUnit.SECONDS)
            controller = connected
            instrumentation.runOnMainSync {
                val items = listOf(MediaItem.Builder().setMediaId("new-book").setUri(wav.toURI().toString()).build())
                connected.sendCustomCommand(BookQueueCommand.command,
                    BookQueueCommand.encode(BookQueueCommand.Load(items, 0, 3_000, 1f, false)))
            }
            await(instrumentation) { delayed.pending != null }
            instrumentation.waitForIdleSync()
            // Cast has not acknowledged the new one-item queue yet. An old periodic/seek update
            // with index 41 must retain a consistent 127-item timeline until the load completes.
            instrumentation.runOnMainSync { exo.seekTo(41, 2_000) }
            Thread.sleep(500)
            instrumentation.runOnMainSync {
                assertEquals(127, connected.mediaItemCount)
                assertEquals(41, connected.currentMediaItemIndex)
            }
            instrumentation.runOnMainSync { delayed.complete() }
            await(instrumentation) { connected.mediaItemCount == 1 && connected.currentMediaItem?.mediaId == "new-book" }
            instrumentation.runOnMainSync {
                assertEquals(0, connected.currentMediaItemIndex)
                assertTrue(connected.isConnected)
            }
        } finally {
            instrumentation.runOnMainSync {
                controller?.release()
                if (created) { wrapper.detach(); session.release(); exo.release() }
            }
            wav.delete()
        }
    }

    private fun await(instrumentation: android.app.Instrumentation, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            var done = false
            instrumentation.runOnMainSync { done = condition() }
            if (done) return
            Thread.sleep(50)
        }
        fail("Timed out waiting for the asynchronous queue update")
    }
}
