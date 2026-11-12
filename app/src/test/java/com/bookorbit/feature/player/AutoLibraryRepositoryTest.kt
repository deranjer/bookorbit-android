package com.bookorbit.feature.player

import com.bookorbit.core.auth.SessionManager
import com.bookorbit.core.db.DownloadEntity
import com.bookorbit.core.model.*
import com.bookorbit.core.network.ApiService
import com.bookorbit.feature.downloads.DownloadsRepository
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class AutoLibraryRepositoryTest {
    private val api = mockk<ApiService>()
    private val session = mockk<SessionManager>()
    private val downloads = mockk<DownloadsRepository>()
    private val local = MutableStateFlow<List<DownloadEntity>>(emptyList())
    private val repository = AutoLibraryRepository(api, session, downloads, Json { ignoreUnknownKeys = true })
    private val labels = AutoBrowseTree.Labels("App", "Continue", "Downloads", "Audiobook", { it })
    init {
        every { session.serverUrl } returns "https://one.example"
        every { session.currentUser } returns AuthUser(1, "one")
        every { session.accessToken } returns "test"
        every { session.dataScopeChanged() } returns false
        every { downloads.downloads } returns local
    }
    private fun book(id: Int) = BookCard(id, title = "Book $id", authors = listOf("Author"),
        files = listOf(BookFileRef(id, "mp3", "primary")), addedAt = "")
    private fun download(id: Int) = DownloadEntity(id, "Offline $id", "Local Author", "", true, "mp3", 0, 0,
        null, "{}", "[]", "COMPLETE", 1f)

    @Test fun `root modes require no network or download reads`() = runTest {
        assertEquals(3, repository.children(AutoBrowseTree.LIBRARY_ID, labels).size)
        coVerify(exactly = 0) { api.queryBooks(any()) }
    }

    @Test fun `index fetches all audio filtered pages once and reuses metadata for other modes`() = runTest {
        val queries = mutableListOf<BookQuery>()
        coEvery { api.queryBooks(capture(queries)) } answers {
            val page = firstArg<BookQuery>().pagination.page
            val start = page * 200 + 1
            BooksPage((start..minOf(start + 199, 405)).map(::book), total = 405, size = 200)
        }
        val authors = repository.children(AutoLibraryTree.AUTHORS, labels)
        assertEquals("405 books", authors.single().subtitle)
        repository.children(AutoLibraryTree.TITLES, labels)
        repository.children(AutoLibraryTree.SERIES, labels)
        assertEquals(listOf(0, 1, 2), queries.map { it.pagination.page }.sorted())
        assertTrue(queries.all { it.filter.toString().contains("m4b") })
    }

    @Test fun `failed requests are retried and complete downloads remain browsable offline`() = runTest {
        local.value = listOf(download(7), download(8).copy(status = "DOWNLOADING"))
        coEvery { api.queryBooks(any()) } throws IOException("offline")
        assertEquals(listOf("book/7"), repository.children(AutoLibraryTree.TITLES, labels).map { it.mediaId })
        assertEquals(listOf("Local Author"), repository.children(AutoLibraryTree.AUTHORS, labels).map { it.title })
        coVerify(exactly = 2) { api.queryBooks(any()) }
        coEvery { api.queryBooks(any()) } returns BooksPage(listOf(book(1)), total = 1)
        assertEquals(setOf("book/1", "book/7"), repository.children(AutoLibraryTree.TITLES, labels).map { it.mediaId }.toSet())
    }

    @Test fun `server page size caps do not hide later books`() = runTest {
        coEvery { api.queryBooks(any()) } answers {
            val page = firstArg<BookQuery>().pagination.page
            BooksPage((page * 20 + 1..minOf(page * 20 + 20, 45)).map(::book), total = 45, size = 20)
        }
        assertEquals("45 books", repository.children(AutoLibraryTree.AUTHORS, labels).single().subtitle)
        coVerify(exactly = 3) { api.queryBooks(any()) }
    }

    @Test fun `a partial failed scan is not cached as a complete collection`() = runTest {
        coEvery { api.queryBooks(any()) } answers {
            if (firstArg<BookQuery>().pagination.page == 0) BooksPage(listOf(book(1)), total = 2, size = 1)
            else throw IOException("page failed")
        }
        try { repository.children(AutoLibraryTree.TITLES, labels); fail("Must not silently truncate") }
        catch (_: IOException) { }
        coEvery { api.queryBooks(any()) } returns BooksPage(listOf(book(2)), total = 1)
        assertEquals("book/2", repository.children(AutoLibraryTree.TITLES, labels).single().mediaId)
    }

    @Test fun `account and server changes cannot reuse an earlier index`() = runTest {
        coEvery { api.queryBooks(any()) } returns BooksPage(listOf(book(1)), total = 1)
        assertEquals("book/1", repository.children(AutoLibraryTree.TITLES, labels).single().mediaId)
        every { session.currentUser } returns AuthUser(2, "two")
        coEvery { api.queryBooks(any()) } returns BooksPage(listOf(book(2)), total = 1)
        assertEquals("book/2", repository.children(AutoLibraryTree.TITLES, labels).single().mediaId)
        every { session.serverUrl } returns "https://two.example"
        coEvery { api.queryBooks(any()) } throws IOException("offline")
        try { repository.children(AutoLibraryTree.TITLES, labels); fail("Must not show another server's books") }
        catch (_: IOException) { }
    }

    @Test fun `scope changes during a request discard its response`() = runTest {
        coEvery { api.queryBooks(any()) } coAnswers {
            every { session.currentUser } returns AuthUser(2, "two")
            BooksPage(listOf(book(1)), total = 1)
        }
        try { repository.children(AutoLibraryTree.TITLES, labels); fail("Must discard old account response") }
        catch (_: AutoRequestException) { }
    }

    @Test fun `parent cancellation is never replaced with offline results`() = runTest {
        local.value = listOf(download(7))
        coEvery { api.queryBooks(any()) } coAnswers { delay(60_000); BooksPage() }
        try { withTimeout(50) { repository.children(AutoLibraryTree.TITLES, labels) }; fail("Must propagate cancellation") }
        catch (_: CancellationException) { }
    }

    @Test fun `signed out browsing uses downloads without network`() = runTest {
        every { session.currentUser } returns null
        every { session.accessToken } returns null
        local.value = listOf(download(7))
        assertEquals("book/7", repository.children(AutoLibraryTree.TITLES, labels).single().mediaId)
        coVerify(exactly = 0) { api.queryBooks(any()) }
    }
}
