package com.bookorbit.feature.reader

import com.bookorbit.core.db.ReaderProgressDao
import com.bookorbit.core.db.ReaderProgressEntity
import com.bookorbit.core.model.SaveFileProgress
import com.bookorbit.core.network.ApiService
import com.bookorbit.core.sync.SyncScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderWebHandoffTest {
    private val dao = mockk<ReaderProgressDao>(relaxed = true)
    private val api = mockk<ApiService>()
    private val repo = ReaderProgressRepository(dao, api, mockk<SyncScheduler>())

    @Test
    fun `pending offline position is saved before web reading`() = runTest {
        coEvery { dao.get(34) } returns ReaderProgressEntity(34, "epubcfi(test)", 42.0, 100, true)
        coEvery { api.saveFileProgress(34, any()) } returns Unit
        repo.syncBeforeWeb(34)
        coVerify { api.saveFileProgress(34, SaveFileProgress("epubcfi(test)", 42.0)) }
        coVerify { dao.markSynced(34) }
    }

    @Test
    fun `failed handoff preserves dirty position and prevents opening behind it`() = runTest {
        coEvery { dao.get(34) } returns ReaderProgressEntity(34, "epubcfi(test)", 42.0, 100, true)
        coEvery { api.saveFileProgress(34, any()) } throws IllegalStateException("offline")
        assertTrue(runCatching { repo.syncBeforeWeb(34) }.isFailure)
        coVerify(exactly = 0) { dao.markSynced(any()) }
    }

    @Test
    fun `clean local progress does not overwrite newer server progress`() = runTest {
        coEvery { dao.get(34) } returns ReaderProgressEntity(34, "old", 10.0, 100, false)
        repo.syncBeforeWeb(34)
        coVerify(exactly = 0) { api.saveFileProgress(any(), any()) }
    }
}
