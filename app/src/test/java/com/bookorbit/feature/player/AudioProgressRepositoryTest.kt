package com.bookorbit.feature.player

import android.util.Log
import com.bookorbit.core.db.AudioProgressDao
import com.bookorbit.core.db.AudioProgressEntity
import com.bookorbit.core.model.AudioProgress
import com.bookorbit.core.model.AudiobookPlaybackState
import com.bookorbit.core.model.PutAudiobookPlaybackState
import com.bookorbit.core.network.ApiService
import com.bookorbit.core.network.AudiobookAssetResolver
import com.bookorbit.core.network.AudiobookAssetResolver.Sources
import com.bookorbit.core.sync.SyncScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class AudioProgressRepositoryTest {

    private lateinit var dao: AudioProgressDao
    private lateinit var api: ApiService
    private lateinit var assets: AudiobookAssetResolver
    private lateinit var syncScheduler: SyncScheduler
    private lateinit var repo: AudioProgressRepository

    private val manifestRevision = "a".repeat(64)
    private val v3Sources = Sources.Assets(
        assetIds = mapOf(10 to "aud_ten", 11 to "aud_eleven"),
        manifestRevision = manifestRevision,
        assetDurationsMs = mapOf("aud_ten" to 60_000L, "aud_eleven" to null),
    )

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>()) } returns 0
        dao = mockk(relaxed = true)
        api = mockk()
        assets = mockk()
        syncScheduler = mockk(relaxed = true)
        coEvery { assets.forBook(any(), any()) } returns Sources.Legacy
        repo = AudioProgressRepository(dao, api, assets, Json { ignoreUnknownKeys = true }, syncScheduler)
    }

    @After
    fun tearDown() = unmockkStatic(Log::class)

    private fun httpError(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody()))

    private fun state(assetId: String = "aud_ten", positionMs: Long = 1_000, capturedAt: String, revision: Int) =
        AudiobookPlaybackState(assetId, positionMs, 5.0, capturedAt, revision)

    private fun stateBody(json: String) = json.toResponseBody()

    // --- Legacy (pre-3.0) servers ---

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

    // --- 3.0+ servers (playback-state) ---

    @Test
    fun `report PUTs playback-state with the asset, clamped position and the returned revision next time`() = runTest {
        coEvery { assets.forBook(1, any()) } returns v3Sources
        val bodies = mutableListOf<PutAudiobookPlaybackState>()
        coEvery { api.putPlaybackState(1, capture(bodies)) } returns
            state(capturedAt = "2026-01-01T00:00:00Z", revision = 7)

        repo.report(bookId = 1, currentFileId = 10, positionSeconds = 90.0, percentage = 5.0)
        repo.report(bookId = 1, currentFileId = 10, positionSeconds = 30.0, percentage = 5.0)

        assertEquals("aud_ten", bodies[0].assetId)
        assertEquals(60_000L, bodies[0].positionMs) // clamped to the asset's duration
        assertEquals(0, bodies[0].baseRevision)
        assertEquals(manifestRevision, bodies[0].manifestRevision)
        assertEquals(7, bodies[1].baseRevision)
        coVerify(exactly = 2) { dao.markSynced(1) }
        coVerify(exactly = 0) { api.saveAudioProgress(any(), any()) }
    }

    @Test
    fun `revision conflict with a newer server write drops the local position`() = runTest {
        coEvery { assets.forBook(1, any()) } returns v3Sources
        coEvery { api.putPlaybackState(1, any()) } throws httpError(409)
        coEvery { api.getPlaybackState(1) } returns
            stateBody("""{"assetId":"aud_ten","positionMs":5000,"capturedAt":"2099-01-01T00:00:00Z","revision":4}""")

        repo.report(bookId = 1, currentFileId = 10, positionSeconds = 30.0, percentage = 5.0)

        coVerify(exactly = 1) { api.putPlaybackState(1, any()) }
        coVerify { dao.markSynced(1) }
    }

    @Test
    fun `revision conflict with an older server write retries on the server's revision`() = runTest {
        coEvery { assets.forBook(1, any()) } returns v3Sources
        val bodies = mutableListOf<PutAudiobookPlaybackState>()
        coEvery { api.putPlaybackState(1, capture(bodies)) } throws httpError(409) andThen
            state(capturedAt = "2026-01-01T00:00:00Z", revision = 5)
        coEvery { api.getPlaybackState(1) } returns
            stateBody("""{"assetId":"aud_ten","positionMs":5000,"capturedAt":"2000-01-01T00:00:00Z","revision":4}""")

        repo.report(bookId = 1, currentFileId = 10, positionSeconds = 30.0, percentage = 5.0)

        assertEquals(listOf(0, 4), bodies.map { it.baseRevision })
        coVerify { dao.markSynced(1) }
    }

    @Test
    fun `changed manifest re-resolves assets and retries`() = runTest {
        val refreshed = v3Sources.copy(assetIds = mapOf(10 to "aud_new"), manifestRevision = "b".repeat(64))
        coEvery { assets.forBook(1, false) } returns v3Sources
        coEvery { assets.forBook(1, true) } returns refreshed
        val bodies = mutableListOf<PutAudiobookPlaybackState>()
        coEvery { api.putPlaybackState(1, capture(bodies)) } throws httpError(412) andThen
            state(assetId = "aud_new", capturedAt = "2026-01-01T00:00:00Z", revision = 1)

        repo.report(bookId = 1, currentFileId = 10, positionSeconds = 30.0, percentage = 5.0)

        assertEquals("aud_new", bodies[1].assetId)
        assertEquals("b".repeat(64), bodies[1].manifestRevision)
        coVerify { dao.markSynced(1) }
    }

    @Test
    fun `transient server errors keep the position dirty`() = runTest {
        coEvery { assets.forBook(1, any()) } returns v3Sources
        coEvery { api.putPlaybackState(1, any()) } throws httpError(503)

        repo.report(bookId = 1, currentFileId = 10, positionSeconds = 30.0, percentage = 5.0)

        coVerify(exactly = 0) { dao.markSynced(any()) }
        verify { syncScheduler.schedule() }
    }

    @Test
    fun `resolveResume maps the server asset back to a file and prefers the newer server write`() = runTest {
        coEvery { assets.forBook(1, any()) } returns v3Sources
        coEvery { dao.dirtyEntries() } returns emptyList()
        coEvery { dao.get(1) } returns AudioProgressEntity(1, 10, 30.0, 5.0, 100L, dirty = false)
        coEvery { api.getPlaybackState(1) } returns
            stateBody("""{"assetId":"aud_eleven","positionMs":12500,"percentage":40,"capturedAt":"2026-01-01T00:00:00Z","revision":3}""")

        val resume = repo.resolveResume(1)!!

        assertEquals(11, resume.currentFileId)
        assertEquals(12.5, resume.positionSeconds, 0.0)
        assertEquals(Instant.parse("2026-01-01T00:00:00Z").toString(), resume.updatedAt)
    }

    @Test
    fun `resolveResume treats an empty playback-state body as no server position`() = runTest {
        coEvery { assets.forBook(1, any()) } returns v3Sources
        coEvery { dao.dirtyEntries() } returns emptyList()
        coEvery { dao.get(1) } returns null
        coEvery { api.getPlaybackState(1) } returns stateBody("")

        assertNull(repo.resolveResume(1))
    }

    // --- resumePoint: resume position plus when it was captured (drives which book reopens on launch) ---

    @Test
    fun `resumePoint uses the server write time when the server is newer`() = runTest {
        coEvery { assets.forBook(1, any()) } returns v3Sources
        coEvery { dao.dirtyEntries() } returns emptyList()
        coEvery { dao.get(1) } returns AudioProgressEntity(1, 10, 30.0, 5.0, 100L, dirty = false)
        coEvery { api.getPlaybackState(1) } returns
            stateBody("""{"assetId":"aud_eleven","positionMs":12500,"percentage":40,"capturedAt":"2026-01-01T00:00:00Z","revision":3}""")

        val point = repo.resumePoint(1)!!

        assertEquals(Instant.parse("2026-01-01T00:00:00Z").toEpochMilli(), point.lastActivityMillis)
        assertEquals(40.0, point.progress.percentage, 0.0)
        assertEquals(11, point.progress.currentFileId)
    }

    @Test
    fun `resumePoint uses the local time when the local row is newer`() = runTest {
        val localAt = Instant.parse("2026-02-01T00:00:00Z").toEpochMilli()
        coEvery { assets.forBook(1, any()) } returns v3Sources
        coEvery { dao.dirtyEntries() } returns emptyList()
        coEvery { dao.get(1) } returns AudioProgressEntity(1, 10, 30.0, 5.0, localAt, dirty = false)
        coEvery { api.getPlaybackState(1) } returns
            stateBody("""{"assetId":"aud_eleven","positionMs":12500,"percentage":40,"capturedAt":"2026-01-01T00:00:00Z","revision":3}""")

        val point = repo.resumePoint(1)!!

        assertEquals(localAt, point.lastActivityMillis)
        assertEquals(5.0, point.progress.percentage, 0.0)
        assertEquals(10, point.progress.currentFileId)
    }

    @Test
    fun `resumePoint ranks a server position without a timestamp as oldest`() = runTest {
        coEvery { dao.dirtyEntries() } returns emptyList()
        coEvery { dao.get(1) } returns null
        coEvery { api.getAudioProgress(1) } returns AudioProgress(currentFileId = 10, positionSeconds = 30.0, percentage = 12.0, updatedAt = null)

        val point = repo.resumePoint(1)!!

        assertEquals(0L, point.lastActivityMillis)
        assertEquals(12.0, point.progress.percentage, 0.0)
    }

    @Test
    fun `localResumePoint reads only the local row`() = runTest {
        coEvery { dao.get(1) } returns AudioProgressEntity(1, 10, 30.0, 5.0, 777L, dirty = false)

        val point = repo.localResumePoint(1)!!

        assertEquals(777L, point.lastActivityMillis)
        assertEquals(30.0, point.progress.positionSeconds, 0.0)
        coVerify(exactly = 0) { api.getPlaybackState(any()) }
        coVerify(exactly = 0) { api.getAudioProgress(any()) }
    }
}
