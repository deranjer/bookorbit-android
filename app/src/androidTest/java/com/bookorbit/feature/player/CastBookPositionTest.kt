package com.bookorbit.feature.player

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Reproduce Cast's 127-item playlist with metadata present only on the active window. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class CastBookPositionTest {
    @Test fun partialQueueRetainsBookTimeAcrossSeekPlaybackAndChapterMode() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val wav = File.createTempFile("cast-position", ".wav", context.cacheDir)
        val size = 8_000 * 2 * 10
        val data = ByteBuffer.allocate(44 + size).order(ByteOrder.LITTLE_ENDIAN)
        data.put("RIFF".toByteArray()).putInt(36 + size).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(8_000).putInt(16_000)
            .putShort(2).putShort(16).put("data".toByteArray()).putInt(size)
        wav.writeBytes(data.array())
        val prefs = MutableStateFlow(AudioSettings(progressBarMode = ProgressBarMode.BOOK))
        val originals = (0 until 127).map { index ->
            val extras = Bundle().apply {
                putDouble(BookAggregatingPlayer.EXTRA_DURATION_SEC, 10.0)
                putDoubleArray(BookAggregatingPlayer.EXTRA_CHAPTER_STARTS_SEC, doubleArrayOf(0.0, 640.0, 900.0))
                putStringArray(BookAggregatingPlayer.EXTRA_CHAPTER_TITLES, arrayOf("One", "Two", "Three"))
            }
            MediaItem.Builder().setMediaId("$index").setUri(wav.toURI().toString())
                .setMediaMetadata(MediaMetadata.Builder().setExtras(extras).build()).build()
        }
        var source = originals
        lateinit var exo: ExoPlayer
        lateinit var wrapper: BookAggregatingPlayer
        var exoCreated = false
        var wrapperCreated = false
        try {
            instrumentation.runOnMainSync {
                exo = ExoPlayer.Builder(context).build()
                exoCreated = true
                val sparse = originals.mapIndexed { i, item ->
                    if (i == 40) item else MediaItem.Builder().setUri(wav.toURI().toString()).build()
                }
                exo.setMediaItems(sparse, 40, 2_000)
                wrapper = BookAggregatingPlayer(exo, prefs) { source }
                wrapperCreated = true
                exo.prepare()
                assertTrue(wrapper.hasCurrentSourceQueue())
                assertEquals(402_000L, wrapper.bookPositionMs())
                assertEquals(1_270_000L, wrapper.duration)

                // Receiver acknowledges a cross-file scrub while distant entries remain placeholders.
                exo.replaceMediaItem(65, originals[65])
                wrapper.seekToBookMs(652_000)
                assertEquals(65, exo.currentMediaItemIndex)
                assertEquals(652_000L, wrapper.bookPositionMs())
                prefs.value = prefs.value.copy(progressBarMode = ProgressBarMode.CHAPTER)
                assertEquals(12_000L, wrapper.currentPosition)
                assertEquals(260_000L, wrapper.duration)

                // Same IDs/size from an old proxy generation must still be rejected.
                source = originals.mapIndexed { i, item ->
                    if (i == 65) item.buildUpon().setUri("http://127.0.0.1/new-generation/65").build() else item
                }
                assertFalse(wrapper.hasCurrentSourceQueue())
                source = originals
                exo.play()
            }
            val deadline = System.currentTimeMillis() + 4_000
            var position = 0L
            while (System.currentTimeMillis() < deadline && position <= 652_300) {
                Thread.sleep(100)
                instrumentation.runOnMainSync { position = wrapper.bookPositionMs() }
            }
            assertTrue("Book time must advance after a scrub rather than snap back", position > 652_300)
        } finally {
            instrumentation.runOnMainSync {
                if (wrapperCreated) wrapper.detach()
                if (exoCreated) exo.release()
            }
            wav.delete()
        }
    }
}
