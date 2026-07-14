package com.bookorbit.feature.bookdetail

import com.bookorbit.core.db.PendingRatingDao
import com.bookorbit.core.db.PendingRatingEntity
import com.bookorbit.core.db.PendingReadStatusDao
import com.bookorbit.core.db.PendingReadStatusEntity
import com.bookorbit.core.model.BookDetail
import com.bookorbit.core.model.SetRatingRequest
import com.bookorbit.core.model.SetReadStatusRequest
import com.bookorbit.core.model.UserBookStatus
import com.bookorbit.core.network.ApiService
import com.bookorbit.core.sync.SyncScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BookDetailRepositoryTest {

    private lateinit var api: ApiService
    private lateinit var pendingRatingDao: PendingRatingDao
    private lateinit var pendingReadStatusDao: PendingReadStatusDao
    private lateinit var syncScheduler: SyncScheduler
    private lateinit var repo: BookDetailRepository

    private fun book(rating: Double? = null, readStatus: UserBookStatus? = null) = BookDetail(
        id = 1,
        libraryId = 1,
        libraryName = "Main",
        status = "active",
        addedAt = "2024-01-01",
        rating = rating,
        readStatus = readStatus,
    )

    @Before
    fun setUp() {
        api = mockk()
        pendingRatingDao = mockk(relaxed = true)
        pendingReadStatusDao = mockk(relaxed = true)
        syncScheduler = mockk(relaxed = true)
        repo = BookDetailRepository(api, pendingRatingDao, pendingReadStatusDao, syncScheduler)
    }

    @Test
    fun `setRating writes pending row before the network call and clears it on success`() = runTest {
        coEvery { api.setRating(any()) } returns Unit

        repo.setRating(1, 4)

        coVerify { pendingRatingDao.upsert(match { it.bookId == 1 && it.rating == 4 }) }
        coVerify { api.setRating(SetRatingRequest(bookIds = listOf(1), rating = 4)) }
        coVerify { pendingRatingDao.delete(1) }
        verify(exactly = 0) { syncScheduler.schedule() }
    }

    @Test
    fun `setRating keeps the pending row and schedules a retry on failure`() = runTest {
        coEvery { api.setRating(any()) } throws RuntimeException("offline")

        repo.setRating(1, 4)

        coVerify { pendingRatingDao.upsert(any()) }
        coVerify(exactly = 0) { pendingRatingDao.delete(any()) }
        verify { syncScheduler.schedule() }
    }

    @Test
    fun `setReadStatus keeps the pending row and schedules a retry on failure`() = runTest {
        coEvery { api.setReadStatus(any(), any()) } throws RuntimeException("offline")

        repo.setReadStatus(1, "reading")

        coVerify { pendingReadStatusDao.upsert(match { it.bookId == 1 && it.status == "reading" }) }
        coVerify(exactly = 0) { pendingReadStatusDao.delete(any()) }
        verify { syncScheduler.schedule() }
    }

    @Test
    fun `setReadStatus clears the pending row on success`() = runTest {
        coEvery { api.setReadStatus(any(), any()) } returns mockk()

        repo.setReadStatus(1, "read")

        coVerify { pendingReadStatusDao.delete(1) }
        verify(exactly = 0) { syncScheduler.schedule() }
    }

    @Test
    fun `applyPending overlays a queued rating onto the book`() = runTest {
        coEvery { pendingRatingDao.get(1) } returns PendingRatingEntity(1, 5, 100L)
        coEvery { pendingReadStatusDao.get(1) } returns null

        val result = repo.applyPending(book(rating = 2.0))

        assertEquals(5.0, result.rating)
    }

    @Test
    fun `applyPending clears the rating when the queued change is a clear`() = runTest {
        coEvery { pendingRatingDao.get(1) } returns PendingRatingEntity(1, null, 100L)
        coEvery { pendingReadStatusDao.get(1) } returns null

        val result = repo.applyPending(book(rating = 3.0))

        assertNull(result.rating)
    }

    @Test
    fun `applyPending overlays a queued status, synthesizing one if the book has none`() = runTest {
        coEvery { pendingRatingDao.get(1) } returns null
        coEvery { pendingReadStatusDao.get(1) } returns PendingReadStatusEntity(1, "reading", 100L)

        val result = repo.applyPending(book(readStatus = null))

        assertEquals("reading", result.readStatus?.status)
    }

    @Test
    fun `applyPending overlays a queued status onto an existing status, preserving other fields`() = runTest {
        coEvery { pendingRatingDao.get(1) } returns null
        coEvery { pendingReadStatusDao.get(1) } returns PendingReadStatusEntity(1, "read", 100L)
        val existing = UserBookStatus(status = "reading", source = "server", startedAt = "2024-01-01", updatedAt = "2024-01-02")

        val result = repo.applyPending(book(readStatus = existing))

        assertEquals("read", result.readStatus?.status)
        assertEquals("2024-01-01", result.readStatus?.startedAt)
    }

    @Test
    fun `applyPending leaves the book unchanged when nothing is pending`() = runTest {
        coEvery { pendingRatingDao.get(1) } returns null
        coEvery { pendingReadStatusDao.get(1) } returns null

        val original = book(rating = 3.0)
        val result = repo.applyPending(original)

        assertEquals(original, result)
    }

    @Test
    fun `flushPendingRatings retries queued entries and reports whether any remain`() = runTest {
        coEvery { pendingRatingDao.all() } returnsMany listOf(
            listOf(PendingRatingEntity(1, 4, 100L), PendingRatingEntity(2, 5, 100L)),
            listOf(PendingRatingEntity(2, 5, 100L)),
        )
        coEvery { api.setRating(SetRatingRequest(bookIds = listOf(1), rating = 4)) } returns Unit
        coEvery { api.setRating(SetRatingRequest(bookIds = listOf(2), rating = 5)) } throws RuntimeException("offline")

        val stillPending = repo.flushPendingRatings()

        coVerify { pendingRatingDao.delete(1) }
        coVerify(exactly = 0) { pendingRatingDao.delete(2) }
        assertEquals(true, stillPending)
    }

    @Test
    fun `flushPendingRatings reports false once the queue is empty`() = runTest {
        coEvery { pendingRatingDao.all() } returns emptyList()

        val stillPending = repo.flushPendingRatings()

        assertEquals(false, stillPending)
    }
}
