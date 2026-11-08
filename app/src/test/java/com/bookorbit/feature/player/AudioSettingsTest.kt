package com.bookorbit.feature.player

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioSettingsTest {
    @Test
    fun `parse maps stored names and defaults to BOOK`() {
        assertEquals(ProgressBarMode.BOOK, ProgressBarMode.parse("BOOK"))
        assertEquals(ProgressBarMode.CHAPTER, ProgressBarMode.parse("CHAPTER"))
        assertEquals(ProgressBarMode.BOOK, ProgressBarMode.parse(null))
        assertEquals(ProgressBarMode.BOOK, ProgressBarMode.parse("garbage"))
    }

    @Test
    fun `AudioSettings defaults to whole-book progress`() {
        assertEquals(ProgressBarMode.BOOK, AudioSettings().progressBarMode)
    }

    @Test
    fun `skip seconds snap to 5 s steps within 5 to 60`() {
        assertEquals(5, snapSkipSeconds(0))
        assertEquals(10, snapSkipSeconds(10))
        assertEquals(15, snapSkipSeconds(13))
        assertEquals(60, snapSkipSeconds(200))
    }

    @Test
    fun `previous track action defaults to chapter`() {
        assertEquals(PreviousTrackAction.CHAPTER, AudioSettings().previousTrackAction)
        assertEquals(PreviousTrackAction.SKIP_BACK, PreviousTrackAction.parse("SKIP_BACK"))
        assertEquals(PreviousTrackAction.CHAPTER, PreviousTrackAction.parse("garbage"))
    }
}
