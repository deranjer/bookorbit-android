package com.bookorbit.feature.downloads

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.bookorbit.core.auth.SessionManager
import com.bookorbit.core.db.DownloadDao
import com.bookorbit.core.db.DownloadEntity
import com.bookorbit.core.model.BookDetail
import com.bookorbit.core.model.BookFiles
import com.bookorbit.core.settings.AppSettingsStore
import com.bookorbit.core.settings.DownloadLocationStore
import com.bookorbit.core.storage.DownloadFileNaming
import com.bookorbit.core.storage.LocalRef
import com.bookorbit.core.storage.delete
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
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
    private val appSettings: AppSettingsStore,
    private val locationStore: DownloadLocationStore,
    private val session: SessionManager,
) {
    val downloads: Flow<List<DownloadEntity>> = dao.observeAll()

    fun observe(bookId: Int): Flow<DownloadEntity?> = dao.observe(bookId)

    suspend fun get(bookId: Int): DownloadEntity? = dao.get(bookId)

    suspend fun isDownloaded(bookId: Int): Boolean = dao.get(bookId)?.status?.isCompleteDownloadStatus() == true

    /** The BookDetail persisted at download time, so book detail/reader/player screens work fully offline. */
    suspend fun cachedBook(bookId: Int): BookDetail? =
        dao.get(bookId)?.let { runCatching { json.decodeFromString(BookDetail.serializer(), it.bookJson) }.getOrNull() }

    /** Map of fileId -> local file path for a completed download (else empty). */
    suspend fun localFiles(bookId: Int): Map<Int, String> {
        val entity = dao.get(bookId)?.takeIf { it.status.isCompleteDownloadStatus() } ?: return emptyMap()
        return decodeFiles(entity).associate { it.id to it.localPath }
    }

    suspend fun coverPath(bookId: Int): String? =
        dao.get(bookId)?.takeIf { it.status.isCompleteDownloadStatus() }?.coverLocalPath

    fun decodeFiles(entity: DownloadEntity): List<DownloadedFile> =
        runCatching { json.decodeFromString(ListSerializer(DownloadedFile.serializer()), entity.filesJson) }
            .getOrDefault(emptyList())

    suspend fun startDownload(book: BookDetail, selectedFileIds: Set<Int>) {
        val files = BookFiles.selectedDownloads(book, selectedFileIds)
        // Never reset a running/completed plan or hide files already on disk.
        val inserted = dao.insertPlan(
            DownloadEntity(
                bookId = book.id,
                title = book.title,
                authors = book.authors.joinToString(", ") { it.name },
                narrators = book.audioMetadata?.narrators?.joinToString(", ") { it.name } ?: "",
                isAudiobook = files.any { it.format?.lowercase() in BookFiles.AUDIO_FORMATS },
                format = files.first().format,
                sizeBytes = 0,
                downloadedAt = System.currentTimeMillis(),
                coverLocalPath = null,
                bookJson = json.encodeToString(BookDetail.serializer(), book),
                filesJson = "[]",
                status = DownloadStatus.DOWNLOADING.name,
                progress = 0f,
                selectedFileIdsJson = json.encodeToString(files.map { it.id }),
            ),
        )
        if (inserted == -1L) return

        enqueue(book.id)
    }

    private suspend fun enqueue(bookId: Int) {
        val networkType = if (appSettings.wifiOnlyDownloads.first()) NetworkType.UNMETERED else NetworkType.CONNECTED
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_BOOK_ID to bookId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(networkType).build())
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .addTag(DownloadWorker.tag(bookId))
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(DownloadWorker.tag(bookId), ExistingWorkPolicy.KEEP, request)
    }

    /** Restarts a download after a terminal [DownloadStatus.FAILED], using the last-known book detail. */
    suspend fun retryDownload(bookId: Int) {
        val entity = dao.get(bookId) ?: return
        if (entity.status != DownloadStatus.FAILED.name) return
        dao.upsert(entity.copy(status = DownloadStatus.DOWNLOADING.name, lastError = null))
        enqueue(bookId)
    }

    suspend fun delete(bookId: Int) {
        WorkManager.getInstance(context).cancelUniqueWork(DownloadWorker.tag(bookId))
        val entity = dao.get(bookId)
        dao.delete(bookId)
        entity?.let {
            deleteSafFolderIfAny(it)
            // Per-file fallback in case the folder-name re-derivation misses (e.g. the tree Uri
            // changed since download) - each file still gets removed via its own stored ref.
            decodeFiles(it).forEach { f -> LocalRef.parse(f.localPath).delete(context) }
        }
        // Handles the cover (always internal) plus legacy/fallback-mode files in one sweep.
        DownloadFolders.internalDir(context, session.dataScope, bookId).deleteRecursively()
    }

    private suspend fun deleteSafFolderIfAny(entity: DownloadEntity) {
        val tree = locationStore.treeUri.first() ?: return
        val name = DownloadFileNaming.bookFolderName(entity.title, entity.bookId)
        runCatching { DocumentFile.fromTreeUri(context, tree)?.findFile(name)?.delete() }
    }

    /** Cancels and removes every download, for the Settings screen's "clear all downloads" action. */
    suspend fun deleteAll() {
        dao.observeAll().first().forEach { delete(it.bookId) }
    }
}
