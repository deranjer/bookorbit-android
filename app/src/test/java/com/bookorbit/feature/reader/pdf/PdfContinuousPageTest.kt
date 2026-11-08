package com.bookorbit.feature.reader.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PdfContinuousPageTest {
    private fun page(index: Int, offset: Int, size: Int = 900) =
        PdfLayout.VisiblePage(index, offset, size)

    @Test
    fun `tablet initially showing one page and a third reports page one`() {
        assertEquals(0, PdfLayout.mostVisiblePage(listOf(page(0, 0), page(1, 908)), 0, 1200))
    }

    @Test
    fun `previous page sliver does not keep page two or three behind`() {
        for (index in 1..2) {
            val pages = listOf(page(index - 1, -600), page(index, 308))
            val current = PdfLayout.mostVisiblePage(pages, 0, 1200)!!
            assertEquals(index, current)
            // The counter and server progress both use this same zero-based page.
            assertEquals(index + 1, PdfProgress.pageNumber(current))
            assertEquals((index + 1) * 10.0, PdfProgress.pageToPercentage(current, 10), 0.0001)
        }
    }

    @Test
    fun `last page is reported even when previous page cannot fully leave viewport`() {
        assertEquals(9, PdfLayout.mostVisiblePage(listOf(page(8, -608), page(9, 300)), 0, 1200))
    }

    @Test
    fun `scrolling back selects previous page once it occupies more space`() {
        assertEquals(0, PdfLayout.mostVisiblePage(listOf(page(0, -200), page(1, 708)), 0, 1200))
    }

    @Test
    fun `zoomed page spanning viewport remains current`() {
        assertEquals(4, PdfLayout.mostVisiblePage(listOf(page(4, -500, 2400)), 0, 1200))
    }

    @Test
    fun `mixed page sizes compare visible pixels rather than page fractions`() {
        assertEquals(1, PdfLayout.mostVisiblePage(listOf(page(0, -200, 400), page(1, 208, 2000)), 0, 1200))
    }

    @Test
    fun `visibility is clipped to both viewport bounds`() {
        assertEquals(1, PdfLayout.mostVisiblePage(listOf(page(0, -100, 200), page(1, 108, 400)), -50, 300))
    }

    @Test
    fun `equal visibility favors earlier page`() {
        assertEquals(0, PdfLayout.mostVisiblePage(listOf(page(1, 608), page(0, -308)), 0, 1200))
    }

    @Test
    fun `unmeasured or invisible pages do not overwrite restored progress`() {
        assertNull(PdfLayout.mostVisiblePage(emptyList(), 0, 0))
        assertNull(PdfLayout.mostVisiblePage(listOf(page(0, 0, 0)), 0, 1200))
        assertNull(PdfLayout.mostVisiblePage(listOf(page(0, -900), page(1, 1200)), 0, 1200))
    }
}
