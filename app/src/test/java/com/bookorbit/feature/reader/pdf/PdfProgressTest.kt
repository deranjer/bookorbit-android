package com.bookorbit.feature.reader.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfProgressTest {

    @Test
    fun `pageToPercentage maps first and last pages to 10 and 100`() {
        assertEquals(10.0, PdfProgress.pageToPercentage(0, 10), 0.0001)
        assertEquals(100.0, PdfProgress.pageToPercentage(9, 10), 0.0001)
    }

    @Test
    fun `pageToPercentage is zero for an empty document`() {
        assertEquals(0.0, PdfProgress.pageToPercentage(0, 0), 0.0001)
    }

    @Test
    fun `pageNumber is one-based`() {
        assertEquals(1, PdfProgress.pageNumber(0))
        assertEquals(42, PdfProgress.pageNumber(41))
    }

    @Test
    fun `fractionToPage round-trips a stored page`() {
        val total = 10
        for (page in 0 until total) {
            val fraction = PdfProgress.pageToPercentage(page, total) / 100.0
            assertEquals(page, PdfProgress.fractionToPage(fraction, total))
        }
    }

    @Test
    fun `fractionToPage clamps and defaults`() {
        assertEquals(0, PdfProgress.fractionToPage(null, 10))
        assertEquals(0, PdfProgress.fractionToPage(0.0, 10))
        assertEquals(9, PdfProgress.fractionToPage(2.0, 10))
        assertEquals(0, PdfProgress.fractionToPage(0.5, 0))
    }
}
