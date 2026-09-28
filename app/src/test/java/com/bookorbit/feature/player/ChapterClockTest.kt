package com.bookorbit.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterClockTest {
    // Two files of 100 s; chapters at 0, 60, 150 → ranges [0,60) [60,150) [150,200)
    private val files = listOf(100.0, 100.0)
    private val chapters = listOf(ResolvedChapter("One", 0.0), ResolvedChapter("Two", 60.0), ResolvedChapter("Three", 150.0))
    private fun clock(mode: ProgressBarMode = ProgressBarMode.CHAPTER, ch: List<ResolvedChapter> = chapters, f: List<Double> = files) =
        ChapterClock(f, ch, mode)

    @Test fun `book duration sums files`() = assertEquals(200_000L, clock().bookDurationMs)

    @Test fun `chapter mode reports chapter-relative position and duration`() {
        val c = clock()
        assertTrue(c.isChapterMode)
        assertEquals(15_000L, c.displayPositionMs(75_000))
        assertEquals(90_000L, c.displayDurationMs(75_000))
        assertEquals(50_000L, c.displayDurationMs(199_000))
    }

    @Test fun `book mode passes book time through`() {
        val c = clock(ProgressBarMode.BOOK)
        assertFalse(c.isChapterMode)
        assertEquals(75_000L, c.displayPositionMs(75_000))
        assertEquals(200_000L, c.displayDurationMs(75_000))
    }

    @Test fun `chapterless falls back to book mode`() {
        val c = clock(ch = emptyList())
        assertFalse(c.isChapterMode)
        assertEquals(75_000L, c.displayPositionMs(75_000))
    }

    @Test fun `buffered is clamped into the chapter`() {
        val c = clock()
        assertEquals(30_000L, c.displayBufferedMs(75_000, 90_000))
        assertEquals(90_000L, c.displayBufferedMs(75_000, 180_000))
    }

    @Test fun `display seek maps into the current chapter and clamps to end minus 1ms`() {
        val c = clock()
        assertEquals(80_000L, c.bookTargetForDisplaySeek(75_000, 20_000))
        assertEquals(149_999L, c.bookTargetForDisplaySeek(75_000, 999_000))
        assertEquals(20_000L, clock(ProgressBarMode.BOOK).bookTargetForDisplaySeek(75_000, 20_000))
    }

    @Test fun `offset seeks cross chapter and file boundaries and clamp to the book`() {
        val c = clock()
        assertEquals(55_000L, c.bookTargetForOffset(62_000, -7_000))
        assertEquals(105_000L, c.bookTargetForOffset(95_000, 10_000))
        assertEquals(0L, c.bookTargetForOffset(3_000, -10_000))
        assertEquals(200_000L, c.bookTargetForOffset(195_000, 30_000))
    }

    @Test fun `next and previous follow chapters in both modes`() {
        for (mode in ProgressBarMode.entries) {
            val c = clock(mode)
            assertEquals(150_000L, c.nextNavTargetMs(75_000))
            assertNull(c.nextNavTargetMs(160_000))
            assertTrue(c.hasNextNav(75_000))
            assertFalse(c.hasNextNav(160_000))
            assertEquals(60_000L, c.previousNavTargetMs(75_000)) // > 3 s in: restart chapter
            assertEquals(0L, c.previousNavTargetMs(61_000))      // < 3 s in: previous chapter
            assertEquals(0L, c.previousNavTargetMs(1_000))       // first chapter: stays at 0
        }
    }

    @Test fun `navigation falls back to file starts when chapterless`() {
        val c = clock(ch = emptyList())
        assertEquals(listOf(0.0, 100.0), c.navPointsSec())
        assertEquals(100_000L, c.nextNavTargetMs(50_000))
        assertTrue(clock(ch = emptyList(), f = listOf(100.0)).navPointsSec().isEmpty())
    }

    @Test fun `rangeAt returns null in book mode`() {
        assertNull(clock(ProgressBarMode.BOOK).rangeAt(75_000))
        assertEquals(1, clock().rangeAt(75_000)?.index)
    }
}
