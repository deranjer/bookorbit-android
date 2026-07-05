package com.bookorbit.feature.bookdrop

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.bookorbit.core.model.BookDockBulkCounts
import com.bookorbit.core.model.BookDockFile
import com.bookorbit.core.model.BookDockFilesPage
import com.bookorbit.core.model.BookDockFinalizeResult
import com.bookorbit.core.model.BookDockSelection
import com.bookorbit.core.model.BookDockSummary
import com.bookorbit.core.model.FinalizeRequest
import com.bookorbit.core.model.Library
import com.bookorbit.core.model.SetTargetRequest
import com.bookorbit.core.model.UpdateBookDockFileRequest
import com.bookorbit.core.network.ApiService
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Read/write access to the server's Book Dock for the [BookDropViewModel]. */
@Singleton
class BookDropRepository @Inject constructor(
    private val api: ApiService,
    @ApplicationContext private val context: Context,
) {
    suspend fun files(status: String?, page: Int, limit: Int, search: String?): BookDockFilesPage =
        api.getBookDockFiles(status = status, page = page, limit = limit, search = search)

    suspend fun summary(): BookDockSummary = api.getBookDockSummary()

    suspend fun file(id: Int): BookDockFile = api.getBookDockFile(id)

    suspend fun update(id: Int, body: UpdateBookDockFileRequest): BookDockFile =
        api.updateBookDockFile(id, body)

    suspend fun discard(id: Int) = api.discardBookDockFile(id)

    suspend fun bulkDiscard(selection: BookDockSelection) = api.bulkDiscardBookDock(selection)

    suspend fun applyFetched(selection: BookDockSelection): BookDockBulkCounts =
        api.applyFetchedBookDock(selection)

    suspend fun setTarget(request: SetTargetRequest): BookDockBulkCounts =
        api.setBookDockTarget(request)

    suspend fun finalize(request: FinalizeRequest): BookDockFinalizeResult =
        api.finalizeBookDock(request)

    suspend fun libraries(): List<Library> = api.getLibraries()

    /**
     * Uploads a file the user picked on-device. Streams from the content [uri] rather than buffering
     * the whole (potentially large) book into memory.
     */
    suspend fun upload(uri: Uri): BookDockFile {
        val resolver = context.contentResolver
        val name = displayName(uri)
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val body = object : RequestBody() {
            override fun contentType() = mime.toMediaTypeOrNull()

            override fun contentLength(): Long = fileSize(uri)

            override fun writeTo(sink: BufferedSink) {
                val input = resolver.openInputStream(uri)
                    ?: throw IOException("Unable to open selected file")
                input.use { sink.writeAll(it.source()) }
            }
        }
        val part = MultipartBody.Part.createFormData("file", name, body)
        return api.uploadBookDockFile(part)
    }

    private fun displayName(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx)?.let { return it }
                }
            }
        return uri.lastPathSegment ?: "upload"
    }

    private fun fileSize(uri: Uri): Long {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0 && !cursor.isNull(idx)) return cursor.getLong(idx)
                }
            }
        return -1L
    }
}
