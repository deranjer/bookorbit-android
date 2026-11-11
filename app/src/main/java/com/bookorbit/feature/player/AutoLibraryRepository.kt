package com.bookorbit.feature.player

import com.bookorbit.core.auth.SessionManager
import com.bookorbit.core.model.BookCard
import com.bookorbit.core.model.BookDetail
import com.bookorbit.core.model.BookFileRef
import com.bookorbit.core.model.BookFiles
import com.bookorbit.core.network.ApiService
import com.bookorbit.feature.downloads.DownloadsRepository
import com.bookorbit.feature.downloads.isCompleteDownloadStatus
import com.bookorbit.feature.library.filters.LibraryFilters
import com.bookorbit.feature.library.filters.LibrarySort
import com.bookorbit.feature.library.filters.buildBookQuery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** A short-lived, account-scoped metadata index avoids repeated scans while browsing car folders. */
@Singleton
class AutoLibraryRepository @Inject constructor(
    private val api: ApiService,
    private val session: SessionManager,
    private val downloads: DownloadsRepository,
    private val json: Json,
) {
    private data class Snapshot(val scope: String, val loadedAt: Long, val books: List<BookCard>)
    private var snapshot: Snapshot? = null
    private val lock = Mutex()
    private fun scope() = "${session.serverUrl}|${session.currentUser?.id}"

    suspend fun children(id: String, labels: AutoBrowseTree.Labels): List<AutoBrowseTree.BrowseEntry> =
        if (id == AutoBrowseTree.LIBRARY_ID) AutoLibraryTree.modes(labels)
        else AutoLibraryTree.children(id, books(), labels)

    suspend fun item(id: String, labels: AutoBrowseTree.Labels): AutoBrowseTree.BrowseEntry? =
        AutoLibraryTree.modes(labels).firstOrNull { it.mediaId == id }
            ?: AutoLibraryTree.item(id, books(), labels)

    private suspend fun books(): List<BookCard> = lock.withLock {
        if (session.dataScopeChanged()) throw AutoRequestException(AutoFailure.INVALID_REQUEST)
        val requestScope = scope()
        val local = downloads.downloads.first().filter { it.isAudiobook && it.status.isCompleteDownloadStatus() }.map { download ->
            val book = runCatching { json.decodeFromString(BookDetail.serializer(), download.bookJson) }.getOrNull()
            BookCard(download.bookId, title = download.title,
                authors = book?.authors?.map { it.name }?.takeIf { it.isNotEmpty() } ?: listOfNotNull(download.authors.takeIf { it.isNotBlank() }),
                seriesName = book?.seriesName, seriesIndex = book?.seriesIndex,
                files = book?.files?.takeIf { it.isNotEmpty() } ?: listOf(BookFileRef(download.bookId, download.format ?: "mp3", "primary")),
                narrators = book?.audioMetadata?.narrators?.map { it.name }.orEmpty(), addedAt = "")
        }
        if (scope() != requestScope) throw AutoRequestException(AutoFailure.INVALID_REQUEST)
        if (session.currentUser == null || session.accessToken == null) {
            snapshot = null
            return@withLock local
        }
        val cached = snapshot?.takeIf { it.scope == requestScope }
        val remote = if (cached != null && System.nanoTime() - cached.loadedAt < 120_000_000_000L) cached.books else {
            try {
                val loaded = withTimeoutOrNull(10_000) { loadAll() }
                    ?: throw java.io.IOException("Audiobook catalog timed out")
                if (scope() != requestScope) throw AutoRequestException(AutoFailure.INVALID_REQUEST)
                snapshot = Snapshot(requestScope, System.nanoTime(), loaded)
                loaded
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (scope() != requestScope || e is AutoRequestException) throw e
                cached?.books ?: local.takeIf { it.isNotEmpty() } ?: throw e
            }
        }
        if (scope() != requestScope) throw AutoRequestException(AutoFailure.INVALID_REQUEST)
        (remote + local).distinctBy { it.id }
    }

    private suspend fun loadAll(): List<BookCard> = coroutineScope {
        suspend fun page(index: Int, size: Int = 200) = api.queryBooks(buildBookQuery(
            LibraryFilters(formats = BookFiles.AUDIO_FORMATS.toList()), LibrarySort(), index, size))
        val first = page(0)
        // Respect older servers that cap the requested page size.
        val size = first.size.takeIf { it in 1..200 }
            ?: first.items.size.takeIf { it in 1..199 && first.total > it } ?: 200
        val pages = ((first.total.toLong() + size - 1) / size).toInt()
        val result = first.items.toMutableList()
        // Bound both requests and coroutine allocation for very large collections.
        for (start in 1 until pages step 4) {
            result += (start until minOf(start + 4, pages)).map { index -> async { page(index, size).items } }.awaitAll().flatten()
        }
        result.distinctBy { it.id }
    }
}
