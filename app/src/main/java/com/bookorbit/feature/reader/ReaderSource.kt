package com.bookorbit.feature.reader

import android.content.Context
import com.bookorbit.core.model.BookDetail
import com.bookorbit.core.model.BookFiles
import com.bookorbit.core.network.ApiService
import com.bookorbit.feature.downloads.DownloadsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves a book to a readable local file for the WebView: the host owns all networking; the
 * WebView only ever sees local bytes. Prefers an offline download
 * (wired in the downloads task) and otherwise fetches once to a reader cache reused on reopen.
 */
@Singleton
class ReaderSource @Inject constructor(
    private val api: ApiService,
    private val downloads: DownloadsRepository,
    @ApplicationContext private val context: Context,
) {
    data class ResolvedFile(val file: File, val format: String, val fileId: Int)

    suspend fun resolve(book: BookDetail): ResolvedFile = withContext(Dispatchers.IO) {
        val ref = BookFiles.readableFile(book) ?: throw IllegalStateException("This book has no readable file.")
        val format = ref.format?.lowercase().orEmpty()
        val ext = format.filter { it.isLetterOrDigit() }.ifEmpty { "bin" }

        // Prefer an offline download (plays without network).
        downloads.localFiles(book.id)[ref.id]?.let { path ->
            val local = File(path)
            if (local.exists()) return@withContext ResolvedFile(local, format, ref.id)
        }

        val cacheDir = File(context.cacheDir, "reader-cache").apply { mkdirs() }
        val dest = File(cacheDir, "${book.id}-${ref.id}.$ext")

        // Reuse a cached copy when it matches the server-reported size.
        val size = ref.sizeBytes
        if (dest.exists() && (size == null || size <= 0 || dest.length() == size)) {
            return@withContext ResolvedFile(dest, format, ref.id)
        }

        api.serveFile(ref.id).byteStream().use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        ResolvedFile(dest, format, ref.id)
    }
}
