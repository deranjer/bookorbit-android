package com.bookorbit.feature.sessions

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.bookorbit.core.db.PendingReadingSessionDao
import com.bookorbit.core.db.PendingReadingSessionEntity
import com.bookorbit.core.model.SaveReadingSession
import com.bookorbit.core.network.ApiService
import com.bookorbit.core.sync.SyncScheduler
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
 */
@Singleton
class ReadingSessionTracker @Inject constructor(
    private val dao: PendingReadingSessionDao,
    private val api: ApiService,
    private val syncScheduler: SyncScheduler,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val recorder = ReadingSessionRecorder(maxGapMs = MAX_GAP_MS)
    private var lastCheckpointMs = 0L

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
    fun beginReading(fileId: Int, percent: Double?) {
        val key = "read:$fileId"
        if (recorder.key == key) return
        endLocked()
        recorder.begin(key, fileId, SessionType.READ, percent, now())
        lastCheckpointMs = now()
    }

    @Synchronized
    fun onReadingProgress(percent: Double) {
        if (recorder.key?.startsWith("read:") != true) return
        recorder.progress(percent, now())
        checkpointIfDue()
    }

    // --- listening (audiobook) ---

    /** Called while playing (every few seconds). Opens a session on first call for a book. */
    @Synchronized
    fun onListening(bookId: Int, firstFileId: Int, percent: Double) {
        val key = "listen:$bookId"
        val now = now()
        if (recorder.key == key && recorder.inactiveForMs(now) > LISTEN_IDLE_CLOSE_MS) endLocked()
        if (recorder.key != key) {
            endLocked()
            recorder.begin(key, firstFileId, SessionType.LISTEN, percent, now)
            lastCheckpointMs = now
        }
        recorder.setActive(true, now)
        recorder.progress(percent, now)
        checkpointIfDue()
    }

    @Synchronized
    fun setListeningActive(active: Boolean) {
        if (recorder.key?.startsWith("listen:") != true) return
        val now = now()
        if (active && recorder.inactiveForMs(now) > LISTEN_IDLE_CLOSE_MS) {
            // Paused a long while: close it; the next onListening call opens a fresh session.
            endLocked()
            return
        }
        recorder.setActive(active, now)
        if (!active) checkpointLocked()
    }

    // --- shared ---

    /** Close the open session (if any) and queue it. */
    @Synchronized
    fun end() = endLocked()

    /** Send every queued session. Returns true if any remain (so the sync worker retries). */
    suspend fun flushPending(): Boolean {
        for (entity in dao.all()) {
            send(entity)
        }
        return dao.all().isNotEmpty()
    }

    @Synchronized
    private fun onForeground(foreground: Boolean) {
        // Only reading follows the app's visibility; audio keeps playing in the background.
        if (recorder.key?.startsWith("read:") != true) return
        recorder.setActive(foreground, now())
        if (!foreground) checkpointLocked()
    }

    private fun endLocked() {
        val snap = recorder.end(now()) ?: return
        persistAndSend(snap)
    }

    private fun checkpointIfDue() {
        if (now() - lastCheckpointMs >= CHECKPOINT_INTERVAL_MS) checkpointLocked()
    }

    private fun checkpointLocked() {
        lastCheckpointMs = now()
        val snap = recorder.snapshot(now()) ?: return
        persistAndSend(snap)
    }

    private fun persistAndSend(snap: SessionSnapshot) {
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
        )
        scope.launch {
            dao.upsert(entity)
            send(entity)
        }
    }

    private suspend fun send(e: PendingReadingSessionEntity) {
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
            api.saveReadingSession(e.fileId, body)
            Log.d(TAG, "sent ${e.sessionType} session ${e.sessionId} file=${e.fileId} ${e.durationSeconds}s end=${e.endProgress}")
            dao.deleteIfUnchanged(e.sessionId, e.endedAt)
        } catch (t: HttpException) {
            // A 4xx other than timeout/rate-limit won't succeed on retry (e.g. the file was removed).
            if (t.code() in 400..499 && t.code() != 408 && t.code() != 429) {
                Log.w(TAG, "dropping session ${e.sessionId}: HTTP ${t.code()}")
                dao.deleteIfUnchanged(e.sessionId, e.endedAt)
            } else {
                syncScheduler.schedule()
            }
        } catch (t: Exception) {
            syncScheduler.schedule()
        }
    }

    private fun now() = System.currentTimeMillis()

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
