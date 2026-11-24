package com.bookorbit.feature.sessions

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.bookorbit.core.auth.SessionManager
import com.bookorbit.core.auth.SessionOwner
import com.bookorbit.core.db.PendingReadingSessionDao
import com.bookorbit.core.db.PendingReadingSessionEntity
import com.bookorbit.core.model.SaveReadingSession
import com.bookorbit.core.network.ApiService
import com.bookorbit.core.sync.SyncScheduler
import com.bookorbit.core.sync.isPermanentRejection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reports reading and listening time to the server so mobile activity counts toward the web's
 * streaks, goals and statistics (`POST /books/files/{fileId}/sessions`, source `android`).
 *
 * One session is open at a time. Closed sessions and periodic checkpoints go through a Room queue
 * ([PendingReadingSessionDao]) so nothing is lost offline or if the process is killed; the server
 * treats a repeated session id as an update to the earlier partial row, so checkpointing is safe.
 *
 * Reading time counts only while the app is in the foreground; listening time only while playing.
 * A queued session belongs to the account that was signed in and waits while that account is signed out.
 */
@Singleton
class ReadingSessionTracker internal constructor(
    private val dao: PendingReadingSessionDao,
    private val api: ApiService,
    private val syncScheduler: SyncScheduler,
    private val session: SessionManager,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
) {
    @Inject constructor(dao: PendingReadingSessionDao, api: ApiService, syncScheduler: SyncScheduler, session: SessionManager) :
        this(dao, api, syncScheduler, session, CoroutineScope(SupervisorJob() + Dispatchers.IO), System::currentTimeMillis)
    private val recorder = ReadingSessionRecorder(maxGapMs = MAX_GAP_MS)
    private val activities = ReadingActivityArbiter()
    private var activeOwner: SessionOwner? = null
    private var lastCheckpointMs = 0L
    private var foreground = true

    init {
        // ProcessLifecycleOwner must be touched on the main thread.
        Handler(Looper.getMainLooper()).post {
            ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = onForeground(true)
                override fun onStop(owner: LifecycleOwner) = onForeground(false)
            })
        }
    }

    // --- reading (ebook / PDF) ---

    @Synchronized
    fun beginReading(fileId: Int, percent: Double?, expectedOwner: SessionOwner? = liveOwner()): ReadingHandle? {
        if (expectedOwner == null || expectedOwner != liveOwner()) { applyActivity(); return null }
        val handle = activities.openReader(expectedOwner, fileId, percent)
        applyActivity()
        return handle
    }

    @Synchronized
    fun onReadingProgress(handle: ReadingHandle, percent: Double) {
        if (handle.owner == liveOwner()) activities.readerProgress(handle, percent)
        applyActivity()
    }

    @Synchronized
    fun endReading(handle: ReadingHandle) { activities.closeReader(handle); applyActivity() }

    // --- listening (audiobook) ---

    /** Called while playing (every few seconds). Opens a session on first call for a book. */
    @Synchronized
    fun onListening(bookId: Int, firstFileId: Int, percent: Double, expectedOwner: SessionOwner? = liveOwner()) {
        if (expectedOwner != null && expectedOwner == liveOwner() && percent.isFinite())
            activities.listening(expectedOwner, bookId, firstFileId, percent)
        applyActivity()
    }

    @Synchronized
    fun setListeningActive(active: Boolean, expectedOwner: SessionOwner? = liveOwner()) {
        if (expectedOwner != null && expectedOwner == liveOwner()) activities.audioPlaying(expectedOwner, active)
        applyActivity()
        if (!active) checkpointLocked()
    }

    @Synchronized
    fun endListening(expectedOwner: SessionOwner? = liveOwner()) {
        expectedOwner?.let(activities::closeAudio)
        applyActivity()
    }

    @Synchronized
    fun beginNarration(fileId: Int, owner: SessionOwner): NarrationHandle? {
        if (owner != liveOwner()) { applyActivity(); return null }
        val handle = activities.openNarration(owner, fileId)
        applyActivity()
        return handle
    }

    @Synchronized
    fun setNarrationActive(handle: NarrationHandle, active: Boolean) {
        if (handle.owner == liveOwner()) activities.narrationPlaying(handle, active)
        applyActivity()
        if (!active) checkpointLocked()
    }

    @Synchronized
    fun endNarration(handle: NarrationHandle) { activities.closeNarration(handle); applyActivity() }

    // --- shared ---

    /** Close the open session (if any) and queue it. */
    @Synchronized
    fun end() { activities.clear(); endLocked() }

    /**
     * Send the signed-in account's queued sessions. Returns true if any of them remain (so the sync
     * worker retries); those of an account that isn't signed in wait for its next sign-in.
     */
    suspend fun flushPending(): Boolean {
        val owner = liveOwner() ?: return false
        val user = owner.userId
        for (entity in mine(user)) {
            send(entity, owner.serverUrl)
        }
        return session.owns(owner) && mine(user).isNotEmpty()
    }

    private fun liveOwner(): SessionOwner? = session.owner()?.takeUnless { session.dataScopeChanged() }

    /** Rows queued before owners were recorded have none and go out with whoever is signed in. */
    private suspend fun mine(user: Int) = dao.all().filter { it.userId == null || it.userId == user }

    @Synchronized
    internal fun onForeground(foreground: Boolean) {
        this.foreground = foreground
        applyActivity()
        if (!foreground) checkpointLocked()
    }

    private fun applyActivity() {
        val choice = activities.choose(liveOwner(), foreground) ?: run { endLocked(); return }
        val now = now()
        val expiredPause = choice.type == SessionType.LISTEN && choice.active && recorder.key == choice.key &&
            recorder.inactiveForMs(now) > LISTEN_IDLE_CLOSE_MS
        if (recorder.key != choice.key || activeOwner != choice.owner || expiredPause) {
            endLocked()
            activeOwner = choice.owner
            recorder.begin(choice.key, choice.fileId, choice.type, choice.percent, now)
            lastCheckpointMs = now
        }
        recorder.setActive(choice.active, now)
        choice.percent?.takeIf { it.isFinite() }?.let { recorder.progress(it, now) }
        checkpointIfDue()
    }

    private fun endLocked() {
        val snap = recorder.end(now()) ?: return
        val owner = activeOwner
        activeOwner = null
        if (owner != null) persistAndSend(snap, owner)
    }

    private fun checkpointIfDue() {
        if (now() - lastCheckpointMs >= CHECKPOINT_INTERVAL_MS) checkpointLocked()
    }

    private fun checkpointLocked() {
        lastCheckpointMs = now()
        val snap = recorder.snapshot(now()) ?: return
        activeOwner?.let { persistAndSend(snap, it) }
    }

    private fun persistAndSend(snap: SessionSnapshot, owner: SessionOwner) {
        if (snap.durationSeconds < MIN_SESSION_SECONDS) return
        val entity = PendingReadingSessionEntity(
            sessionId = snap.sessionId,
            fileId = snap.fileId,
            sessionType = snap.type.wire,
            startedAt = snap.startedAtMs,
            endedAt = snap.endedAtMs,
            durationSeconds = snap.durationSeconds,
            progressDelta = snap.progressDelta,
            endProgress = snap.endProgress,
            userId = owner.userId,
        )
        scope.launch {
            dao.upsert(entity)
            send(entity, owner.serverUrl)
        }
    }

    private suspend fun send(e: PendingReadingSessionEntity, expectedServer: String) {
        val owner = liveOwner() ?: return
        if (owner.serverUrl != expectedServer || e.userId != null && e.userId != owner.userId) return
        val body = SaveReadingSession(
            sessionId = e.sessionId,
            startedAt = Instant.ofEpochMilli(e.startedAt).toString(),
            endedAt = Instant.ofEpochMilli(e.endedAt).toString(),
            durationSeconds = e.durationSeconds,
            progressDelta = e.progressDelta,
            endProgress = e.endProgress,
            sessionType = e.sessionType,
            source = SOURCE,
        )
        try {
            api.saveReadingSession(e.fileId, body, owner)
            Log.d(TAG, "sent ${e.sessionType} session ${e.sessionId} file=${e.fileId} ${e.durationSeconds}s end=${e.endProgress}")
            dao.deleteIfUnchanged(e.sessionId, e.endedAt)
        } catch (t: HttpException) {
            // A permanent rejection won't succeed on retry (e.g. the file was removed).
            if (isPermanentRejection(t.code())) {
                Log.w(TAG, "dropping session ${e.sessionId}: HTTP ${t.code()}")
                dao.deleteIfUnchanged(e.sessionId, e.endedAt)
            } else {
                syncScheduler.schedule()
            }
        } catch (t: Exception) {
            syncScheduler.schedule()
        }
    }

    private fun now() = clock()

    companion object {
        private const val TAG = "ReadingSessions"
        private const val SOURCE = "android"

        /** The server drops sessions shorter than this, so don't bother queuing them. */
        const val MIN_SESSION_SECONDS = 10

        /** Longest stretch between two activity events that still counts as reading. */
        private const val MAX_GAP_MS = 120_000L
        private const val CHECKPOINT_INTERVAL_MS = 60_000L

        /** Paused for this long, and the next play starts a new session. */
        private const val LISTEN_IDLE_CLOSE_MS = 10 * 60_000L
    }
}
