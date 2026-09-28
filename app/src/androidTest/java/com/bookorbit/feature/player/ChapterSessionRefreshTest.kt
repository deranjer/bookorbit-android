package com.bookorbit.feature.player

import android.media.MediaMetadata as PlatformMetadata
import android.media.session.MediaController as PlatformController
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile

/**
 * Two 10 s silent WAV files, chapters at 0 / 5 / 15 s → ranges [0,5) [5,15) [15,20).
 * Asserts the platform MediaSession metadata duration follows the current chapter.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class ChapterSessionRefreshTest {
    private val instr = InstrumentationRegistry.getInstrumentation()
    private val ctx = instr.targetContext
    private val prefs = MutableStateFlow(AudioSettings(progressBarMode = ProgressBarMode.CHAPTER))
    private lateinit var exo: ExoPlayer
    private lateinit var wrapper: BookAggregatingPlayer
    private lateinit var session: MediaSession
    private lateinit var platform: PlatformController

    @Before fun setUp() {
        val files = (1..2).map { writeSilentWav(File(ctx.cacheDir, "ch$it.wav"), seconds = 10) }
        instr.runOnMainSync {
            exo = ExoPlayer.Builder(ctx).build()
            wrapper = BookAggregatingPlayer(exo, prefs)
            session = MediaSession.Builder(ctx, wrapper).setId("chapter-test-${System.nanoTime()}").build()
            platform = PlatformController(ctx, session.platformToken)
            exo.setMediaItems(files.mapIndexed { i, f -> item(i, f) })
            exo.prepare()
        }
    }

    @After fun tearDown() = instr.runOnMainSync { wrapper.detach(); session.release(); exo.release() }

    @Test fun durationFollowsChapterOnSeekAndPlayback() {
        seekBook(3_000);  awaitDuration(5_000)
        seekBook(6_000);  awaitDuration(10_000)      // paused seek into chapter 2
        assertEquals("Two", platform.metadata?.getString(PlatformMetadata.METADATA_KEY_DISPLAY_SUBTITLE))
        seekBook(14_000)
        instr.runOnMainSync { exo.play() }
        awaitDuration(5_000, timeoutMs = 8_000)       // natural playback across 15 s
        prefs.value = prefs.value.copy(progressBarMode = ProgressBarMode.BOOK)
        awaitDuration(20_000, timeoutMs = 4_000)      // mode toggle
    }

    private fun seekBook(ms: Long) = instr.runOnMainSync { wrapper.seekToBookMs(ms) }

    private fun awaitDuration(expected: Long, timeoutMs: Long = 4_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        var last: Long? = null
        while (System.currentTimeMillis() < deadline) {
            last = platform.metadata?.getLong(PlatformMetadata.METADATA_KEY_DURATION)
            if (last == expected) return
            Thread.sleep(100)
        }
        assertEquals(expected, last)
    }

    private fun item(i: Int, f: File): MediaItem {
        val extras = Bundle().apply {
            putDouble(BookAggregatingPlayer.EXTRA_DURATION_SEC, 10.0)
            putDoubleArray(BookAggregatingPlayer.EXTRA_CHAPTER_STARTS_SEC, doubleArrayOf(0.0, 5.0, 15.0))
            putStringArray(BookAggregatingPlayer.EXTRA_CHAPTER_TITLES, arrayOf("One", "Two", "Three"))
        }
        val md = MediaMetadata.Builder().setTitle("Test book").setArtist("Narrator").setExtras(extras).build()
        return MediaItem.Builder().setMediaId("f$i").setUri(f.toURI().toString()).setMediaMetadata(md).build()
    }

    /** 8 kHz mono 16-bit PCM silence. */
    private fun writeSilentWav(file: File, seconds: Int): File {
        val dataLen = 8_000 * 2 * seconds
        RandomAccessFile(file, "rw").use { out ->
            out.setLength(0)
            fun le32(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte()))
            fun le16(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte()))
            out.writeBytes("RIFF"); le32(36 + dataLen); out.writeBytes("WAVE")
            out.writeBytes("fmt "); le32(16); le16(1); le16(1); le32(8_000); le32(16_000); le16(2); le16(16)
            out.writeBytes("data"); le32(dataLen); out.write(ByteArray(dataLen))
        }
        return file
    }
}
