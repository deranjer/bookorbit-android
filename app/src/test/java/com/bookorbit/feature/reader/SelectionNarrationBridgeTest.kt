package com.bookorbit.feature.reader

import org.junit.Assert.*
import org.junit.Test

class SelectionNarrationBridgeTest {
    @Test fun `selection carries an exact optional narration coordinate`() {
        val event = ReaderBridge.parseEvent("""{"type":"selection","text":"word","cfi":"epubcfi(/6/2)","narration":{"sectionIndex":2,"fragment":"OPS/ch.xhtml#p","audioSrc":"OPS/voice.mp3","audioSeconds":12.5}}""") as ReaderEvent.Selection
        assertEquals(2, event.narration!!.sectionIndex)
        assertEquals("OPS/ch.xhtml#p", event.narration!!.fragment)
        assertEquals(12.5, event.narration!!.audioSeconds, 0.0)
    }
    @Test fun `malformed or completed narration cannot become a listening action`() {
        for (payload in listOf("null", "{}", """{"sectionIndex":-1,"fragment":"p","audioSrc":"v","audioSeconds":0}""",
            """{"sectionIndex":0,"fragment":"p","audioSrc":"v","audioSeconds":0,"completed":true}""")) {
            val event = ReaderBridge.parseEvent("""{"type":"selection","text":"word","narration":$payload}""") as ReaderEvent.Selection
            assertNull(event.narration)
        }
    }
}
