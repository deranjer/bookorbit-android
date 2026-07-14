package com.bookorbit.feature.reader

import com.bookorbit.core.db.ReaderProgressDao
import com.bookorbit.core.db.ReaderProgressEntity
import com.bookorbit.core.model.SaveFileProgress
import com.bookorbit.core.network.ApiService
import com.bookorbit.core.sync.SyncScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderProgressRepositoryTest {

    private lateinit var dao: ReaderProgressDao
    private lateinit var api: ApiService
    private lateinit var syncScheduler: SyncScheduler
    private lateinit var repo: ReaderProgressRepository

    @Before
    fun setUp() {
        dao = mockk(relaxed = true)
        api = mockk()
        syncScheduler = mockk(relaxed = true)
        repo = ReaderProgressRepository(dao, api, syncScheduler)
    }

    @Test
    fun `report schedules a background retry when the network call fails`() = runTest {
        coEvery { api.saveFileProgress(any(), any()) } throws RuntimeException("offline")

        repo.report(fileId = 1, cfi = "epubcfi(...)", percentage = 50.0)

        coVerify { dao.upsert(match { it.fileId == 1 && it.cfi == "epubcfi(...)" && it.percentage == 50.0 && it.dirty }) }
        coVerify(exactly = 0) { dao.markSynced(any()) }
        verify { syncScheduler.schedule() }
    }

    @Test
    fun `report does not schedule a retry when the network call succeeds`() = runTest {
        coEvery { api.saveFileProgress(any(), any()) } returns Unit

        repo.report(fileId = 1, cfi = null, percentage = 50.0)

        coVerify { dao.markSynced(1) }
        verify(exactly = 0) { syncScheduler.schedule() }
    }

    @Test
    fun `flushPending returns false once every dirty entry syncs`() = runTest {
        coEvery { dao.dirtyEntries() } returnsMany listOf(
            listOf(ReaderProgressEntity(1, null, 10.0, 100L, dirty = true)),
            emptyList(),
        )
        coEvery { api.saveFileProgress(any(), any()) } returns Unit

        assertFalse(repo.flushPending())
        coVerify { dao.markSynced(1) }
    }

    @Test
    fun `flushPending returns true when an entry stays dirty after a failed retry`() = runTest {
        val stillDirty = ReaderProgressEntity(1, null, 10.0, 100L, dirty = true)
        coEvery { dao.dirtyEntries() } returns listOf(stillDirty)
        coEvery { api.saveFileProgress(any(), any()) } throws RuntimeException("offline")

        assertTrue(repo.flushPending())
        coVerify(exactly = 0) { dao.markSynced(any()) }
    }
}
