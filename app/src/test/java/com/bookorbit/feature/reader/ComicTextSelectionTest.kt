package com.bookorbit.feature.reader

import org.junit.Assert.*
import org.junit.Test

class ComicTextSelectionTest {
    // Reading-order indices are interleaved between separate speech bubbles.
    private val page = OcrPage(300, 200, listOf(
        OcrWord("Hello", 10f, 10f, 40f, 30f, 0),
        OcrWord("Other", 180f, 10f, 220f, 30f, 0),
        OcrWord("world!", 10f, 40f, 50f, 60f, 1),
        OcrWord("bubble.", 180f, 40f, 230f, 60f, 1)))

    @Test fun `sentence connects nearby lines but not a different bubble or intervening indices`() {
        assertEquals(setOf(0, 2), page.sentenceWordsAt(0))
        assertEquals(setOf(0, 2), page.selectionBetween(0, 2))
        assertEquals(setOf(0), page.selectionBetween(0, 3))
        assertEquals(setOf(1, 3), page.sentenceWordsAt(3))
    }

    @Test fun `sentence stops at punctuation inside a spatial group`() {
        val line = OcrPage(300, 200, listOf(
            OcrWord("Hi!", 0f, 0f, 20f, 20f, 0),
            OcrWord("Next", 25f, 0f, 55f, 20f, 0),
            OcrWord("one.", 60f, 0f, 85f, 20f, 0)))
        assertEquals(setOf(1, 2), line.sentenceWordsAt(1))
    }

    @Test fun `weak guesses and punctuation rejected while clear short words remain`() {
        assertFalse(acceptOcrWord("art", 25f, 30, 20))
        assertFalse(acceptOcrWord("...", 99f, 30, 20))
        assertFalse(acceptOcrWord("x", Float.NaN, 30, 20))
        assertTrue(acceptOcrWord("Я", 90f, 10, 20))
        assertTrue(acceptOcrWord("Hi!", 90f, 30, 20))
    }
}
