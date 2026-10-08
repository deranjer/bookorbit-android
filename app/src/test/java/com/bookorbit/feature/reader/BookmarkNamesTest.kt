package com.bookorbit.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class BookmarkNamesTest {
    @Test
    fun `title is the chapter and how far through the book`() {
        assertEquals("Chapter 4 · 37%", BookmarkNames.default("Chapter 4", 37))
    }

    @Test
    fun `title is just the percentage without a chapter`() {
        assertEquals("37%", BookmarkNames.default(null, 37))
        assertEquals("0%", BookmarkNames.default("   ", 0))
    }

    @Test
    fun `chapter whitespace is trimmed`() {
        assertEquals("Prologue · 1%", BookmarkNames.default("  Prologue ", 1))
    }
}
