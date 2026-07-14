package com.bookorbit.feature.player

import com.bookorbit.core.db.AudioProgressDao
import com.bookorbit.core.db.AudioProgressEntity
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
class AudioProgressRepositoryTest {

    private lateinit var dao: AudioProgressDao
    private lateinit var api: ApiService
    private lateinit var syncScheduler: SyncScheduler
    private lateinit var repo: AudioProgressRepository

    @Before
    fun setUp() {
        dao = mockk(relaxed = true)
        api = mockk()
        syncScheduler = mockk(relaxed = true)
        repo = AudioProgressRepository(dao, api, syncScheduler)
    }

    @Test
    fun `report schedules a background retry when the network call fails`() = runTest {
        coEvery { api.saveAudioProgress(any(), any()) } throws RuntimeException("offline")

        repo.report(bookId = 1, currentFileId = 10, positionSeconds = 30.0, percentage = 5.0)

        coVerify { dao.upsert(match { it.bookId == 1 && it.currentFileId == 10 && it.positionSeconds == 30.0 && it.dirty }) }
        coVerify(exactly = 0) { dao.markSynced(any()) }
        verify { syncScheduler.schedule() }
    }

    @Test
    fun `report does not schedule a retry when the network call succeeds`() = runTest {
        coEvery { api.saveAudioProgress(any(), any()) } returns Unit

        repo.report(bookId = 1, currentFileId = 10, positionSeconds = 30.0, percentage = 5.0)

        coVerify { dao.markSynced(1) }
        verify(exactly = 0) { syncScheduler.schedule() }
    }

    @Test
    fun `flushPending returns false once every dirty entry syncs`() = runTest {
        coEvery { dao.dirtyEntries() } returnsMany listOf(
            listOf(AudioProgressEntity(1, 10, 30.0, 5.0, 100L, dirty = true)),
            emptyList(),
        )
        coEvery { api.saveAudioProgress(any(), any()) } returns Unit

        assertFalse(repo.flushPending())
        coVerify { dao.markSynced(1) }
    }

    @Test
    fun `flushPending returns true when an entry stays dirty after a failed retry`() = runTest {
        val stillDirty = AudioProgressEntity(1, 10, 30.0, 5.0, 100L, dirty = true)
        coEvery { dao.dirtyEntries() } returns listOf(stillDirty)
        coEvery { api.saveAudioProgress(any(), any()) } throws RuntimeException("offline")

        assertTrue(repo.flushPending())
        coVerify(exactly = 0) { dao.markSynced(any()) }
    }
}
