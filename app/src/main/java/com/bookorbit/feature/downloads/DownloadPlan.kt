package com.bookorbit.feature.downloads

import com.bookorbit.core.db.DownloadEntity
import com.bookorbit.core.model.BookDetail
import com.bookorbit.core.model.BookFileRef
import com.bookorbit.core.model.BookFiles
import kotlinx.serialization.json.Json

/** Invalid persisted plans must fail, never silently download a different file. */
object DownloadPlan {
    fun files(entity: DownloadEntity, book: BookDetail, json: Json): List<BookFileRef> =
        entity.selectedFileIdsJson?.let {
            BookFiles.selectedDownloads(book, json.decodeFromString<List<Int>>(it).toSet())
        } ?: BookFiles.downloadableFiles(book)
}
