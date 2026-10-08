package com.bookorbit.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderBridgeEventsTest {
    @Test
    fun `parses a tap`() {
        val e = ReaderBridge.parseEvent("""{"type":"tap","x":0.82}""")
        assertEquals(ReaderEvent.Tap(0.82), e)
    }

    @Test
    fun `parses a selection with its viewport rect`() {
        val e = ReaderBridge.parseEvent(
            """{"type":"selection","text":"undertaker","cfi":"epubcfi(/6/10!/4/2)","rect":{"left":1.5,"top":2.0,"right":30.0,"bottom":18.0}}""",
        ) as ReaderEvent.Selection
        assertEquals("undertaker", e.text)
        assertEquals("epubcfi(/6/10!/4/2)", e.cfi)
        assertEquals(ViewportRect(1.5, 2.0, 30.0, 18.0), e.rect)
    }

    @Test
    fun `selection without a cfi still parses so the host can ignore it`() {
        val e = ReaderBridge.parseEvent("""{"type":"selection","text":"x","cfi":null,"rect":null}""") as ReaderEvent.Selection
        assertNull(e.cfi)
        assertNull(e.rect)
    }

    @Test
    fun `parses selection cleared and annotation tap`() {
        assertTrue(ReaderBridge.parseEvent("""{"type":"selectionCleared"}""") === ReaderEvent.SelectionCleared)
        val tap = ReaderBridge.parseEvent("""{"type":"annotationTap","cfi":"epubcfi(/6/2)","rect":null}""") as ReaderEvent.AnnotationTap
        assertEquals("epubcfi(/6/2)", tap.cfi)
        assertNull(tap.rect)
    }

    @Test
    fun `a malformed rect is dropped rather than failing the event`() {
        val e = ReaderBridge.parseEvent("""{"type":"selection","text":"x","cfi":"c","rect":{"left":1}}""") as ReaderEvent.Selection
        assertNull(e.rect)
    }

    @Test
    fun `parses a batch of search results`() {
        val e = ReaderBridge.parseEvent(
            """{"type":"searchResults","label":"Chapter 2","items":[{"cfi":"epubcfi(/6/4)","pre":"the ","match":"beadle","post":" said"},{"pre":"no cfi"}]}""",
        ) as ReaderEvent.SearchResults
        assertEquals("Chapter 2", e.label)
        // The item without a cfi can't be jumped to, so it is dropped.
        assertEquals(listOf(SearchHit("epubcfi(/6/4)", "the ", "beadle", " said")), e.hits)
    }

    @Test
    fun `parses search progress and completion`() {
        assertEquals(ReaderEvent.SearchProgress(0.5), ReaderBridge.parseEvent("""{"type":"searchProgress","progress":0.5}"""))
        assertEquals(ReaderEvent.SearchDone(3, true), ReaderBridge.parseEvent("""{"type":"searchDone","total":3,"capped":true}"""))
        assertEquals(ReaderEvent.SearchDone(0, false), ReaderBridge.parseEvent("""{"type":"searchDone"}"""))
    }
}
