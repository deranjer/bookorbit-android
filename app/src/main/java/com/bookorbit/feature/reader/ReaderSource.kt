package com.bookorbit.feature.reader

import android.content.Context
import com.bookorbit.core.auth.SessionManager
import com.bookorbit.core.model.BookDetail
import com.bookorbit.core.model.BookFileRef
import com.bookorbit.core.model.BookFiles
import com.bookorbit.core.network.ApiService
import com.bookorbit.core.storage.LocalRef
import com.bookorbit.core.storage.exists
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
    private val session: SessionManager,
    @ApplicationContext private val context: Context,
) {
    data class ResolvedFile(val ref: LocalRef, val format: String, val fileId: Int)

    /** Resolve the foliate-readable file for a book to a local file. */
    suspend fun resolve(book: BookDetail, fileId: Int): ResolvedFile {
        val ref = (BookFiles.readingTarget(book, fileId) as? BookFiles.ReadingTarget.Foliate)?.file
            ?: throw IllegalStateException("The selected reading file is unavailable.")
        return resolveFile(book, ref)
    }

    /** Resolve the PDF file for a book to a local file (native PDF reader). */
    suspend fun resolvePdf(book: BookDetail, fileId: Int): ResolvedFile {
        val ref = (BookFiles.readingTarget(book, fileId) as? BookFiles.ReadingTarget.Pdf)?.file
            ?: throw IllegalStateException("The selected PDF file is unavailable.")
        return resolveFile(book, ref)
    }

    /**
     * Resolve a specific book file to a local file: an offline download wins, otherwise it is
     * fetched once into a reader cache reused on reopen. The host owns all networking.
     */
    suspend fun resolveFile(book: BookDetail, ref: BookFileRef): ResolvedFile = withContext(Dispatchers.IO) {
        val format = ref.format?.lowercase().orEmpty()
        val ext = format.filter { it.isLetterOrDigit() }.ifEmpty { "bin" }

        // Prefer an offline download (plays without network).
        downloads.localFiles(book.id)[ref.id]?.let { path ->
            val local = LocalRef.parse(path)
            if (local.exists(context)) return@withContext ResolvedFile(local, format, ref.id)
        }

        // New namespace deliberately excludes potentially partial legacy cache entries.
        val cacheDir = File(context.cacheDir, "reader-cache-v2/${session.dataScope.ifEmpty { "owner" }}/${session.currentUser?.id ?: 0}")
        val dest = File(cacheDir, "${book.id}-${ref.id}.$ext")
        ReaderFileCache.getOrFetch(dest, ref.sizeBytes) { partial ->
            api.serveFile(ref.id).use { body ->
                val copied = body.byteStream().use { input ->
                    partial.outputStream().use { output -> input.copyTo(output) }
                }
                check(body.contentLength() < 0 || copied == body.contentLength()) { "Incomplete reading response." }
            }
        }
        ResolvedFile(LocalRef.PlainFile(dest), format, ref.id)
    }
}
