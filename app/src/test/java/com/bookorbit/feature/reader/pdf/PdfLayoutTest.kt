package com.bookorbit.feature.reader.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfLayoutTest {

    @Test
    fun `spreadItems NONE is one page per item`() {
        assertEquals(listOf(listOf(0), listOf(1), listOf(2)), PdfLayout.spreadItems(3, PdfReaderSettings.Spread.NONE))
    }

    @Test
    fun `spreadItems EVEN pairs from page zero with a trailing single`() {
        assertEquals(
            listOf(listOf(0, 1), listOf(2, 3), listOf(4)),
            PdfLayout.spreadItems(5, PdfReaderSettings.Spread.EVEN),
        )
    }

    @Test
    fun `spreadItems ODD shows page zero alone then pairs`() {
        assertEquals(
            listOf(listOf(0), listOf(1, 2), listOf(3, 4)),
            PdfLayout.spreadItems(5, PdfReaderSettings.Spread.ODD),
        )
    }

    @Test
    fun `spreadItems empty document is empty`() {
        assertEquals(emptyList<List<Int>>(), PdfLayout.spreadItems(0, PdfReaderSettings.Spread.EVEN))
    }

    @Test
    fun `itemForPage and firstPageOfItem are inverse for EVEN spread`() {
        val items = PdfLayout.spreadItems(5, PdfReaderSettings.Spread.EVEN)
        assertEquals(1, PdfLayout.itemForPage(items, 3))
        assertEquals(2, PdfLayout.firstPageOfItem(items, 1))
    }

    @Test
    fun `displayedSize keeps aspect ratio and swaps on rotation`() {
        assertEquals(100 to 200, PdfLayout.displayedSize(100, 200, 100, 0))
        // Rotated 90°: the page's height becomes the displayed width reference.
        assertEquals(100 to 50, PdfLayout.displayedSize(100, 200, 100, 90))
    }

    @Test
    fun `renderWidthPx honors zoom modes and the cap`() {
        val fitWidth = PdfReaderSettings(zoomMode = PdfReaderSettings.ZoomMode.FIT_WIDTH)
        assertEquals(300, PdfLayout.renderWidthPx(100, 200, 300, 1000, fitWidth))

        val custom = PdfReaderSettings(zoomMode = PdfReaderSettings.ZoomMode.CUSTOM, customScale = 2.0)
        assertEquals(600, PdfLayout.renderWidthPx(100, 200, 300, 1000, custom))
        assertEquals(4096, PdfLayout.renderWidthPx(100, 200, 3000, 1000, custom))

        // Fit-page on a portrait page taller than the viewport scales the width down.
        val fitPage = PdfReaderSettings(zoomMode = PdfReaderSettings.ZoomMode.FIT_PAGE)
        assertEquals(50, PdfLayout.renderWidthPx(100, 200, 100, 100, fitPage))
    }
}
