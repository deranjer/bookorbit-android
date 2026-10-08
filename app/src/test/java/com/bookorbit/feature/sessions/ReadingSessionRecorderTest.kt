package com.bookorbit.feature.sessions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingSessionRecorderTest {
    private val gap = 120_000L

    private fun recorder() = ReadingSessionRecorder(maxGapMs = gap)

    @Test
    fun `accrues time between progress events`() {
        val r = recorder()
        r.begin("read:1", 1, SessionType.READ, 10.0, nowMs = 0)
        r.progress(12.0, nowMs = 30_000)
        r.progress(15.0, nowMs = 90_000)
        val snap = r.end(nowMs = 90_000)!!
        assertEquals(90, snap.durationSeconds)
        assertEquals(5.0, snap.progressDelta!!, 1e-9)
        assertEquals(15.0, snap.endProgress!!, 1e-9)
    }

    @Test
    fun `a long gap is capped so idle time is not counted`() {
        val r = recorder()
        r.begin("read:1", 1, SessionType.READ, 0.0, nowMs = 0)
        r.progress(1.0, nowMs = 60 * 60_000L) // an hour with no page turn
        val snap = r.end(nowMs = 60 * 60_000L)!!
        assertEquals(120, snap.durationSeconds)
    }

    @Test
    fun `inactive time does not accrue`() {
        val r = recorder()
        r.begin("read:1", 1, SessionType.READ, 0.0, nowMs = 0)
        r.setActive(false, nowMs = 20_000) // backgrounded after 20s
        r.setActive(true, nowMs = 500_000) // back much later
        r.progress(5.0, nowMs = 510_000)
        val snap = r.end(nowMs = 510_000)!!
        assertEquals(30, snap.durationSeconds)
    }

    @Test
    fun `snapshot is repeatable and keeps the same session id`() {
        val r = recorder()
        r.begin("read:1", 1, SessionType.READ, 0.0, nowMs = 0, sessionId = "abc")
        val first = r.snapshot(nowMs = 40_000)!!
        r.progress(8.0, nowMs = 60_000)
        val second = r.snapshot(nowMs = 60_000)!!
        assertEquals("abc", first.sessionId)
        assertEquals("abc", second.sessionId)
        assertEquals(40, first.durationSeconds)
        assertEquals(60, second.durationSeconds)
        assertTrue(second.endedAtMs > first.endedAtMs)
    }

    @Test
    fun `end closes the session`() {
        val r = recorder()
        r.begin("listen:2", 9, SessionType.LISTEN, null, nowMs = 0)
        assertTrue(r.isOpen)
        assertNotNull(r.end(nowMs = 1_000))
        assertFalse(r.isOpen)
        assertNull(r.snapshot(nowMs = 2_000))
        assertNull(r.end(nowMs = 3_000))
    }

    @Test
    fun `progress delta is null when the start position is unknown`() {
        val r = recorder()
        r.begin("read:1", 1, SessionType.READ, null, nowMs = 0)
        r.progress(40.0, nowMs = 10_000)
        val snap = r.end(nowMs = 10_000)!!
        assertNull(snap.progressDelta)
        assertEquals(40.0, snap.endProgress!!, 1e-9)
    }

    @Test
    fun `progress is clamped to 0-100`() {
        val r = recorder()
        r.begin("read:1", 1, SessionType.READ, 99.0, nowMs = 0)
        r.progress(120.0, nowMs = 1_000)
        assertEquals(100.0, r.snapshot(1_000)!!.endProgress!!, 1e-9)
    }

    @Test
    fun `inactiveForMs reports time since pause and resets on resume`() {
        val r = recorder()
        r.begin("listen:2", 9, SessionType.LISTEN, 0.0, nowMs = 0)
        assertEquals(0, r.inactiveForMs(5_000))
        r.setActive(false, nowMs = 10_000)
        assertEquals(40_000, r.inactiveForMs(50_000))
        r.setActive(true, nowMs = 60_000)
        assertEquals(0, r.inactiveForMs(70_000))
    }
}
