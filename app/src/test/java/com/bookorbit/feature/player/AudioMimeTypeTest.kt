package com.bookorbit.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioMimeTypeTest {
    @Test fun `server format identifies m4b when stream URL has no extension`() {
        assertEquals("audio/mp4", audioMimeType("M4B", null))
        assertEquals("audio/mp4", audioMimeType("audio/mp4", null))
    }

    @Test fun `filename supplies missing or unknown format`() {
        assertEquals("audio/mp4", audioMimeType(null, "Book.M4B"))
        assertEquals("audio/flac", audioMimeType("AUDIO", "chapter.flac"))
        assertEquals("audio/ogg", audioMimeType(null, "chapter.opus"))
    }

    @Test fun `known metadata wins over filename and unknown input stays unknown`() {
        assertEquals("audio/mpeg", audioMimeType("MP3", "book.m4b"))
        assertNull(audioMimeType(null, "42"))
        assertNull(audioMimeType("PDF", "cover.jpg"))
    }
}
