package com.bookorbit.feature.sessions

import com.bookorbit.core.auth.SessionManager
import com.bookorbit.core.db.PendingReadingSessionDao
import com.bookorbit.core.db.PendingReadingSessionEntity
import com.bookorbit.core.model.AuthUser
import com.bookorbit.core.network.ApiService
import com.bookorbit.core.sync.SyncScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/** In-memory stand-in for the Room DAO. */
private class FakeSessionDao : PendingReadingSessionDao {
    val rows = mutableMapOf<String, PendingReadingSessionEntity>()

    override suspend fun upsert(entity: PendingReadingSessionEntity) {
        rows[entity.sessionId] = entity
    }

    override suspend fun all() = rows.values.toList()

    override suspend fun deleteIfUnchanged(sessionId: String, endedAt: Long) {
        if (rows[sessionId]?.endedAt == endedAt) rows.remove(sessionId)
    }
}

class ReadingSessionTrackerTest {
    private lateinit var api: ApiService
    private lateinit var dao: FakeSessionDao
    private lateinit var tracker: ReadingSessionTracker

    private val me = AuthUser(id = 1, username = "me")
    private val someoneElse = AuthUser(id = 2, username = "someone-else")

    /** Who [SessionManager.currentUser] reports; null means signed out. */
    private var signedIn: AuthUser? = me

    @Before
    fun setUp() {
        api = mockk(relaxed = true)
        dao = FakeSessionDao()
        val session = mockk<SessionManager>()
        every { session.currentUser } answers { signedIn }
        tracker = ReadingSessionTracker(dao, api, mockk<SyncScheduler>(relaxed = true), session)
    }

    private suspend fun queue(id: String, userId: Int?) = dao.upsert(
        PendingReadingSessionEntity(
            sessionId = id,
            fileId = 9,
            sessionType = "READ",
            startedAt = 0,
            endedAt = 60_000,
            durationSeconds = 60,
            progressDelta = null,
            endProgress = null,
            userId = userId,
        ),
    )

    @Test
    fun `flush keeps a session queued when the session has expired`() = runTest {
        queue("s1", me.id)
        coEvery { api.saveReadingSession(any(), any()) } throws HttpException(Response.error<Any>(401, "".toResponseBody()))
        assertTrue(tracker.flushPending())
        assertEquals(1, dao.rows.size)
    }

    @Test
    fun `flush still drops a session the server permanently rejects`() = runTest {
        queue("s1", me.id)
        coEvery { api.saveReadingSession(any(), any()) } throws HttpException(Response.error<Any>(404, "".toResponseBody()))
        assertFalse(tracker.flushPending())
        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun `a session waits for the account that recorded it`() = runTest {
        queue("s1", me.id)
        signedIn = someoneElse
        assertFalse(tracker.flushPending())
        coVerify(exactly = 0) { api.saveReadingSession(any(), any()) }

        signedIn = null
        assertFalse(tracker.flushPending())
        coVerify(exactly = 0) { api.saveReadingSession(any(), any()) }

        signedIn = me
        assertFalse(tracker.flushPending())
        coVerify(exactly = 1) { api.saveReadingSession(9, any()) }
        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun `a session queued before owners were recorded goes out with whoever is signed in`() = runTest {
        queue("s1", null)
        signedIn = someoneElse
        assertFalse(tracker.flushPending())
        coVerify(exactly = 1) { api.saveReadingSession(9, any()) }
    }
}
