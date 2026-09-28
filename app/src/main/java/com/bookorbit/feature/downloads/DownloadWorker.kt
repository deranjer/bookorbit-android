package com.bookorbit.feature.downloads

import android.content.Context
import android.net.Uri
import android.util.Log
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
import com.bookorbit.core.network.AudiobookAssetResolver
import com.bookorbit.core.network.AudiobookAssetResolver.Sources
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
    private val audiobookAssets: AudiobookAssetResolver,
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
            dao.updateFailure(bookId, DownloadStatus.FAILED.name, "This book has no files available to download.")
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
        val knownSizes = files.map { it.sizeBytes?.takeIf { size -> size > 0 } }
        val useByteWeights = knownSizes.all { it != null }
        val weights = if (useByteWeights) knownSizes.map { it!!.toDouble() }
            else files.map { 1.0 }
        val totalWeight = weights.sum().coerceAtLeast(1.0)
        var completedWeight = 0.0
        var activeStage = "preparing"
        var activeFileId: Int? = null

        try {
            activeStage = "audiobook_manifest"
            val audioFiles = files.filter { it.format?.lowercase() in BookFiles.AUDIO_FORMATS }
            val sources = if (audioFiles.isEmpty()) Sources.Legacy
                else audiobookAssets.resolve(bookId, audioFiles)
            files.forEachIndexed { index, ref ->
                activeFileId = ref.id
                val name = DownloadFileNaming.fileName(ref, index, book)
                activeStage = "request_or_transfer"
                val localPath = if (bookFolder != null) {
                    downloadToSaf(bookFolder, name, ref, bookId, sources, completedWeight, weights[index], totalWeight)
                } else {
                    downloadToInternal(internalDir, name, ref, bookId, sources, completedWeight, weights[index], totalWeight)
                }
                downloaded.add(DownloadedFile(ref.id, localPath, ref.filename, ref.format, ref.durationSeconds))
                completedWeight += weights[index]
                val progress = (completedWeight / totalWeight).toFloat().coerceIn(0f, 1f)
                dao.updateProgress(bookId, progress)
                setProgress(workDataOf(KEY_PROGRESS to progress))
            }
        } catch (e: Exception) {
            val failure = e as? DownloadStageException
            val stage = failure?.stage ?: activeStage
            val cause = failure?.cause ?: e
            Log.e(TAG, "download failed bookId=$bookId fileId=$activeFileId attempt=$runAttemptCount " +
                "stage=$stage " +
                "destination=${if (bookFolder != null) "saf" else "internal"} " +
                "exception=${cause.javaClass.simpleName} " +
                "message=${cause.message}", e)
            // WorkManager's Result.retry() alone would retry forever - cap it so a persistently
            // failing download (e.g. a server that can't serve this file) surfaces as a real,
            // explained failure instead of sitting at 0% and quietly vanishing from the UI.
            if (runAttemptCount < MAX_ATTEMPTS - 1) {
                return Result.retry()
            }
            val reason = "Failed while ${stage.replace('_', ' ')}: ${cause.javaClass.simpleName}" +
                (cause.message?.let { " ($it)" } ?: "")
            dao.updateFailure(bookId, DownloadStatus.FAILED.name, reason)
            return Result.failure()
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
                lastError = null,
                coverLocalPath = coverPath,
                sizeBytes = downloaded.sumOf { LocalRef.parse(it.localPath).length(applicationContext) },
                filesJson = json.encodeToString(ListSerializer(DownloadedFile.serializer()), downloaded),
            ),
        )
        return Result.success()
    }

    /** Resumability journal against DocumentFile: a same-named, size-matching document is skipped. */
    private suspend fun downloadToSaf(
        folder: DocumentFile, name: String, ref: BookFileRef, bookId: Int, sources: Sources,
        completedWeight: Double, fileWeight: Double, totalWeight: Double,
    ): String {
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
            activeNetworkCall(bookId, ref.id, sources) { body ->
                applicationContext.contentResolver.openOutputStream(doc.uri)?.use { output ->
                    body.byteStream().use { input ->
                        copyWithProgress(input, output, body.contentLength(), ref.sizeBytes,
                            completedWeight, fileWeight, totalWeight)
                    }
                } ?: error("Unable to open the selected storage destination")
            }
        }
        return doc.uri.toString()
    }

    private fun createSafFile(folder: DocumentFile, name: String, ref: BookFileRef): DocumentFile {
        val mime = DownloadFileNaming.mimeTypeFor(DownloadFileNaming.extensionOf(ref))
        return folder.createFile(mime, name) ?: error("Unable to create $name in the chosen folder")
    }

    private suspend fun downloadToInternal(
        dir: File, name: String, ref: BookFileRef, bookId: Int, sources: Sources,
        completedWeight: Double, fileWeight: Double, totalWeight: Double,
    ): String {
        val dest = File(dir, name)
        val complete = dest.exists() &&
            (ref.sizeBytes == null || ref.sizeBytes <= 0 || dest.length() == ref.sizeBytes)
        if (!complete) {
            activeNetworkCall(bookId, ref.id, sources) { body ->
                body.byteStream().use { input ->
                    dest.outputStream().use { output ->
                        copyWithProgress(input, output, body.contentLength(), ref.sizeBytes,
                            completedWeight, fileWeight, totalWeight)
                    }
                }
            }
        }
        return Uri.fromFile(dest).toString()
    }

    private suspend fun activeNetworkCall(
        bookId: Int, fileId: Int, sources: Sources,
        consume: suspend (okhttp3.ResponseBody) -> Unit,
    ) {
        val assetId = (sources as? Sources.Assets)?.assetIds?.get(fileId)
        val body = try {
            if (assetId != null) api.serveAudiobookAsset(bookId, assetId) else api.serveFile(fileId)
        } catch (e: Exception) {
            throw DownloadStageException("server_request", e)
        }
        try {
            consume(body)
        } catch (e: Exception) {
            throw DownloadStageException("response_read_or_storage_write", e)
        } finally {
            body.close()
        }
    }

    private suspend fun copyWithProgress(
        input: java.io.InputStream,
        output: java.io.OutputStream,
        responseLength: Long,
        declaredLength: Long?,
        completedWeight: Double,
        fileWeight: Double,
        totalWeight: Double,
    ) {
        val length = declaredLength?.takeIf { it > 0 } ?: responseLength.takeIf { it > 0 }
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        var copied = 0L
        var lastReportedBytes = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
            copied += count
            if (copied - lastReportedBytes >= PROGRESS_REPORT_BYTES && length != null) {
                val withinFile = (copied.toDouble() / length).coerceIn(0.0, 1.0)
                val progress = ((completedWeight + fileWeight * withinFile) / totalWeight)
                    .toFloat().coerceIn(0f, 0.999f)
                dao.updateProgress(inputData.getInt(KEY_BOOK_ID, -1), progress)
                setProgress(workDataOf(KEY_PROGRESS to progress))
                lastReportedBytes = copied
            }
        }
        output.flush()
    }

    companion object {
        private const val TAG = "BookOrbitDownload"
        private const val COPY_BUFFER_BYTES = 64 * 1024
        private const val PROGRESS_REPORT_BYTES = 512 * 1024L
        /** Total attempts (initial + retries) before a failing download is reported as terminal. */
        private const val MAX_ATTEMPTS = 5
        const val KEY_BOOK_ID = "bookId"
        const val KEY_PROGRESS = "progress"
        fun tag(bookId: Int) = "download-$bookId"
    }

    private class DownloadStageException(val stage: String, cause: Exception) : Exception(cause)
}
