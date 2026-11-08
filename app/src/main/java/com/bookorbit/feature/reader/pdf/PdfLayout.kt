package com.bookorbit.feature.reader.pdf

import kotlin.math.roundToInt

/**
 * Pure layout helpers for continuous page visibility and paginated page spreads.
 */
object PdfLayout {
    /** A continuous page's bounds in the lazy list's viewport coordinate system. */
    data class VisiblePage(val index: Int, val offset: Int, val size: Int)

    /**
     * Page occupying the most visible vertical space, with ties going to the earlier page.
     * A trailing sliver of the previous page must not keep the counter and sync one page behind.
     * Returns null before layout has produced any visible pages.
     */
    fun mostVisiblePage(
        pages: List<VisiblePage>,
        viewportStart: Int,
        viewportEnd: Int,
    ): Int? {
        var current: Int? = null
        var largestVisibleHeight = 0
        for (page in pages) {
            val visibleHeight = (minOf(page.offset + page.size, viewportEnd) -
                maxOf(page.offset, viewportStart)).coerceAtLeast(0)
            if (visibleHeight > largestVisibleHeight ||
                (visibleHeight > 0 && visibleHeight == largestVisibleHeight &&
                    page.index < (current ?: Int.MAX_VALUE))
            ) {
                current = page.index
                largestVisibleHeight = visibleHeight
            }
        }
        return current
    }

    /** Pager items, each a list of 1 or 2 page indices (left, then optional right). */
    fun spreadItems(pageCount: Int, spread: PdfReaderSettings.Spread): List<List<Int>> {
        if (pageCount <= 0) return emptyList()
        return when (spread) {
            PdfReaderSettings.Spread.NONE -> (0 until pageCount).map { listOf(it) }
            PdfReaderSettings.Spread.EVEN -> chunkPairs(0, pageCount)
            PdfReaderSettings.Spread.ODD -> listOf(listOf(0)) + chunkPairs(1, pageCount)
        }
    }

    private fun chunkPairs(start: Int, pageCount: Int): List<List<Int>> {
        val items = mutableListOf<List<Int>>()
        var i = start
        while (i < pageCount) {
            if (i + 1 < pageCount) items.add(listOf(i, i + 1)) else items.add(listOf(i))
            i += 2
        }
        return items
    }

    /** Index of the pager item that contains [page]. */
    fun itemForPage(items: List<List<Int>>, page: Int): Int {
        val idx = items.indexOfFirst { page in it }
        return if (idx >= 0) idx else 0
    }

    /** The left-most (lowest) page index shown by pager [item]. */
    fun firstPageOfItem(items: List<List<Int>>, item: Int): Int =
        items.getOrNull(item)?.firstOrNull() ?: 0

    /** Final bitmap pixel size for a page rendered [targetWidthPx] wide and rotated by [rotate]. */
    fun displayedSize(pageW: Int, pageH: Int, targetWidthPx: Int, rotate: Int): Pair<Int, Int> {
        val swap = rotate == 90 || rotate == 270
        val w = pageW.toFloat().coerceAtLeast(1f)
        val h = pageH.toFloat().coerceAtLeast(1f)
        val dispW = if (swap) h else w
        val dispH = if (swap) w else h
        val scale = targetWidthPx / dispW
        return targetWidthPx to (dispH * scale).roundToInt().coerceAtLeast(1)
    }

    /** Render width (px) for one page given the zoom mode, capped to avoid oversized bitmaps. */
    fun renderWidthPx(
        pageW: Int,
        pageH: Int,
        baseWidthPx: Int,
        containerHeightPx: Int,
        settings: PdfReaderSettings,
        maxWidthPx: Int = 4096,
    ): Int {
        val width = when (settings.zoomMode) {
            PdfReaderSettings.ZoomMode.FIT_WIDTH -> baseWidthPx
            PdfReaderSettings.ZoomMode.CUSTOM -> (baseWidthPx * settings.customScale).roundToInt()
            PdfReaderSettings.ZoomMode.FIT_PAGE -> {
                val (_, h) = displayedSize(pageW, pageH, baseWidthPx, settings.rotation)
                if (containerHeightPx <= 0 || h <= containerHeightPx) baseWidthPx
                else (baseWidthPx.toDouble() * containerHeightPx / h).roundToInt()
            }
        }
        return width.coerceIn(1, maxWidthPx)
    }
}
