package com.bookorbit.feature.downloads

import com.bookorbit.core.db.DownloadEntity
import com.bookorbit.core.model.BookDetail
import com.bookorbit.core.model.BookFileRef
import com.bookorbit.core.model.BookFiles
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.util.UUID

/** Invalid persisted plans must fail, never silently download a different file. */
object DownloadPlan {
    fun completed(entity: DownloadEntity, json: Json): List<DownloadedFile> =
        json.decodeFromString<List<DownloadedFile>>(entity.filesJson)

    /** Additive only: completed bytes and their original identity/metadata are never discarded. */
    fun expand(entity: DownloadEntity, book: BookDetail, requested: Set<Int>, json: Json): DownloadEntity? {
        require(book.id == entity.bookId)
        BookFiles.selectedDownloads(book, requested)
        if (entity.status == DownloadStatus.DOWNLOADING.name) return null
        require(entity.status.isCompleteDownloadStatus() || entity.status == DownloadStatus.FAILED.name)
        val oldBook = json.decodeFromString<BookDetail>(entity.bookJson)
        val completed = completed(entity, json).map { it.id }.toSet()
        val retained = oldBook.files.associateBy { it.id }
        val currentIds = book.files.map { it.id }.toSet()
        val mergedBook = book.copy(files = book.files.map { if (it.id in completed) retained[it.id] ?: it else it } +
            oldBook.files.filter { it.id !in currentIds })
        val previous = if (entity.status.isCompleteDownloadStatus() && completed.isNotEmpty()) completed
            else files(entity, oldBook, json).map { it.id }.toSet()
        val ids = previous + completed + requested
        val selected = BookFiles.selectedDownloads(mergedBook, ids)
        if (entity.status.isCompleteDownloadStatus() && ids == previous) return null
        return entity.copy(
            title = book.title,
            authors = book.authors.joinToString(", ") { it.name },
            narrators = book.audioMetadata?.narrators?.joinToString(", ") { it.name }.orEmpty(),
            isAudiobook = selected.any { it.format?.lowercase() in BookFiles.AUDIO_FORMATS },
            format = selected.first().format,
            bookJson = json.encodeToString(mergedBook),
            selectedFileIdsJson = json.encodeToString(selected.map { it.id }),
            status = DownloadStatus.DOWNLOADING.name, progress = 0f, lastError = null,
            planId = UUID.randomUUID().toString(),
        )
    }

    fun files(entity: DownloadEntity, book: BookDetail, json: Json): List<BookFileRef> =
        entity.selectedFileIdsJson?.let {
            BookFiles.selectedDownloads(book, json.decodeFromString<List<Int>>(it).toSet())
        } ?: BookFiles.downloadableFiles(book)
}
