package com.bookorbit.feature.reader

import com.bookorbit.core.db.ReaderProgressDao
import com.bookorbit.core.db.ReaderProgressEntity
import com.bookorbit.core.db.AudioProgressDao
import com.bookorbit.core.db.AudioProgressEntity
import com.bookorbit.core.model.SaveFileProgress
import com.bookorbit.core.network.ApiService
import com.bookorbit.core.network.AudiobookAssetResolver
import com.bookorbit.core.sync.SyncScheduler
import com.bookorbit.feature.player.AudioProgressRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressWriteRaceTest {
    @Test fun `old reader acknowledgement cannot clear newer offline position`() = runTest {
        val dao = mockk<ReaderProgressDao>()
        val api = mockk<ApiService>()
        var row: ReaderProgressEntity? = null
        coEvery { dao.upsert(any()) } coAnswers { row = firstArg() }
        coEvery { dao.get(any()) } coAnswers { row }
        coEvery { dao.markSynced(any(), any()) } coAnswers {
            if (row?.writeId == secondArg<String>()) { row = row?.copy(dirty = false); 1 } else 0
        }
        val release = CompletableDeferred<Unit>()
        val calls = mutableListOf<SaveFileProgress>()
        coEvery { api.saveFileProgress(1, any()) } coAnswers {
            calls.add(secondArg())
            if (calls.size == 1) release.await() else error("offline")
        }
        val repo = ReaderProgressRepository(dao, api, mockk(relaxed = true))
        val first = launch { repo.report(1, "old", 10.0) }
        runCurrent()
        val oldId = row!!.writeId
        val second = launch { repo.report(1, "new", 20.0, pageNumber = 7) }
        runCurrent()
        assertNotEquals(oldId, row!!.writeId)
        assertEquals(1, calls.size) // second request cannot overtake the first
        release.complete(Unit)
        first.join(); second.join()
        assertEquals("new", row!!.cfi)
        assertEquals(7, row!!.pageNumber)
        assertTrue(row!!.dirty)
        coVerify { dao.markSynced(1, oldId) }
    }

    @Test fun `PDF page survives background retry`() = runTest {
        val dao = mockk<ReaderProgressDao>(relaxed = true)
        val api = mockk<ApiService>()
        val row = ReaderProgressEntity(1, null, 25.0, 1, true, pageNumber = 7, writeId = "queued")
        coEvery { dao.dirtyEntries() } returnsMany listOf(listOf(row), emptyList())
        coEvery { api.saveFileProgress(1, SaveFileProgress(null, 25.0, 7)) } returns Unit
        val repo = ReaderProgressRepository(dao, api, mockk(relaxed = true))
        assertFalse(repo.flushPending())
        coVerify { dao.markSynced(1, "queued") }
    }

    @Test fun `old audio acknowledgement cannot clear newer offline position`() = runTest {
        val dao = mockk<AudioProgressDao>()
        val api = mockk<ApiService>()
        val assets = mockk<AudiobookAssetResolver>()
        coEvery { assets.forBook(any(), any()) } returns AudiobookAssetResolver.Sources.Legacy
        var row: AudioProgressEntity? = null
        coEvery { dao.upsert(any()) } coAnswers { row = firstArg() }
        coEvery { dao.get(any()) } coAnswers { row }
        coEvery { dao.markSynced(any(), any()) } coAnswers {
            if (row?.writeId == secondArg<String>()) { row = row?.copy(dirty = false); 1 } else 0
        }
        val release = CompletableDeferred<Unit>()
        var calls = 0
        coEvery { api.saveAudioProgress(1, any()) } coAnswers {
            calls++
            if (calls == 1) release.await() else error("offline")
        }
        val repo = AudioProgressRepository(dao, api, assets, Json, mockk(relaxed = true))
        val first = launch { repo.report(1, 11, 10.0, 10.0) }
        runCurrent()
        val oldId = row!!.writeId
        val second = launch { repo.report(1, 12, 20.0, 20.0) }
        runCurrent()
        assertNotEquals(oldId, row!!.writeId)
        assertEquals(1, calls)
        release.complete(Unit)
        first.join(); second.join()
        assertEquals(12, row!!.currentFileId)
        assertTrue(row!!.dirty)
    }
}
