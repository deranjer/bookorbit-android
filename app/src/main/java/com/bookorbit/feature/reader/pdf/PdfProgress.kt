package com.bookorbit.feature.reader.pdf

/**
 * Pure page <-> progress conversions for the PDF reader. PDF page counts are stable, so a stored
 * percentage round-trips back to an exact page without needing a persisted page number locally.
 */
object PdfProgress {
    /** Percentage (0..100) for a 0-based [pageIndex] within [pageCount] pages. */
    fun pageToPercentage(pageIndex: Int, pageCount: Int): Double {
        if (pageCount <= 0) return 0.0
        return ((pageIndex + 1).toDouble() / pageCount * 100.0).coerceIn(0.0, 100.0)
    }

    /** 1-based page number for a 0-based [pageIndex] (what the server stores). */
    fun pageNumber(pageIndex: Int): Int = pageIndex + 1

    /** 0-based page to resume at from a stored [fraction] (0..1), clamped to the document. */
    fun fractionToPage(fraction: Double?, pageCount: Int): Int {
        if (fraction == null || pageCount <= 0) return 0
        return (Math.round(fraction * pageCount).toInt() - 1).coerceIn(0, pageCount - 1)
    }
}
