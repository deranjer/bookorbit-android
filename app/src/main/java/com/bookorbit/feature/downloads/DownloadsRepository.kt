package com.bookorbit.feature.downloads

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.bookorbit.core.db.DownloadDao
import com.bookorbit.core.db.DownloadEntity
import com.bookorbit.core.model.BookDetail
import com.bookorbit.core.model.BookFiles
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Orchestrates downloads (WorkManager) and exposes the offline catalog. */
@Singleton
class DownloadsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DownloadDao,
    private val json: Json,
) {
    val downloads: Flow<List<DownloadEntity>> = dao.observeAll()

    fun observe(bookId: Int): Flow<DownloadEntity?> = dao.observe(bookId)

    suspend fun get(bookId: Int): DownloadEntity? = dao.get(bookId)

    suspend fun isDownloaded(bookId: Int): Boolean = dao.get(bookId)?.status == DownloadStatus.COMPLETE.name

    /** The BookDetail persisted at download time, so book detail/reader/player screens work fully offline. */
    suspend fun cachedBook(bookId: Int): BookDetail? =
        dao.get(bookId)?.let { runCatching { json.decodeFromString(BookDetail.serializer(), it.bookJson) }.getOrNull() }

    /** Map of fileId -> local file path for a completed download (else empty). */
    suspend fun localFiles(bookId: Int): Map<Int, String> {
        val entity = dao.get(bookId)?.takeIf { it.status == DownloadStatus.COMPLETE.name } ?: return emptyMap()
        return decodeFiles(entity).associate { it.id to it.localPath }
    }

    suspend fun coverPath(bookId: Int): String? =
        dao.get(bookId)?.takeIf { it.status == DownloadStatus.COMPLETE.name }?.coverLocalPath

    fun decodeFiles(entity: DownloadEntity): List<DownloadedFile> =
        runCatching { json.decodeFromString(ListSerializer(DownloadedFile.serializer()), entity.filesJson) }
            .getOrDefault(emptyList())

    suspend fun startDownload(book: BookDetail) {
        if (BookFiles.downloadableFiles(book).isEmpty()) return
        dao.upsert(
            DownloadEntity(
                bookId = book.id,
                title = book.title,
                authors = book.authors.joinToString(", ") { it.name },
                narrators = book.audioMetadata?.narrators?.joinToString(", ") { it.name } ?: "",
                isAudiobook = BookFiles.isAudiobook(book),
                format = BookFiles.downloadableFiles(book).firstOrNull()?.format,
                sizeBytes = 0,
                downloadedAt = System.currentTimeMillis(),
                coverLocalPath = null,
                bookJson = json.encodeToString(BookDetail.serializer(), book),
                filesJson = "[]",
                status = DownloadStatus.DOWNLOADING.name,
                progress = 0f,
            ),
        )

        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_BOOK_ID to book.id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .addTag(DownloadWorker.tag(book.id))
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(DownloadWorker.tag(book.id), ExistingWorkPolicy.KEEP, request)
    }

    suspend fun delete(bookId: Int) {
        WorkManager.getInstance(context).cancelUniqueWork(DownloadWorker.tag(bookId))
        dao.delete(bookId)
        File(context.filesDir, "downloads/$bookId").deleteRecursively()
    }
}
