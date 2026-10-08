package com.bookorbit.core.model

import kotlinx.serialization.Serializable

/**
 * A highlight/note on a book, as returned by `GET /books/{id}/annotations`. Shared with the web
 * client, KOReader and Kobo (three-way synced), so [origin] may be something other than "android".
 * [cfi] is null for PDF annotations, which carry a page rectangle instead (not modelled here yet).
 */
@Serializable
data class BookAnnotation(
    val id: Int,
    val bookId: Int,
    val cfi: String? = null,
    val text: String = "",
    val color: String = "yellow",
    val style: String = "highlight",
    val note: String? = null,
    val chapterTitle: String? = null,
    val origin: String = "web",
    val positionStatus: String? = null,
)

/** Body of `POST /books/{id}/annotations`. */
@Serializable
data class CreateAnnotation(
    val cfi: String,
    val bookFileId: Int,
    val text: String,
    val color: String,
    val style: String,
    val note: String?,
    val chapterTitle: String?,
)
