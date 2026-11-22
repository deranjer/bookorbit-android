package com.bookorbit.feature.sessions

import com.bookorbit.core.auth.SecureStorage
import com.bookorbit.core.auth.SessionManager
import com.bookorbit.core.db.PendingReadingSessionDao
import com.bookorbit.core.db.PendingReadingSessionEntity
import com.bookorbit.core.model.AuthUser
import com.bookorbit.core.network.ApiService
import com.bookorbit.core.sync.SyncScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ReadingSessionOwnershipTest {
    private class Dao : PendingReadingSessionDao {
        val rows = linkedMapOf<String, PendingReadingSessionEntity>()
        override suspend fun upsert(entity: PendingReadingSessionEntity) { rows[entity.sessionId] = entity }
        override suspend fun all() = rows.values.toList()
        override suspend fun deleteIfUnchanged(sessionId: String, endedAt: Long) {
            if (rows[sessionId]?.endedAt == endedAt) rows.remove(sessionId)
        }
    }
    private val first = AuthUser(1, "synthetic-first")
    private val second = AuthUser(2, "synthetic-second")
    private fun session() = SessionManager(mockk<SecureStorage>(relaxed = true), Json).apply {
        setServerUrl("https://synthetic.test"); signIn("first-token", first)
    }
    private fun api() = mockk<ApiService>(relaxed = true).also {
        coEvery { it.saveReadingSession(any(), any(), any()) } throws IOException("synthetic offline")
    }

    @Test fun `closing after account switch preserves the owner captured at activity start`() = runTest {
        val dao = Dao(); val session = session(); val api = api(); var now = 0L
        val tracker = ReadingSessionTracker(dao, api, mockk<SyncScheduler>(relaxed = true), session, this) { now }
        tracker.beginReading(9, 0.0)
        now = 30_000
        session.signIn("second-token", second)
        tracker.end()
        runCurrent()
        assertEquals(first.id, dao.rows.values.single().userId)
        coVerify(exactly = 0) { api.saveReadingSession(any(), any(), any()) }
    }

    @Test fun `closing after logout must not turn an owned snapshot into an unowned legacy row`() = runTest {
        val dao = Dao(); val session = session(); val api = api(); var now = 0L
        val tracker = ReadingSessionTracker(dao, api, mockk<SyncScheduler>(relaxed = true), session, this) { now }
        tracker.beginReading(9, 0.0)
        now = 30_000
        session.signOut()
        tracker.end()
        runCurrent()
        assertEquals(first.id, dao.rows.values.single().userId)
        coVerify(exactly = 0) { api.saveReadingSession(any(), any(), any()) }
    }

    @Test fun `identical file IDs in different accounts cannot share one open session`() = runTest {
        val dao = Dao(); val session = session(); val api = api(); var now = 0L
        val tracker = ReadingSessionTracker(dao, api, mockk<SyncScheduler>(relaxed = true), session, this) { now }
        tracker.beginReading(9, 0.0)
        now = 30_000
        session.signIn("second-token", second)
        tracker.beginReading(9, 10.0)
        now = 60_000
        tracker.end()
        runCurrent()
        assertEquals(setOf(first.id, second.id), dao.rows.values.map { it.userId }.toSet())
        assertEquals(listOf(30, 30), dao.rows.values.map { it.durationSeconds })
    }
}
