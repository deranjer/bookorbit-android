package com.bookorbit.feature.sessions

import com.bookorbit.core.auth.SessionOwner
import org.junit.Assert.*
import org.junit.Test

class ReadingActivityArbiterTest {
    private val owner = SessionOwner("https://synthetic.test", 1, 2)

    @Test fun `empty or signed out state has no activity`() {
        val arbiter = ReadingActivityArbiter()
        assertNull(arbiter.choose(owner, true))
        arbiter.openReader(owner, 9, 10.0)
        assertNull(arbiter.choose(null, true))
    }

    @Test fun `text reading is active only in foreground`() {
        val arbiter = ReadingActivityArbiter(); arbiter.openReader(owner, 9, 10.0)
        assertTrue(arbiter.choose(owner, true)!!.active)
        assertFalse(arbiter.choose(owner, false)!!.active)
        assertEquals(SessionType.READ, arbiter.choose(owner, true)!!.type)
    }

    @Test fun `preparing native narration never starts a parallel listening session`() {
        val arbiter = ReadingActivityArbiter(); arbiter.openReader(owner, 9, 10.0)
        arbiter.openNarration(owner, 9)
        assertEquals("read:9", arbiter.choose(owner, true)!!.key)
    }

    @Test fun `playing narration is one listening activity in foreground and background`() {
        val arbiter = ReadingActivityArbiter(); arbiter.openReader(owner, 9, 10.0)
        val narration = arbiter.openNarration(owner, 9); arbiter.narrationPlaying(narration, true)
        assertEquals("narration:9", arbiter.choose(owner, true)!!.key)
        assertEquals("narration:9", arbiter.choose(owner, false)!!.key)
        assertEquals(SessionType.LISTEN, arbiter.choose(owner, false)!!.type)
        assertTrue(arbiter.choose(owner, false)!!.active)
    }

    @Test fun `closing the reader cannot close background narration`() {
        val arbiter = ReadingActivityArbiter(); val reader = arbiter.openReader(owner, 9, 10.0)
        val narration = arbiter.openNarration(owner, 9); arbiter.narrationPlaying(narration, true)
        arbiter.closeReader(reader)
        assertTrue(arbiter.choose(owner, false)!!.active)
        arbiter.closeNarration(narration)
        assertNull(arbiter.choose(owner, false))
    }

    @Test fun `pause resumes foreground reading without counting both types at once`() {
        val arbiter = ReadingActivityArbiter(); arbiter.openReader(owner, 9, 10.0)
        val narration = arbiter.openNarration(owner, 9); arbiter.narrationPlaying(narration, true)
        arbiter.narrationPlaying(narration, false)
        assertEquals("read:9", arbiter.choose(owner, true)!!.key)
        assertFalse(arbiter.choose(owner, false)!!.active)
    }

    @Test fun `only actual text coordinates for the same edition enter narration statistics`() {
        val arbiter = ReadingActivityArbiter(); val reader = arbiter.openReader(owner, 9, 10.0)
        val narration = arbiter.openNarration(owner, 9); arbiter.narrationPlaying(narration, true)
        arbiter.readerProgress(reader, 15.0)
        assertEquals(15.0, arbiter.choose(owner, false)!!.percent!!, 0.0)
        val otherReader = arbiter.openReader(owner, 10, 40.0); arbiter.readerProgress(otherReader, 80.0)
        assertEquals(15.0, arbiter.choose(owner, true)!!.percent!!, 0.0)
    }

    @Test fun `a stale reader handle cannot move or close a replacement reader even for the same file`() {
        val arbiter = ReadingActivityArbiter(); val first = arbiter.openReader(owner, 9, 10.0)
        arbiter.openReader(owner, 9, 20.0)
        arbiter.readerProgress(first, 90.0); arbiter.closeReader(first)
        assertEquals(20.0, arbiter.choose(owner, true)!!.percent!!, 0.0)
    }

    @Test fun `an old narration callback cannot pause or close a replacement queue`() {
        val arbiter = ReadingActivityArbiter(); val first = arbiter.openNarration(owner, 9)
        val second = arbiter.openNarration(owner, 10); arbiter.narrationPlaying(second, true)
        arbiter.narrationPlaying(first, false); arbiter.closeNarration(first)
        assertEquals("narration:10", arbiter.choose(owner, false)!!.key)
        assertTrue(arbiter.choose(owner, false)!!.active)
    }

    @Test fun `account generation changes discard old activity instead of relabeling it`() {
        val arbiter = ReadingActivityArbiter(); arbiter.openReader(owner, 9, 10.0)
        val narration = arbiter.openNarration(owner, 9); arbiter.narrationPlaying(narration, true)
        assertNull(arbiter.choose(owner.copy(generation = 3), true))
        assertNull(arbiter.choose(owner, true))
    }

    @Test fun `ordinary audiobook activity yields back to the visible reader when paused or stopped`() {
        val arbiter = ReadingActivityArbiter(); arbiter.openReader(owner, 9, 10.0)
        arbiter.listening(owner, 2, 20, 25.0)
        assertEquals("listen:2", arbiter.choose(owner, true)!!.key)
        arbiter.audioPlaying(owner, false)
        assertEquals("read:9", arbiter.choose(owner, true)!!.key)
        arbiter.closeAudio(owner)
        assertEquals("read:9", arbiter.choose(owner, true)!!.key)
    }

    @Test fun `invalid initial percentages do not enter a persisted JSON session`() {
        val arbiter = ReadingActivityArbiter(); arbiter.openReader(owner, 9, Double.NaN)
        assertNull(arbiter.choose(owner, true)!!.percent)
    }
}
