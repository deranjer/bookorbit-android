package com.bookorbit.feature.downloads

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.bookorbit.core.db.DownloadDao
import com.bookorbit.core.db.DownloadEntity
import com.bookorbit.core.model.BookDetail
import com.bookorbit.core.model.BookFileRef
import com.bookorbit.core.model.BookFiles
import com.bookorbit.core.network.ApiService
import com.bookorbit.core.settings.DownloadLocationStore
import com.bookorbit.core.storage.DownloadFileNaming
import com.bookorbit.core.storage.LocalRef
import com.bookorbit.core.storage.length
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Downloads a book's files (and cover) for offline use, writing the final record into Room. Uses a
 * journaled download flow: already-complete files are skipped so an interrupted job resumes cleanly
 * on retry. Writes into the user-chosen SAF folder ([DownloadLocationStore]) when one is configured
 * and still accessible; otherwise (or if it's become inaccessible) falls back to app-private storage
 * and the resulting record is marked [DownloadStatus.COMPLETE_FALLBACK] instead of
 * [DownloadStatus.COMPLETE] so the UI can surface that it didn't land where configured. The cover
 * always writes to app-private storage regardless of the configured folder.
 */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val dao: DownloadDao,
    private val api: ApiService,
    private val json: Json,
    private val locationStore: DownloadLocationStore,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val bookId = inputData.getInt(KEY_BOOK_ID, -1)
        if (bookId < 0) return Result.failure()
        val entity = dao.get(bookId) ?: return Result.failure()
        val book = runCatching { json.decodeFromString<BookDetail>(entity.bookJson) }.getOrNull()
            ?: return Result.failure()

        val files = BookFiles.downloadableFiles(book)
        if (files.isEmpty()) {
            dao.updateStatus(bookId, DownloadStatus.FAILED.name)
            return Result.failure()
        }

        val internalDir = File(applicationContext.filesDir, "downloads/$bookId").apply { mkdirs() }

        // configuredTreeUri (raw preference) vs treeUri (validated) are deliberately distinct: a
        // configured-but-currently-inaccessible tree must still count as a fallback below, whereas
        // "nothing configured" is just the ordinary internal-storage case.
        val configuredTreeUri = locationStore.treeUri.first()
        val treeUri = configuredTreeUri?.takeIf(locationStore::isAccessible)
        val bookFolder = treeUri?.let { tree ->
            val root = DocumentFile.fromTreeUri(applicationContext, tree) ?: return@let null
            val name = DownloadFileNaming.bookFolderName(book.title, bookId)
            root.findFile(name)?.takeIf { it.isDirectory } ?: root.createDirectory(name)
        }
        // Configured but unusable right now (permission revoked, tree gone, or folder creation
        // failed) - fall back silently for this run rather than failing the whole download.
        val fellBack = configuredTreeUri != null && bookFolder == null

        val downloaded = mutableListOf<DownloadedFile>()

        try {
            files.forEachIndexed { index, ref ->
                val name = DownloadFileNaming.fileName(ref, index, book)
                val localPath = if (bookFolder != null) {
                    downloadToSaf(bookFolder, name, ref)
                } else {
                    downloadToInternal(internalDir, name, ref)
                }
                downloaded.add(DownloadedFile(ref.id, localPath, ref.filename, ref.format, ref.durationSeconds))
                val progress = (index + 1).toFloat() / files.size
                dao.updateProgress(bookId, progress)
                setProgress(workDataOf(KEY_PROGRESS to progress))
            }
        } catch (e: Exception) {
            dao.updateStatus(bookId, DownloadStatus.FAILED.name)
            return Result.retry()
        }

        // Cover is best-effort, and always internal (see class doc).
        val coverPath = runCatching {
            val coverFile = File(internalDir, "cover.jpg")
            api.serveCover(bookId).byteStream().use { input ->
                coverFile.outputStream().use { output -> input.copyTo(output) }
            }
            Uri.fromFile(coverFile).toString()
        }.getOrNull()

        val finalStatus = if (fellBack) DownloadStatus.COMPLETE_FALLBACK else DownloadStatus.COMPLETE
        dao.upsert(
            entity.copy(
                status = finalStatus.name,
                progress = 1f,
                coverLocalPath = coverPath,
                sizeBytes = downloaded.sumOf { LocalRef.parse(it.localPath).length(applicationContext) },
                filesJson = json.encodeToString(ListSerializer(DownloadedFile.serializer()), downloaded),
            ),
        )
        return Result.success()
    }

    /** Resumability journal against DocumentFile: a same-named, size-matching document is skipped. */
    private suspend fun downloadToSaf(folder: DocumentFile, name: String, ref: BookFileRef): String {
        val existing = folder.findFile(name)
        val complete = existing != null &&
            (ref.sizeBytes == null || ref.sizeBytes <= 0 || existing.length() == ref.sizeBytes)
        val doc = when {
            complete -> existing!!
            existing != null -> {
                existing.delete() // stale/partial from an interrupted run - recreate
                createSafFile(folder, name, ref)
            }
            else -> createSafFile(folder, name, ref)
        }
        if (!complete) {
            api.serveFile(ref.id).byteStream().use { input ->
                applicationContext.contentResolver.openOutputStream(doc.uri)!!.use { output ->
                    input.copyTo(output)
                }
            }
        }
        return doc.uri.toString()
    }

    private fun createSafFile(folder: DocumentFile, name: String, ref: BookFileRef): DocumentFile {
        val mime = DownloadFileNaming.mimeTypeFor(DownloadFileNaming.extensionOf(ref))
        return folder.createFile(mime, name) ?: error("Unable to create $name in the chosen folder")
    }

    private suspend fun downloadToInternal(dir: File, name: String, ref: BookFileRef): String {
        val dest = File(dir, name)
        val complete = dest.exists() &&
            (ref.sizeBytes == null || ref.sizeBytes <= 0 || dest.length() == ref.sizeBytes)
        if (!complete) {
            api.serveFile(ref.id).byteStream().use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return Uri.fromFile(dest).toString()
    }

    companion object {
        const val KEY_BOOK_ID = "bookId"
        const val KEY_PROGRESS = "progress"
        fun tag(bookId: Int) = "download-$bookId"
    }
}
