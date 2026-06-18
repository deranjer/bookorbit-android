package com.bookorbit.feature.library.filters

import kotlinx.serialization.Serializable

/**
 * Curated, mobile-friendly view of the server's filter model. Each populated property maps to one
 * rule under a single top-level AND group.
 */
@Serializable
data class LibraryFilters(
    val readStatus: List<String> = emptyList(),
    val readProgress: String? = null, // "unread" | "inProgress" | "finished"
    val formats: List<String> = emptyList(),
    val fileAvailability: String? = null, // "present" | "missing"
    val authors: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    val minRating: Int? = null,
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
)

@Serializable
data class LibrarySort(
    val field: String = "title",
    val dir: String = "asc", // "asc" | "desc"
)

@Serializable
data class StoredFilterPrefs(
    val filters: LibraryFilters = LibraryFilters(),
    val sort: LibrarySort = LibrarySort(),
)

val DEFAULT_FILTERS = LibraryFilters()
val DEFAULT_SORT = LibrarySort()

val READ_STATUS_OPTIONS: List<Pair<String, String>> = listOf(
    "unread" to "Unread",
    "want_to_read" to "Want to Read",
    "reading" to "Reading",
    "on_hold" to "On Hold",
    "rereading" to "Rereading",
    "read" to "Read",
    "skimmed" to "Skimmed",
    "abandoned" to "Abandoned",
)

val READ_PROGRESS_OPTIONS: List<Pair<String, String>> = listOf(
    "unread" to "Unread",
    "inProgress" to "In Progress",
    "finished" to "Finished",
)

val FILE_AVAILABILITY_OPTIONS: List<Pair<String, String>> = listOf(
    "present" to "Present",
    "missing" to "Missing",
)

val FORMAT_OPTIONS: List<String> = listOf(
    "epub", "pdf", "mobi", "azw3", "cbz", "cbr", "fb2", "m4b", "mp3", "m4a", "opus", "ogg", "flac",
)

val SORT_OPTIONS: List<Pair<String, String>> = listOf(
    "title" to "Title",
    "author" to "Author",
    "addedAt" to "Date Added",
    "updatedAt" to "Date Updated",
    "publishedYear" to "Published Year",
    "rating" to "Rating",
    "series" to "Series",
    "pageCount" to "Page Count",
    "readStatus" to "Read Status",
    "readProgress" to "Read Progress",
    "lastReadAt" to "Last Read",
    "random" to "Random",
)

/** Catalog kinds for the typeahead multiselects. */
enum class CatalogKind(val path: String) {
    AUTHORS("authors"),
    GENRES("genres"),
    TAGS("tags"),
    LANGUAGES("languages"),
}
