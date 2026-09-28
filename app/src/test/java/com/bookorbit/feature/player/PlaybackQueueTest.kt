package com.bookorbit.feature.player

import com.bookorbit.core.model.AudioMetadata
import com.bookorbit.core.model.AudiobookChapter
import com.bookorbit.core.model.BookDetail
import com.bookorbit.core.model.BookFileRef
import org.junit.Assert.assertEquals
import org.junit.Test

/** Covers the playback queue math in [PlaybackQueue]. */
class PlaybackQueueTest {

    private val files = listOf(
        BookFileRef(id = 1, format = "mp3", role = "primary", durationSeconds = 100.0),
        BookFileRef(id = 2, format = "mp3", role = "supplementary", durationSeconds = 200.0),
        BookFileRef(id = 3, format = "mp3", role = "supplementary", durationSeconds = 50.0),
    )

    @Test
    fun `totalDuration sums file durations`() {
        assertEquals(350.0, PlaybackQueue.totalDurationSec(files), 0.0001)
    }

    @Test
    fun `toAbsolute adds preceding file durations`() {
        assertEquals(130.0, PlaybackQueue.toAbsoluteSec(files, index = 1, offsetSec = 30.0), 0.0001)
    }

    @Test
    fun `locateAbsolute finds the containing file and offset`() {
        val loc = PlaybackQueue.locateAbsolute(files, 130.0)
        assertEquals(1, loc.index)
        assertEquals(30.0, loc.offsetSec, 0.0001)
    }

    @Test
    fun `locateAbsolute clamps into the last file`() {
        val loc = PlaybackQueue.locateAbsolute(files, 10_000.0)
        assertEquals(2, loc.index)
    }

    @Test
    fun `percentageFor is whole-book percent`() {
        assertEquals(50.0, PlaybackQueue.percentageFor(files, index = 1, offsetSec = 75.0), 0.0001)
    }

    @Test
    fun `currentChapterIndex returns the last chapter at or before the offset`() {
        val book = BookDetail(
            id = 1, libraryId = 1, libraryName = "L", status = "unread", addedAt = "now",
            audioMetadata = AudioMetadata(
                chapters = listOf(
                    AudiobookChapter("One", 0),
                    AudiobookChapter("Two", 60_000),
                    AudiobookChapter("Three", 120_000),
                ),
            ),
        )
        val chapters = PlaybackQueue.resolveChapters(book, 300.0)
        assertEquals(1, PlaybackQueue.currentChapterIndex(chapters, 90.0))
        assertEquals(2, PlaybackQueue.currentChapterIndex(chapters, 120.0))
        assertEquals(-1, PlaybackQueue.currentChapterIndex(emptyList(), 90.0))
    }

    private fun ch(title: String, startSec: Double) = ResolvedChapter(title, startSec)

    @Test
    fun `normalizeChapters sorts and collapses duplicate starts`() {
        val out = PlaybackQueue.normalizeChapters(listOf(ch("B", 60.0), ch("A", 0.0), ch("A2", 0.0), ch("C", 120.0)), 300.0)
        assertEquals(listOf("A", "B", "C"), out.map { it.title })
    }

    @Test
    fun `normalizeChapters drops chapters at or past the end`() {
        val out = PlaybackQueue.normalizeChapters(listOf(ch("A", 0.0), ch("B", 60.0), ch("X", 300.0), ch("Y", 400.0)), 300.0)
        assertEquals(listOf("A", "B"), out.map { it.title })
    }

    @Test
    fun `normalizeChapters prepends Intro when first chapter starts late`() {
        val out = PlaybackQueue.normalizeChapters(listOf(ch("One", 30.0), ch("Two", 90.0)), 300.0)
        assertEquals(listOf(INTRO_TITLE, "One", "Two"), out.map { it.title })
        assertEquals(0.0, out[0].startSec, 0.0)
    }

    @Test
    fun `normalizeChapters snaps a near-zero first start to zero`() {
        val out = PlaybackQueue.normalizeChapters(listOf(ch("One", 0.4), ch("Two", 90.0)), 300.0)
        assertEquals(listOf("One", "Two"), out.map { it.title })
        assertEquals(0.0, out[0].startSec, 0.0)
    }

    @Test
    fun `normalizeChapters returns empty when fewer than two remain`() {
        assertEquals(emptyList<ResolvedChapter>(), PlaybackQueue.normalizeChapters(listOf(ch("Only", 0.0)), 300.0))
        assertEquals(emptyList<ResolvedChapter>(), PlaybackQueue.normalizeChapters(listOf(ch("A", 0.0), ch("B", 500.0)), 300.0))
    }

    private val threeChapters = listOf(ch("One", 0.0), ch("Two", 60.0), ch("Three", 120.0))

    @Test
    fun `chapterRanges end at the next start and the last ends at total`() {
        val r = PlaybackQueue.chapterRanges(threeChapters, 300.0)
        assertEquals(listOf(0.0 to 60.0, 60.0 to 120.0, 120.0 to 300.0), r.map { it.startSec to it.endSec })
        assertEquals(listOf(0, 1, 2), r.map { it.index })
    }

    @Test
    fun `chapterRange finds containing range including exact boundary and clamps`() {
        assertEquals(1, PlaybackQueue.chapterRange(threeChapters, 300.0, 60.0)?.index)
        assertEquals(1, PlaybackQueue.chapterRange(threeChapters, 300.0, 119.9)?.index)
        assertEquals(0, PlaybackQueue.chapterRange(threeChapters, 300.0, -5.0)?.index)
        assertEquals(2, PlaybackQueue.chapterRange(threeChapters, 300.0, 999.0)?.index)
        assertEquals(null, PlaybackQueue.chapterRange(emptyList(), 300.0, 10.0))
    }

    @Test
    fun `chapter and book time conversions clamp to the range`() {
        val r = ChapterRange(1, "Two", 60.0, 120.0)
        assertEquals(15.0, PlaybackQueue.toChapterTime(r, 75.0), 1e-9)
        assertEquals(0.0, PlaybackQueue.toChapterTime(r, 10.0), 1e-9)
        assertEquals(75.0, PlaybackQueue.toBookTime(r, 15.0), 1e-9)
        assertEquals(119.999, PlaybackQueue.toBookTime(r, 60.0), 1e-9)
        assertEquals(60.0, PlaybackQueue.toBookTime(r, -3.0), 1e-9)
    }

    @Test
    fun `formatDurationShort renders hours-minutes, minutes, or seconds`() {
        assertEquals("5h 12m", PlaybackQueue.formatDurationShort(5 * 3600 + 12 * 60 + 30.0))
        assertEquals("12m", PlaybackQueue.formatDurationShort(12 * 60 + 5.0))
        assertEquals("45s", PlaybackQueue.formatDurationShort(45.0))
    }
}
