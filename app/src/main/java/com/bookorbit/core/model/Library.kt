package com.bookorbit.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Library(
    val id: Int,
    val name: String,
    val icon: String? = null,
    val displayOrder: Int = 0,
    val bookCount: Int? = null,
    val folders: List<LibraryFolder> = emptyList(),
    val createdAt: String,
    val updatedAt: String,
)

/** A configured root folder of a [Library]; a Book Dock file is finalized into one of these. */
@Serializable
data class LibraryFolder(
    val id: Int,
    val path: String,
    val createdAt: String? = null,
)

@Serializable
data class SmartScope(
    val id: Int,
    val name: String,
    val icon: String? = null,
    val isPublic: Boolean = false,
    val displayOrder: Int = 0,
    val bookCount: Int? = null,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class Collection(
    val id: Int,
    val name: String,
    val icon: String? = null,
    val displayOrder: Int = 0,
    val bookCount: Int? = null,
    val createdAt: String,
    val updatedAt: String,
)

/** `GET /collections?bookIds=` augments each collection with a membership count. */
@Serializable
data class CollectionWithMembership(
    val id: Int,
    val name: String,
    val icon: String? = null,
    val displayOrder: Int = 0,
    val bookCount: Int? = null,
    val createdAt: String,
    val updatedAt: String,
    val memberCount: Int = 0,
)

@Serializable
data class CollectionBookIds(val bookIds: List<Int>)

@Serializable
data class AuthorSummary(
    val id: Int,
    val name: String,
    val sortName: String? = null,
    val imageUrl: String? = null,
    val bookCount: Int = 0,
    val lastAddedAt: String? = null,
)

@Serializable
data class SeriesSummary(
    val name: String,
    val bookCount: Int = 0,
    val readCount: Int = 0,
    val authors: List<String> = emptyList(),
    val coverBookIds: List<Int> = emptyList(),
    val lastAddedAt: String? = null,
)

@Serializable
data class SeriesInfo(
    val name: String,
    val bookCount: Int = 0,
    val readCount: Int = 0,
    val authors: List<String> = emptyList(),
    val possibleGaps: List<Double> = emptyList(),
)

// --- Paged envelopes ---

@Serializable
data class BooksPage(
    val items: List<BookCard> = emptyList(),
    val total: Int = 0,
    val page: Int = 0,
    val size: Int = 0,
)

@Serializable
data class AuthorsPage(
    val items: List<AuthorSummary> = emptyList(),
    val total: Int = 0,
    val page: Int = 0,
    val size: Int = 0,
)

@Serializable
data class SeriesPage(
    val items: List<SeriesSummary> = emptyList(),
    val total: Int = 0,
    val page: Int = 0,
    val size: Int = 0,
)

@Serializable
data class SeriesBooksPage(
    val items: List<BookCard> = emptyList(),
    val total: Int = 0,
    val page: Int = 0,
    val size: Int = 0,
    val seriesInfo: SeriesInfo? = null,
)
