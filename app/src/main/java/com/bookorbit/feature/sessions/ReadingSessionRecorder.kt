package com.bookorbit.feature.sessions

import java.util.UUID

enum class SessionType(val wire: String) { READ("read"), LISTEN("listen") }

/** A point-in-time view of an open session, in the shape the server's session endpoint wants. */
data class SessionSnapshot(
    val sessionId: String,
    val fileId: Int,
    val type: SessionType,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val durationSeconds: Int,
    val progressDelta: Double?,
    val endProgress: Double?,
)

/**
 * Pure bookkeeping for one open reading/listening session: active time, progress delta, ids. No
 * Android or I/O so it's unit-testable; [ReadingSessionTracker] owns persistence and lifecycle.
 *
 * Active time accrues only while the session is "active" (screen in the foreground for reading,
 * playing for listening). Each accrual step is capped at [maxGapMs], so a long pause between page
 * turns, or the process being frozen, doesn't count as time spent reading.
 */
class ReadingSessionRecorder(private val maxGapMs: Long) {
    var key: String? = null
        private set
    private var sessionId = ""
    private var fileId = 0
    private var type = SessionType.READ
    private var startedAtMs = 0L
    private var startProgress: Double? = null
    private var endProgress: Double? = null
    private var accruedMs = 0L
    private var activeSince: Long? = null
    private var inactiveSince: Long? = null

    val isOpen: Boolean get() = key != null

    fun begin(
        key: String,
        fileId: Int,
        type: SessionType,
        progress: Double?,
        nowMs: Long,
        sessionId: String = UUID.randomUUID().toString(),
    ) {
        this.key = key
        this.sessionId = sessionId
        this.fileId = fileId
        this.type = type
        startedAtMs = nowMs
        startProgress = progress
        endProgress = progress
        accruedMs = 0
        activeSince = nowMs
        inactiveSince = null
    }

    /** Record the latest position (0-100) and count the time since the previous activity. */
    fun progress(percent: Double, nowMs: Long) {
        if (!isOpen) return
        accrue(nowMs)
        endProgress = percent.coerceIn(0.0, 100.0)
    }

    /** Flip between counting and not counting time (foreground/background, playing/paused). */
    fun setActive(active: Boolean, nowMs: Long) {
        if (!isOpen) return
        if (active) {
            if (activeSince == null) activeSince = nowMs
            inactiveSince = null
        } else {
            accrue(nowMs)
            activeSince = null
            if (inactiveSince == null) inactiveSince = nowMs
        }
    }

    /** How long the session has been inactive, or 0 while active. */
    fun inactiveForMs(nowMs: Long): Long = inactiveSince?.let { nowMs - it } ?: 0L

    fun snapshot(nowMs: Long): SessionSnapshot? {
        if (!isOpen) return null
        accrue(nowMs)
        val start = startProgress
        val end = endProgress
        return SessionSnapshot(
            sessionId = sessionId,
            fileId = fileId,
            type = type,
            startedAtMs = startedAtMs,
            endedAtMs = nowMs,
            durationSeconds = (accruedMs / 1000).toInt(),
            progressDelta = if (start != null && end != null) end - start else null,
            endProgress = end,
        )
    }

    fun end(nowMs: Long): SessionSnapshot? {
        val snap = snapshot(nowMs)
        key = null
        activeSince = null
        inactiveSince = null
        return snap
    }

    private fun accrue(nowMs: Long) {
        val since = activeSince ?: return
        accruedMs += (nowMs - since).coerceIn(0L, maxGapMs)
        activeSince = nowMs
    }
}
