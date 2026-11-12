package com.bookorbit.feature.player

import com.bookorbit.core.db.AudioProgressEntity
import com.bookorbit.core.db.DownloadEntity
import com.bookorbit.core.model.BookCard
import com.bookorbit.core.model.BookFileRef
import com.bookorbit.core.model.BookQuery
import com.bookorbit.core.model.BooksPage
import com.bookorbit.core.network.ApiService
import com.bookorbit.feature.bookdetail.BookDetailRepository
import com.bookorbit.feature.browse.BrowseRepository
import com.bookorbit.feature.downloads.DownloadsRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AutoCatalogTest {
    private val api = mockk<ApiService>()
    private val browse = mockk<BrowseRepository>()
    private val books = mockk<BookDetailRepository>()
    private val downloads = mockk<DownloadsRepository>()
    private val progress = mockk<AudioProgressRepository>()
    private val downloaded = MutableStateFlow<List<DownloadEntity>>(emptyList())
    private val catalog = AutoCatalog(api, browse, books, downloads, progress)
    private val labels = AutoBrowseTree.Labels("App", "Continue", "Downloaded", "Audiobook", { it })
    init {
        every { downloads.downloads } returns downloaded
        coEvery { progress.recent() } returns emptyList()
    }
    private fun card(id: Int, format: String = "mp3") = BookCard(id, title = "Book $id", addedAt = "",
        files = listOf(BookFileRef(id, format, "primary")))
    private fun download(id: Int) = DownloadEntity(id, "Book $id", "Author", "", true, "mp3", 0, 0,
        null, "{}", "[]", "COMPLETE", 1f)
    private fun point(id: Int, percent: Double, at: Long = 1) = AudioProgressEntity(id, id, 10.0, percent, at, false)

    @Test fun `catalog applies the audiobook filter before server pagination`() = runTest {
        val query = slot<BookQuery>()
        coEvery { api.queryBooks(capture(query)) } returns BooksPage(listOf(card(1), card(2, "epub")), total = 90)
        val page = catalog.page(1, labels, "Dune")
        assertEquals(listOf("book/1"), page.entries.map { it.mediaId })
        assertEquals(90, page.total)
        assertEquals(1, query.captured.pagination.page)
        assertEquals("Dune", query.captured.q)
        assertTrue(query.captured.filter.toString().contains("m4b"))
    }

    @Test fun `continue includes streaming books and deduplicates downloaded books`() = runTest {
        downloaded.value = listOf(download(1))
        coEvery { progress.recent() } returns listOf(point(1, 40.0))
        coEvery { browse.scroller(any(), any(), any()) } returns listOf(card(1), card(2), card(3, "epub"))
        assertEquals(listOf("book/1", "book/2"), catalog.continueListening(labels).map { it.mediaId })
    }

    @Test fun `offline continue keeps usable downloads`() = runTest {
        downloaded.value = listOf(download(1))
        coEvery { progress.recent() } returns listOf(point(1, 40.0))
        coEvery { browse.scroller(any(), any(), any()) } coAnswers { delay(60_000); emptyList() }
        assertEquals(listOf("book/1"), catalog.continueListening(labels).map { it.mediaId })
    }

    @Test fun `locally finished books stay out of the server shelf`() = runTest {
        coEvery { progress.recent() } returns listOf(point(1, 100.0))
        coEvery { browse.scroller(any(), any(), any()) } returns listOf(card(1), card(2))
        assertEquals(listOf("book/2"), catalog.continueListening(labels).map { it.mediaId })
    }
}
