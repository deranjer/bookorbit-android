package com.bookorbit.feature.reader

import org.junit.Assert.*
import org.junit.Test

class ReadAlongBridgeTest {
    @Test fun `overlay support and playback state decode without becoming reader failure`() {
        val state = ReaderBridge.parseEvent("""{"type":"readAlong","available":true,"state":"playing","rate":1.5}""") as ReaderEvent.ReadAlong
        assertTrue(state.available)
        assertEquals("playing", state.state)
        assertEquals(1.5, state.rate, 0.0)
    }

    @Test fun `audio failure remains separate from reading failure`() {
        val state = ReaderBridge.parseEvent("""{"type":"readAlong","available":true,"state":"error","error":"audio"}""")
        assertTrue(state is ReaderEvent.ReadAlong)
        assertFalse(state is ReaderEvent.Error)
    }

    @Test fun `invalid playback rates are not accepted from bridge`() {
        val state = ReaderBridge.parseEvent("""{"type":"readAlong","available":false,"state":"unavailable","rate":500}""") as ReaderEvent.ReadAlong
        assertEquals(1.0, state.rate, 0.0)
    }
}
