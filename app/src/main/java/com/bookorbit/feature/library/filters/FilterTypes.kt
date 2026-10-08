package com.bookorbit.feature.library.filters

import com.bookorbit.R
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

/** Filter options as (server value, label resource). Labels stay resource ids so this file holds no English. */
val READ_STATUS_OPTIONS: List<Pair<String, Int>> = listOf(
    "unread" to R.string.status_unread,
    "want_to_read" to R.string.status_want_to_read,
    "reading" to R.string.status_reading,
    "on_hold" to R.string.status_on_hold,
    "rereading" to R.string.status_rereading,
    "read" to R.string.status_read,
    "skimmed" to R.string.status_skimmed,
    "abandoned" to R.string.status_abandoned,
)

val READ_PROGRESS_OPTIONS: List<Pair<String, Int>> = listOf(
    "unread" to R.string.progress_unread,
    "inProgress" to R.string.progress_in_progress,
    "finished" to R.string.progress_finished,
)

val FILE_AVAILABILITY_OPTIONS: List<Pair<String, Int>> = listOf(
    "present" to R.string.availability_present,
    "missing" to R.string.availability_missing,
)

val FORMAT_OPTIONS: List<String> = listOf(
    "epub", "pdf", "mobi", "azw3", "cbz", "cbr", "fb2", "m4b", "mp3", "m4a", "opus", "ogg", "flac",
)

val SORT_OPTIONS: List<Pair<String, Int>> = listOf(
    "title" to R.string.sort_title,
    "author" to R.string.sort_author,
    "addedAt" to R.string.sort_added,
    "updatedAt" to R.string.sort_updated,
    "publishedYear" to R.string.sort_published,
    "rating" to R.string.sort_rating,
    "series" to R.string.sort_series,
    "pageCount" to R.string.sort_pages,
    "readStatus" to R.string.sort_status,
    "readProgress" to R.string.sort_progress,
    "lastReadAt" to R.string.sort_last_read,
    "random" to R.string.sort_random,
)

/** Catalog kinds for the typeahead multiselects. */
enum class CatalogKind(val path: String) {
    AUTHORS("authors"),
    GENRES("genres"),
    TAGS("tags"),
    LANGUAGES("languages"),
}
