package com.bookorbit.feature.reader

import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

class NativeNarrationSessionTest {
    private val owner = ReadAlongOwner("https://books.example", 7, 84, "revision")
    private class Port : NativeNarrationSession.Port {
        var starts = 0
        var commands = 0
        var leaves = 0
        override fun start(queue: NativeNarrationQueue, owner: ReadAlongOwner, saved: ReadAlongPosition?, section: Int) { starts++ }
        override fun command(action: String, rate: Double?) { commands++ }
        override fun leave() { leaves++ }
    }
    @Test fun `an audiobook replacement or newer narration request invalidates a pending start`() {
        val session = NativeNarrationSession()
        val port = Port()
        session.bind(port)
        val older = session.ticket()
        val newer = session.ticket()
        assertFalse(session.start(older, mockk(), owner, null, 0))
        assertTrue(session.start(newer, mockk(), owner, null, 0))
        val pending = session.ticket()
        session.leaveForAudiobook()
        assertFalse(session.start(pending, mockk(), owner, null, 0))
        assertEquals(1, port.starts)
    }
    @Test fun `only the exact owner controls playback and a detached service cannot start it`() {
        val session = NativeNarrationSession()
        val port = Port()
        session.bind(port)
        session.publish(NativeNarrationSession.State(owner, 5, ReaderEvent.ReadAlong(true, "paused")))
        assertFalse(session.command(owner.copy(userId = 8), "resume"))
        assertFalse(session.command(owner.copy(fingerprint = "other"), "resume"))
        assertTrue(session.command(owner, "resume"))
        assertEquals(1, port.commands)
        session.unbind(port)
        assertNull(session.state.value)
        assertFalse(session.start(session.ticket(), mockk(), owner, null, 0))
    }
}
