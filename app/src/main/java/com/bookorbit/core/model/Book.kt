package com.bookorbit.core.model

import kotlinx.serialization.Serializable

@Serializable
data class BookFileRef(
    val id: Int,
    val format: String? = null,
    val role: String,
    val sizeBytes: Long? = null,
    val durationSeconds: Double? = null,
    val filename: String? = null,
)

@Serializable
data class AudiobookChapter(val title: String, val startMs: Long)

@Serializable
data class NarratorRef(
    val id: Int,
    val name: String,
    val sortName: String? = null,
    val displayOrder: Int = 0,
)

@Serializable
data class AudioMetadata(
    val narrators: List<NarratorRef> = emptyList(),
    val durationSeconds: Double? = null,
    val abridged: Boolean = false,
    val chapters: List<AudiobookChapter>? = null,
)

@Serializable
data class BookAuthorRef(
    val id: Int,
    val name: String,
    val sortName: String? = null,
)

/** Read-status enum values accepted by the server (kept as String on the wire). */
@Serializable
data class UserBookStatus(
    val status: String,
    val source: String,
    val startedAt: String? = null,
    val finishedAt: String? = null,
    val updatedAt: String,
)

@Serializable
data class CollectionRef(val id: Int, val name: String)

/** Lean card shape returned by list/grid endpoints. */
@Serializable
data class BookCard(
    val id: Int,
    val title: String? = null,
    val authors: List<String> = emptyList(),
    val seriesName: String? = null,
    val seriesIndex: Double? = null,
    val files: List<BookFileRef> = emptyList(),
    val publishedYear: Int? = null,
    val language: String? = null,
    val genres: List<String> = emptyList(),
    val rating: Double? = null,
    val readingProgress: Double? = null,
    val addedAt: String,
    val hasCover: Boolean = false,
    val tags: List<String> = emptyList(),
    val narrators: List<String> = emptyList(),
)

/** Populated subset of the server BookDetailDto that the detail screen renders. */
@Serializable
data class BookDetail(
    val id: Int,
    val libraryId: Int,
    val libraryName: String,
    val status: String,
    val addedAt: String,
    val title: String? = null,
    val subtitle: String? = null,
    val description: String? = null,
    val isbn10: String? = null,
    val isbn13: String? = null,
    val publisher: String? = null,
    val publishedYear: Int? = null,
    val language: String? = null,
    val pageCount: Int? = null,
    val seriesId: Int? = null,
    val seriesName: String? = null,
    val seriesIndex: Double? = null,
    val rating: Double? = null,
    val coverSource: String? = null,
    val providerIds: Map<String, String?> = emptyMap(),
    val authors: List<BookAuthorRef> = emptyList(),
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val files: List<BookFileRef> = emptyList(),
    val readStatus: UserBookStatus? = null,
    val collections: List<CollectionRef> = emptyList(),
    val audioMetadata: AudioMetadata? = null,
)

@Serializable
data class BookRecommendation(
    val id: Int,
    val title: String? = null,
    val hasCover: Boolean = false,
    val authors: List<String> = emptyList(),
    val seriesIndex: Double? = null,
    val isAudiobook: Boolean? = null,
)

/** `GET /books/search` returns this lean cross-library shape (not a full BookCard). */
@Serializable
data class SearchResult(
    val id: Int,
    val title: String? = null,
    val seriesName: String? = null,
    val authors: List<String> = emptyList(),
    val libraryId: Int,
    val libraryName: String,
    val formats: List<String> = emptyList(),
)

// --- Progress payloads ---

@Serializable
data class AudioProgress(
    val currentFileId: Int,
    val positionSeconds: Double,
    val percentage: Double,
    val updatedAt: String? = null,
)

@Serializable
data class SaveAudioProgress(
    val currentFileId: Int,
    val positionSeconds: Double,
    val percentage: Double,
)

/** Server stores a richer shape; the app only reads the CFI + percentage the reader needs. */
@Serializable
data class FileProgress(
    val cfi: String? = null,
    val percentage: Double = 0.0,
    val pageNumber: Int? = null,
    val updatedAt: String? = null,
)

@Serializable
data class SaveFileProgress(
    val cfi: String? = null,
    val percentage: Double,
    val pageNumber: Int? = null,
)

// --- Mutation request bodies ---

@Serializable
data class SetReadStatusRequest(val status: String)

@Serializable
data class SetRatingRequest(val bookIds: List<Int>, val rating: Int?)
