package com.bookorbit.feature.downloads

import android.content.Context
import com.bookorbit.core.auth.SessionManager
import com.bookorbit.core.db.DownloadDao
import com.bookorbit.core.db.DownloadEntity
import com.bookorbit.core.model.BookDetail
import com.bookorbit.core.model.BookFileRef
import com.bookorbit.core.settings.DownloadLocationStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsRepositoryTest {
    private val dao = mockk<DownloadDao>(relaxed = true)
    private val scheduler = mockk<DownloadWorkScheduler>(relaxed = true)
    private val repo = DownloadsRepository(mockk<Context>(), dao, Json, scheduler, mockk<DownloadLocationStore>(), mockk<SessionManager>())
    private val book = BookDetail(id = 1, libraryId = 1, libraryName = "Test", status = "present", addedAt = "2026-11-22",
        files = listOf(BookFileRef(11, "epub", "primary"), BookFileRef(12, "mp3", "supplement")))
    private var row: DownloadEntity? = null

    private fun database() {
        coEvery { dao.get(1) } coAnswers { row }
        coEvery { dao.insertPlan(any()) } coAnswers { if (row == null) { row = firstArg(); 1L } else -1L }
        coEvery { dao.replacePlan(any(), any()) } coAnswers {
            if (row == firstArg<DownloadEntity>()) { row = secondArg(); true } else false
        }
        coEvery { dao.failPlan(any(), any(), any(), any()) } coAnswers {
            if (row?.planId == secondArg<String>()) {
                row = row?.copy(status = thirdArg(), lastError = arg(3)); 1
            } else 0
        }
    }

    @Test fun `explicit add schedules a new generation and does not hide existing bytes`() = runTest {
        database()
        repo.startDownload(book, setOf(11))
        val first = row!!
        row = first.copy(status = "COMPLETE", filesJson = Json.encodeToString(listOf(DownloadedFile(11, "/existing/book.epub"))))
        repo.startDownload(book, setOf(12))
        assertNotEquals(first.planId, row!!.planId)
        assertEquals(first.storageId, row!!.storageId)
        assertEquals(mapOf(11 to "/existing/book.epub"), repo.localFiles(1))
        coVerify(exactly = 2) { scheduler.enqueue(1, any()) }
        row = row!!.copy(status = "FAILED")
        assertEquals(mapOf(11 to "/existing/book.epub"), repo.localFiles(1))
    }

    @Test fun `repeat taps never replace running plan or schedule unwanted files`() = runTest {
        database()
        repo.startDownload(book, setOf(11))
        val first = row
        repo.startDownload(book, setOf(12))
        assertEquals(first, row)
        coVerify(exactly = 1) { scheduler.enqueue(1, any()) }
    }

    @Test fun `retry keeps journal and storage but changes worker generation`() = runTest {
        database()
        repo.startDownload(book, setOf(11))
        val failed = row!!.copy(status = "FAILED", filesJson = "[]")
        row = failed
        repo.retryDownload(1)
        assertEquals(failed.storageId, row!!.storageId)
        assertEquals(failed.selectedFileIdsJson, row!!.selectedFileIdsJson)
        assertEquals(failed.filesJson, row!!.filesJson)
        assertNotEquals(failed.planId, row!!.planId)
        assertEquals("DOWNLOADING", row!!.status)
    }

    @Test fun `lost database compare-and-swap never schedules obsolete work`() = runTest {
        database()
        repo.startDownload(book, setOf(11))
        row = row!!.copy(status = "COMPLETE")
        coEvery { dao.replacePlan(any(), any()) } returns false
        repo.startDownload(book, setOf(12))
        coVerify(exactly = 1) { scheduler.enqueue(1, any()) }
    }

    @Test fun `scheduling failure is retryable without hiding completed files`() = runTest {
        database()
        repo.startDownload(book, setOf(11))
        row = row!!.copy(status = "COMPLETE", filesJson = Json.encodeToString(listOf(DownloadedFile(11, "/existing/book.epub"))))
        coEvery { scheduler.enqueue(1, any()) } throws IllegalStateException("scheduler unavailable")
        assertTrue(runCatching { repo.startDownload(book, setOf(12)) }.isFailure)
        assertEquals("FAILED", row!!.status)
        assertEquals(mapOf(11 to "/existing/book.epub"), repo.localFiles(1))
    }

    @Test fun `leaving screen cannot cancel scheduling after a plan was reserved`() = runTest {
        database()
        val scheduled = CompletableDeferred<Unit>()
        val reserved = CompletableDeferred<Unit>()
        coEvery { scheduler.enqueue(1, any()) } coAnswers { reserved.complete(Unit); scheduled.await(); Unit }
        val request = launch { repo.startDownload(book, setOf(11)) }
        reserved.await()
        assertNotNull(row)
        request.cancel()
        runCurrent()
        assertFalse(request.isCompleted)
        scheduled.complete(Unit)
        request.join()
        coVerify(exactly = 1) { scheduler.enqueue(1, any()) }
        assertEquals("DOWNLOADING", row!!.status)
    }
}
