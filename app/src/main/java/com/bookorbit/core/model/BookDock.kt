package com.bookorbit.core.model

import kotlinx.serialization.Serializable

/**
 * Mirrors the server's Book Dock API (`server/src/modules/book-dock`, types in
 * `packages/types/src/book-dock.ts`). Files dropped into the server's staging area are reviewed,
 * edited, assigned a destination (library + folder), and finalized into a real library.
 *
 * Json is configured with `explicitNulls = false`, so omitted (`null`) fields are NOT serialized —
 * which matches the server's "field present = set this column" PATCH/body semantics.
 */
@Serializable
data class BookDockMetadata(
    val title: String? = null,
    val subtitle: String? = null,
    val authors: List<String>? = null,
    val narrators: List<String>? = null,
    val description: String? = null,
    val publisher: String? = null,
    val publishedYear: Int? = null,
    val language: String? = null,
    val pageCount: Int? = null,
    val isbn10: String? = null,
    val isbn13: String? = null,
    val seriesName: String? = null,
    val seriesIndex: Double? = null,
    val genres: List<String>? = null,
    val coverUrl: String? = null,
)

@Serializable
data class BookDockFile(
    val id: Int,
    val fileName: String,
    val fileSize: Long? = null,
    val format: String? = null,
    val status: String,
    val embeddedMetadata: BookDockMetadata? = null,
    val selectedMetadata: BookDockMetadata? = null,
    val fetchedMetadata: BookDockMetadata? = null,
    val targetLibraryId: Int? = null,
    val targetFolderId: Int? = null,
    val confidence: Int? = null,
    val errorMessage: String? = null,
    val metadataEditedAt: String? = null,
    val createdAt: String,
    val updatedAt: String,
) {
    /** The metadata shown to the user: the working copy if present, otherwise what was embedded. */
    val displayMetadata: BookDockMetadata?
        get() = selectedMetadata ?: embeddedMetadata
}

@Serializable
data class BookDockFilesPage(
    val items: List<BookDockFile> = emptyList(),
    val total: Int = 0,
    val page: Int = 0,
    val size: Int = 0,
)

@Serializable
data class BookDockSummary(
    val pending: Int = 0,
    val ready: Int = 0,
    val error: Int = 0,
    val total: Int = 0,
)

/** `PATCH /book-dock/files/:id` body. */
@Serializable
data class UpdateBookDockFileRequest(
    val selectedMetadata: BookDockMetadata? = null,
    val targetLibraryId: Int? = null,
    val targetFolderId: Int? = null,
)

/**
 * Selection envelope shared by every bulk endpoint (discard / apply-fetched / set-target /
 * finalize). Either an explicit [fileIds] list, or [selectAll] over the current filter minus
 * [excludedIds]. [status]/[search] scope a `selectAll` to the visible filter.
 */
@Serializable
data class BookDockSelection(
    val fileIds: List<Int>? = null,
    val selectAll: Boolean? = null,
    val excludedIds: List<Int>? = null,
    val status: String? = null,
    val search: String? = null,
)

/** `POST /book-dock/files/set-target` body: a [BookDockSelection] plus the destination. */
@Serializable
data class SetTargetRequest(
    val fileIds: List<Int>? = null,
    val selectAll: Boolean? = null,
    val excludedIds: List<Int>? = null,
    val status: String? = null,
    val search: String? = null,
    val targetLibraryId: Int? = null,
    val targetFolderId: Int? = null,
)

/** Per-file destination override for finalize. */
@Serializable
data class FinalizeOverride(
    val fileId: Int,
    val libraryId: Int? = null,
    val folderId: Int? = null,
)

/**
 * `POST /book-dock/finalize` body. [defaultLibraryId]/[defaultFolderId] must both be present or both
 * absent; per-file targets (set earlier) take precedence, then [overrides], then the defaults.
 */
@Serializable
data class FinalizeRequest(
    val fileIds: List<Int>? = null,
    val selectAll: Boolean? = null,
    val excludedIds: List<Int>? = null,
    val status: String? = null,
    val search: String? = null,
    val defaultLibraryId: Int? = null,
    val defaultFolderId: Int? = null,
    val overrides: List<FinalizeOverride>? = null,
)

@Serializable
data class BookDockFinalizeFileResult(
    val fileId: Int,
    val fileName: String,
    val newName: String? = null,
    val success: Boolean,
    val bookId: Int? = null,
    val isDuplicate: Boolean? = null,
    val existingBookId: Int? = null,
    val message: String? = null,
)

@Serializable
data class BookDockFinalizeResult(
    val total: Int = 0,
    val succeeded: Int = 0,
    val failed: Int = 0,
    val results: List<BookDockFinalizeFileResult> = emptyList(),
)

/** Generic `{ total, updated/applied, failed/... }` counts returned by apply-fetched/set-target. */
@Serializable
data class BookDockBulkCounts(
    val total: Int = 0,
    val updated: Int = 0,
    val applied: Int = 0,
    val failed: Int = 0,
)
