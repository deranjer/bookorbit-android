package com.bookorbit.feature.player

import com.bookorbit.core.model.BookCard
import com.bookorbit.core.model.BookFiles
import com.bookorbit.core.model.ScrollerType
import com.bookorbit.core.network.ApiService
import com.bookorbit.feature.bookdetail.BookDetailRepository
import com.bookorbit.feature.browse.BrowseRepository
import com.bookorbit.feature.downloads.DownloadsRepository
import com.bookorbit.feature.library.filters.LibraryFilters
import com.bookorbit.feature.library.filters.LibrarySort
import com.bookorbit.feature.library.filters.buildBookQuery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

/** Car-sized views of the online library, merged with the always-available download catalog. */
@Singleton
class AutoCatalog @Inject constructor(
    private val api: ApiService,
    private val browse: BrowseRepository,
    private val books: BookDetailRepository,
    private val downloads: DownloadsRepository,
    private val progress: AudioProgressRepository,
) {
    data class Page(val entries: List<AutoBrowseTree.BrowseEntry>, val total: Int)

    suspend fun continueListening(labels: AutoBrowseTree.Labels): List<AutoBrowseTree.BrowseEntry> {
        val history = progress.recent()
        val local = AutoBrowseTree.continueListening(downloads.downloads.first(), history, labels)
        val remote = try {
            withTimeout(4_000) { browse.scroller(ScrollerType.CONTINUE_LISTENING, 40) }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) { emptyList() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { emptyList() }
        val finished = history.filter { it.percentage >= ResumeCandidates.FINISHED_PERCENT }.map { it.bookId }.toSet()
        val online = remote.filter { it.id !in finished }.mapNotNull { entry(it, labels) }
        val byId = (online + local).associateBy { it.mediaId }
        val recent = history.filter { it.percentage < ResumeCandidates.FINISHED_PERCENT }
            .sortedByDescending { it.updatedAt }.mapNotNull { byId[AutoBrowseTree.bookMediaId(it.bookId)] }
        return (recent + online + local).distinctBy { it.mediaId }.take(80)
    }

    suspend fun page(page: Int, labels: AutoBrowseTree.Labels, query: String? = null): Page {
        require(page >= 0)
        val result = withTimeout(4_000) {
            api.queryBooks(buildBookQuery(LibraryFilters(formats = BookFiles.AUDIO_FORMATS.toList()),
                LibrarySort(), page, PAGE_SIZE, query))
        }
        return Page(result.items.mapNotNull { entry(it, labels) }, result.total)
    }

    suspend fun item(bookId: Int, labels: AutoBrowseTree.Labels): AutoBrowseTree.BrowseEntry? {
        AutoBrowseTree.downloadedAudiobooks(downloads.downloads.first(), labels)
            .firstOrNull { it.mediaId == AutoBrowseTree.bookMediaId(bookId) }?.let { return it }
        val book = withTimeout(4_000) { books.detail(bookId) }
        if (!BookFiles.isAudiobook(book)) return null
        return AutoBrowseTree.BrowseEntry(AutoBrowseTree.bookMediaId(book.id), book.title ?: labels.audiobook,
            PlaybackQueue.performerLabel(book).ifBlank { null }, null, true)
    }

    private fun entry(book: BookCard, labels: AutoBrowseTree.Labels): AutoBrowseTree.BrowseEntry? {
        if (book.files.none { it.format?.lowercase() in BookFiles.AUDIO_FORMATS }) return null
        return AutoBrowseTree.BrowseEntry(AutoBrowseTree.bookMediaId(book.id), book.title ?: labels.audiobook,
            book.narrators.ifEmpty { book.authors }.joinToString(", ").ifBlank { null }, null, true)
    }

    companion object { const val PAGE_SIZE = 40 }
}
