package com.bookorbit.feature.reader

import io.mockk.mockk
import io.mockk.every
import org.junit.Assert.*
import org.junit.Test

class NativeNarrationSessionTest {
    private fun queue() = mockk<NativeNarrationQueue> { every { plan } returns mockk(relaxed = true) }
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
        assertFalse(session.start(older, queue(), owner, null, 0))
        assertTrue(session.start(newer, queue(), owner, null, 0))
        val pending = session.ticket()
        session.leaveForAudiobook()
        assertFalse(session.start(pending, queue(), owner, null, 0))
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
        assertFalse(session.start(session.ticket(), queue(), owner, null, 0))
    }

    @Test fun `a superseded local plan is released without starting the service`() {
        val session = NativeNarrationSession()
        val port = Port()
        session.bind(port)
        val ticket = session.ticket()
        session.ticket()
        var closed = 0
        val source = object : NativeNarrationSource {
            override val timeline: NativeNarrationTimeline get() = error("unused")
            override val owner: com.bookorbit.core.auth.SessionOwner get() = error("unused")
            override fun resourceUri(href: String): String = error("unused")
            override fun dataSourceFactory(): androidx.media3.datasource.DataSource.Factory = error("unused")
            override fun close() { closed++ }
        }
        val pending = mockk<NativeNarrationQueue> { every { plan } returns source }
        assertFalse(session.start(ticket, pending, owner, null, 0))
        assertEquals(1, closed)
        assertEquals(0, port.starts)
    }
}
