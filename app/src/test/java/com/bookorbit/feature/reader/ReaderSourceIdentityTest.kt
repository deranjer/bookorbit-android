package com.bookorbit.feature.reader

import android.content.Context
import com.bookorbit.core.auth.SessionManager
import com.bookorbit.core.model.AuthUser
import com.bookorbit.core.model.BookDetail
import com.bookorbit.core.model.BookFileRef
import com.bookorbit.core.network.ApiService
import com.bookorbit.feature.downloads.DownloadsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReaderSourceIdentityTest {
    @get:Rule val temp = TemporaryFolder()
    private val api = mockk<ApiService>()
    private val downloads = mockk<DownloadsRepository>()
    private val session = mockk<SessionManager> {
        every { dataScope } returns "server-a"
        every { currentUser } returns AuthUser(7, "test")
    }
    private val book = BookDetail(
        id = 1, libraryId = 1, libraryName = "Test", status = "present", addedAt = "2026-11-22",
        files = listOf(BookFileRef(11, "epub", "primary"), BookFileRef(12, "epub", "supplement"), BookFileRef(13, "pdf", "supplement")),
    )
    private fun source(): ReaderSource {
        val context = mockk<Context> { every { cacheDir } returns temp.root }
        coEvery { downloads.localFiles(1) } returns emptyMap()
        return ReaderSource(api, downloads, session, context)
    }

    @Test fun `secondary EPUB is fetched and cached by exact ID`() = runTest {
        coEvery { api.serveFile(12) } returns "secondary".toResponseBody()
        val resolved = source().resolve(book, 12)
        assertEquals(12, resolved.fileId)
        coVerify(exactly = 1) { api.serveFile(12) }
        coVerify(exactly = 0) { api.serveFile(11) }
        assertTrue(java.io.File(temp.root, "reader-cache-v2/server-a/7/1-12.epub").exists())
    }

    @Test fun `selected PDF is not replaced by EPUB`() = runTest {
        coEvery { api.serveFile(13) } returns "pdf".toResponseBody()
        assertEquals(13, source().resolvePdf(book, 13).fileId)
        coVerify(exactly = 0) { api.serveFile(11) }
    }

    @Test fun `missing file and wrong engine fail without fetching fallback`() = runTest {
        val source = source()
        assertTrue(runCatching { source.resolve(book, 99) }.isFailure)
        assertTrue(runCatching { source.resolve(book, 13) }.isFailure)
        assertTrue(runCatching { source.resolvePdf(book, 12) }.isFailure)
        coVerify(exactly = 0) { api.serveFile(any()) }
    }
}
