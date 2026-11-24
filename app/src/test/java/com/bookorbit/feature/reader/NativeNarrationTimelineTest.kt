package com.bookorbit.feature.reader

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class NativeNarrationTimelineTest {
    private val json = Json { ignoreUnknownKeys = true }
    private fun clip(index: Int, section: Int, begin: Double) = NativeNarrationClip(index, section,
        "OPS/$section.smil", "OPS/$section.xhtml", if (begin == 0.0) "one" else "two", "OPS/$section.mp3", "audio/mpeg",
        begin, begin + 10, 10.0, "Chapter $section")
    private val playlist = NativeNarrationPlaylist(5, 84, 40.0,
        listOf(clip(0, 0, 0.0), clip(1, 0, 10.0), clip(2, 2, 0.0), clip(3, 2, 10.0)),
        listOf(NativeNarrationSection(0, "OPS/0.xhtml", "First", "OPS/0.smil", 0.0, 20.0),
            NativeNarrationSection(2, "OPS/2.xhtml", "Second", "OPS/2.smil", 20.0, 20.0)),
        listOf(NativeNarrationResource("OPS/0.mp3", "audio/mpeg", 80_293), NativeNarrationResource("OPS/2.mp3", "audio/mpeg", 80_293)))
    private fun timeline(value: NativeNarrationPlaylist = playlist) = NativeNarrationTimeline(value, 5, 84)
    private val saved = ReadAlongPosition(0, "OPS/0.xhtml#two", "OPS/0.mp3", 11.724068, 1.25)

    @Test fun `server playlist binds exact book and file identity and does not accept a default edition`() {
        for (value in listOf(playlist.copy(bookId = 6), playlist.copy(fileId = 85), playlist.copy(fileId = null)))
            assertTrue(runCatching { timeline(value) }.isFailure)
        assertEquals(40.0, timeline().durationSeconds!!, 0.0)
    }

    @Test fun `native item offset restores fractional asset clock rather than fragment start or text percentage`() {
        val timeline = timeline()
        val restored = timeline.restore(saved)!!
        assertEquals(1, restored.index)
        assertEquals(11.724068, restored.assetSeconds, 0.0)
        assertEquals(1724, restored.clipOffsetMs)
        val checkpoint = timeline.checkpoint(restored.index, restored.clipOffsetMs, 1.25)!!
        assertEquals(saved.fragment, checkpoint.fragment)
        assertEquals(saved.audioSrc, checkpoint.audioSrc)
        assertEquals(saved.audioSeconds, checkpoint.audioSeconds, 0.001)
        assertEquals(1.25, checkpoint.rate, 0.0)
    }

    @Test fun `narrated queue skips silent interlude and traverses actual fragments in both directions`() {
        val timeline = timeline()
        assertEquals(2, timeline.startAtSection(1)!!.index)
        assertEquals(1, timeline.previousChapter(2)!!.index)
        assertEquals(2, timeline.nextChapter(0)!!.index)
        assertEquals(1, timeline.previous(2)!!.index)
        assertEquals(2, timeline.next(1)!!.index)
        assertNull(timeline.previous(0))
        assertNull(timeline.next(3))
        assertNull(timeline.startAtSection(3))
        assertNull(timeline.startAtSection(-1))
        assertNull(timeline.nextChapter(-1))
    }

    @Test fun `changed section fragment or audio cannot resume another narration asset`() {
        val timeline = timeline()
        for (position in listOf(saved.copy(sectionIndex = 1), saved.copy(fragment = "OPS/0.xhtml#other"), saved.copy(audioSrc = "OPS/2.mp3")))
            assertNull(timeline.restore(position))
    }

    @Test fun `invalid or completed checkpoints cannot autoplay through restore`() {
        for (position in listOf(saved.copy(audioSeconds = Double.NaN), saved.copy(audioSeconds = -1.0),
            saved.copy(rate = 3.0), saved.copy(completed = true))) assertNull(timeline().restore(position))
    }

    @Test fun `out of clip clock restarts only a uniquely identified exact fragment`() {
        val location = timeline().restore(saved.copy(audioSeconds = 100.0))!!
        assertEquals(1, location.index)
        assertEquals(10.0, location.assetSeconds, 0.0)
        assertEquals(0, location.clipOffsetMs)
    }

    @Test fun `ambiguous repeated fragment and resource clocks fail closed`() {
        val first = playlist.items.first()
        val ambiguous = playlist.copy(items = listOf(first, first.copy(index = 1)))
        assertNull(timeline(ambiguous).restore(ReadAlongPosition(0, first.fragment, first.audioHref, 2.0)))
    }

    @Test fun `invalid timing order and inconsistent duration are rejected`() {
        val first = playlist.items.first()
        for (clip in listOf(first.copy(clipBeginSeconds = -1.0), first.copy(clipBeginSeconds = Double.NaN),
            first.copy(clipEndSeconds = 0.0), first.copy(durationSeconds = 5.0), first.copy(index = 2))) {
            assertTrue(runCatching { timeline(playlist.copy(items = listOf(clip))) }.isFailure)
        }
        assertTrue(runCatching { timeline(playlist.copy(items = listOf(playlist.items[2].copy(index = 0), first.copy(index = 1)))) }.isFailure)
    }

    @Test fun `audio resources and SMIL sections must actually belong to this playlist`() {
        val first = playlist.items.first()
        for (clip in listOf(first.copy(audioHref = "OPS/not-in-manifest.mp3"), first.copy(textHref = "OPS/2.xhtml"),
            first.copy(smilHref = "OPS/2.smil"), first.copy(audioMimeType = "audio/other"))) {
            assertTrue(runCatching { timeline(playlist.copy(items = listOf(clip))) }.isFailure)
        }
        assertTrue(runCatching { timeline(playlist.copy(resources = playlist.resources + playlist.resources.first())) }.isFailure)
    }

    @Test fun `resource paths reject traversal origins and query or fragment injection but preserve literal percent`() {
        for (value in listOf("../secret", "OPS/../secret", "/absolute", "https://other.test/audio", "OPS/audio?fileId=1", "OPS/audio#hash", "OPS\\audio"))
            assertFalse(value, NativeNarrationTimeline.path(value))
        assertTrue(NativeNarrationTimeline.path("OPS/audio 100%.mp3"))
    }

    @Test fun `unknown clip end remains unknown instead of deriving audio alignment from book progress`() {
        val first = playlist.items.first().copy(clipEndSeconds = null, durationSeconds = null)
        val timeline = timeline(playlist.copy(items = listOf(first), durationSeconds = null))
        assertNull(timeline.durationSeconds)
        assertEquals(12.345, timeline.checkpoint(0, 12_345, 1.0)!!.audioSeconds, 0.0)
    }

    @Test fun `Media3 checkpoint bounds clamp the clip while invalid indices clocks and rates are ignored`() {
        val timeline = timeline()
        assertEquals(20.0, timeline.checkpoint(1, 50_000, 1.0)!!.audioSeconds, 0.0)
        assertNull(timeline.checkpoint(-1, 0, 1.0))
        assertNull(timeline.checkpoint(0, -1, 1.0))
        assertNull(timeline.checkpoint(0, 0, Double.NaN))
    }

    @Test fun `DTO roundtrip preserves nullable fields without adding unsupported wire properties`() {
        val encoded = json.encodeToString(NativeNarrationPlaylist.serializer(), playlist)
        val decoded = json.decodeFromString<NativeNarrationPlaylist>(encoded)
        assertEquals(playlist, decoded)
        assertEquals(84, NativeNarrationTimeline(decoded, 5, 84).fileId)
    }

    @Test fun `captured synthetic server v3_9_0 playlist decodes with exact section fragment and resource identities`() {
        val text = checkNotNull(javaClass.getResourceAsStream("/epub-overlay-playlist.json")).bufferedReader().use { it.readText() }
        val captured = json.decodeFromString<NativeNarrationPlaylist>(text)
        val timeline = NativeNarrationTimeline(captured, 5, 84)
        assertEquals(4, timeline.clips.size)
        assertEquals(listOf(0, 2), timeline.sections.map { it.index })
        val position = timeline.checkpoint(1, 1724, 1.0)!!
        assertEquals("OEBPS/chapter.xhtml#two", position.fragment)
        assertEquals("OEBPS/audio.mp3", position.audioSrc)
        assertEquals(11.724, position.audioSeconds, 0.001)
    }
}
