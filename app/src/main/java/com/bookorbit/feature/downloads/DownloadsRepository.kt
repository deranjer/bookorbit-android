package com.bookorbit.feature.downloads

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.bookorbit.core.auth.SessionManager
import com.bookorbit.core.db.DownloadDao
import com.bookorbit.core.db.DownloadEntity
import com.bookorbit.core.model.BookDetail
import com.bookorbit.core.model.BookFiles
import com.bookorbit.core.settings.DownloadLocationStore
import com.bookorbit.core.storage.DownloadFileNaming
import com.bookorbit.core.storage.LocalRef
import com.bookorbit.core.storage.delete
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Orchestrates downloads (WorkManager) and exposes the offline catalog. */
@Singleton
class DownloadsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DownloadDao,
    private val json: Json,
    private val scheduler: DownloadWorkScheduler,
    private val locationStore: DownloadLocationStore,
    private val session: SessionManager,
) {
    private val changes = Mutex()
    val downloads: Flow<List<DownloadEntity>> = dao.observeAll()

    fun observe(bookId: Int): Flow<DownloadEntity?> = dao.observe(bookId)

    suspend fun get(bookId: Int): DownloadEntity? = dao.get(bookId)

    suspend fun isDownloaded(bookId: Int): Boolean = dao.get(bookId)?.status?.isCompleteDownloadStatus() == true

    /** The BookDetail persisted at download time, so book detail/reader/player screens work fully offline. */
    suspend fun cachedBook(bookId: Int): BookDetail? =
        dao.get(bookId)?.let { runCatching { json.decodeFromString(BookDetail.serializer(), it.bookJson) }.getOrNull() }

    /** Published files stay usable while additional formats are downloading or have failed. */
    suspend fun localFiles(bookId: Int): Map<Int, String> {
        val entity = dao.get(bookId) ?: return emptyMap()
        return decodeFiles(entity).associate { it.id to it.localPath }
    }

    suspend fun coverPath(bookId: Int): String? =
        dao.get(bookId)?.coverLocalPath

    fun decodeFiles(entity: DownloadEntity): List<DownloadedFile> =
        runCatching { json.decodeFromString(ListSerializer(DownloadedFile.serializer()), entity.filesJson) }
            .getOrDefault(emptyList())

    suspend fun startDownload(book: BookDetail, selectedFileIds: Set<Int>) = change {
        val files = BookFiles.selectedDownloads(book, selectedFileIds)
        val existing = dao.get(book.id)
        if (existing != null) {
            val updated = DownloadPlan.expand(existing, book, selectedFileIds, json) ?: return@change
            if (dao.replacePlan(existing, updated)) enqueue(book.id, updated.planId)
            return@change
        }
        val planId = UUID.randomUUID().toString()
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
                planId = planId,
                storageId = planId,
            ),
        )
        if (inserted != -1L) enqueue(book.id, planId)
    }

    /** Restarts a download after a terminal [DownloadStatus.FAILED], using the last-known book detail. */
    suspend fun retryDownload(bookId: Int) = change {
        val entity = dao.get(bookId) ?: return@change
        if (entity.status != DownloadStatus.FAILED.name) return@change
        val updated = entity.copy(status = DownloadStatus.DOWNLOADING.name, lastError = null, planId = UUID.randomUUID().toString())
        if (dao.replacePlan(entity, updated)) enqueue(bookId, updated.planId)
    }

    private suspend fun enqueue(bookId: Int, planId: String) {
        try {
            scheduler.enqueue(bookId, planId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            dao.failPlan(bookId, planId, DownloadStatus.FAILED.name, "Could not schedule download. Please retry.")
            throw e
        }
    }

    suspend fun delete(bookId: Int) = change {
        scheduler.cancel(bookId)

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

    /** Finish DB + WorkManager bookkeeping even if the user immediately leaves the detail screen. */
    private suspend fun <T> change(action: suspend () -> T): T = withContext(NonCancellable + Dispatchers.IO) {
        changes.withLock { action() }
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
