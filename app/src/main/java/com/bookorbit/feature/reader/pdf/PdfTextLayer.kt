package com.bookorbit.feature.reader.pdf

import android.graphics.pdf.PdfRenderer
import androidx.annotation.RequiresApi

/**
 * Android 15 (API 35) text layer for [PdfRenderer.Page]: full-text search, link extraction and
 * page-text extraction. These APIs do not exist below API 35, so every caller must gate on
 * [android.os.Build.VERSION.SDK_INT] >= 35 before touching this object (see [PdfRenderCore]).
 *
 * Note: PdfRenderer exposes per-page goto/link content but NOT a document-level outline/bookmark
 * tree, so there is no TOC here by design.
 */
@RequiresApi(35)
object PdfTextLayer {

    /** Bounds (page space) of every match of [query] on the page. */
    fun search(page: PdfRenderer.Page, query: String): List<PdfTextMatch> =
        runCatching {
            page.searchText(query).map { PdfTextMatch(it.bounds) }
        }.getOrDefault(emptyList())

    /** External URL links and internal goto links present on the page. */
    fun links(page: PdfRenderer.Page): List<PdfLink> = runCatching {
        val external = page.linkContents.map { PdfLink.External(it.bounds, it.uri.toString()) }
        val goto = page.gotoLinks.map { PdfLink.Internal(it.bounds, it.destination.pageNumber) }
        external + goto
    }.getOrDefault(emptyList())

    /** Concatenated text of the page (used for a copy-page-text action). */
    fun text(page: PdfRenderer.Page): String = runCatching {
        page.textContents.joinToString("\n") { it.text }
    }.getOrDefault("")
}
