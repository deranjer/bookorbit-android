package com.bookorbit.feature.reader.pdf

import android.graphics.RectF

/**
 * Plain (API-agnostic) results from the PDF text layer. The extraction that produces them is gated
 * to Android 15+ ([PdfTextLayer]); below that these lists are always empty. All rects are in PDF
 * point coordinates in the page's pre-rotation space — map them with [PdfRenderCore.mapPageRect].
 */
data class PdfTextMatch(val rects: List<RectF>)

sealed interface PdfLink {
    val rects: List<RectF>

    /** A link to an external URI (open in a browser). */
    data class External(override val rects: List<RectF>, val uri: String) : PdfLink

    /** An internal "goto" link to another page in the same document (0-based). */
    data class Internal(override val rects: List<RectF>, val targetPage: Int) : PdfLink
}
