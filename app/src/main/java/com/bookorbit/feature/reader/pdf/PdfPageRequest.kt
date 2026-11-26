package com.bookorbit.feature.reader.pdf

/** Navigation intent is separate from the last page actually visible/reported. */
data class PdfPageRequest(val page: Int, val sequence: Long)
