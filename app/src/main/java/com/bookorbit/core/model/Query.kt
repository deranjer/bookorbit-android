package com.bookorbit.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Wire shape for the advanced book query (`POST /books/query`, `POST /libraries/:id/books`).
 *
 * The filter is a nested rule/group tree whose rule values are polymorphic
 * (string | number | array). Rather than model that awkward union as Kotlin classes, the
 * filter/sort sheet (browse task) builds the tree directly as a [JsonElement] via a small DSL, and
 * we send it through unchanged. Sort and pagination are well-typed.
 */
@Serializable
data class SortSpec(val field: String, val dir: String)

@Serializable
data class Pagination(val page: Int, val size: Int)

@Serializable
data class BookQuery(
    val sort: List<SortSpec> = emptyList(),
    val pagination: Pagination,
    val filter: JsonElement? = null,
    val q: String? = null,
)
