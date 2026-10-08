package com.bookorbit.feature.reader.comic

import kotlin.math.roundToInt

/** Page <-> progress maths for the comic reader. Pages are 0-based; progress is 0..100. */
object ComicPaging {
    /** Progress for being on [page] of [pageCount]; the last page reads as 100%. */
    fun percentage(page: Int, pageCount: Int): Double {
        if (pageCount <= 0) return 0.0
        if (pageCount == 1) return 100.0
        return (page.coerceIn(0, pageCount - 1).toDouble() / (pageCount - 1) * 100.0)
    }

    /** The page to resume on, from a saved fraction (0..1) of the way through the book. */
    fun pageForFraction(fraction: Double?, pageCount: Int): Int {
        if (fraction == null || pageCount <= 1) return 0
        return (fraction.coerceIn(0.0, 1.0) * (pageCount - 1)).roundToInt().coerceIn(0, pageCount - 1)
    }

    /** One-based page number sent to the server alongside the percentage. */
    fun pageNumber(page: Int): Int = page + 1
}
