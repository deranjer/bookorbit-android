package com.bookorbit.feature.player

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioSettingsTest {
    @Test
    fun `parse maps stored names and defaults to CHAPTER`() {
        assertEquals(ProgressBarMode.BOOK, ProgressBarMode.parse("BOOK"))
        assertEquals(ProgressBarMode.CHAPTER, ProgressBarMode.parse("CHAPTER"))
        assertEquals(ProgressBarMode.CHAPTER, ProgressBarMode.parse(null))
        assertEquals(ProgressBarMode.CHAPTER, ProgressBarMode.parse("garbage"))
    }

    @Test
    fun `AudioSettings defaults to chapter progress`() {
        assertEquals(ProgressBarMode.CHAPTER, AudioSettings().progressBarMode)
    }
}
