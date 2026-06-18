package com.bookorbit.feature.downloads

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.bookorbit.core.db.DownloadDao
import com.bookorbit.core.db.DownloadEntity
import com.bookorbit.core.model.BookDetail
import com.bookorbit.core.model.BookFiles
import com.bookorbit.core.network.ApiService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Downloads a book's files (and cover) for offline use, writing the final record into Room. Uses a
 * journaled download flow: already-complete files are skipped so an interrupted job resumes cleanly
 * on retry.
 */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val dao: DownloadDao,
    private val api: ApiService,
    private val json: Json,
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

        val dir = File(applicationContext.filesDir, "downloads/$bookId").apply { mkdirs() }
        val downloaded = mutableListOf<DownloadedFile>()

        try {
            files.forEachIndexed { index, ref ->
                val ext = ref.format?.lowercase()?.filter { it.isLetterOrDigit() }?.ifEmpty { "bin" } ?: "bin"
                val dest = File(dir, "${ref.id}.$ext")
                val complete = dest.exists() && (ref.sizeBytes == null || ref.sizeBytes <= 0 || dest.length() == ref.sizeBytes)
                if (!complete) {
                    api.serveFile(ref.id).byteStream().use { input ->
                        dest.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                downloaded.add(DownloadedFile(ref.id, dest.absolutePath, ref.filename, ref.format, ref.durationSeconds))
                val progress = (index + 1).toFloat() / files.size
                dao.updateProgress(bookId, progress)
                setProgress(workDataOf(KEY_PROGRESS to progress))
            }
        } catch (e: Exception) {
            dao.updateStatus(bookId, DownloadStatus.FAILED.name)
            return Result.retry()
        }

        // Cover is best-effort.
        val coverPath = runCatching {
            val coverFile = File(dir, "cover.jpg")
            api.serveCover(bookId).byteStream().use { input ->
                coverFile.outputStream().use { output -> input.copyTo(output) }
            }
            coverFile.absolutePath
        }.getOrNull()

        dao.upsert(
            entity.copy(
                status = DownloadStatus.COMPLETE.name,
                progress = 1f,
                coverLocalPath = coverPath,
                sizeBytes = downloaded.sumOf { File(it.localPath).length() },
                filesJson = json.encodeToString(ListSerializer(DownloadedFile.serializer()), downloaded),
            ),
        )
        return Result.success()
    }

    companion object {
        const val KEY_BOOK_ID = "bookId"
        const val KEY_PROGRESS = "progress"
        fun tag(bookId: Int) = "download-$bookId"
    }
}
