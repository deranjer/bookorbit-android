package com.bookorbit.feature.player

import org.junit.Assert.assertEquals
import org.junit.Test

class BookmarkTitlesTest {
    @Test
    fun `time uses minutes and seconds under an hour`() {
        assertEquals("2:03", BookmarkTitles.time(123.0))
        assertEquals("0:00", BookmarkTitles.time(0.0))
    }

    @Test
    fun `time adds hours past an hour`() {
        assertEquals("1:02:03", BookmarkTitles.time(3723.4))
    }

    @Test
    fun `a negative position is treated as the start`() {
        assertEquals("0:00", BookmarkTitles.time(-5.0))
    }

    @Test
    fun `default title puts the time before the chapter`() {
        assertEquals("36:16 · Chapter 4", BookmarkTitles.default("Chapter 4", 2176.0))
    }

    @Test
    fun `default title is just the time when there is no chapter`() {
        assertEquals("36:16", BookmarkTitles.default(null, 2176.0))
        assertEquals("36:16", BookmarkTitles.default("   ", 2176.0))
    }
}
