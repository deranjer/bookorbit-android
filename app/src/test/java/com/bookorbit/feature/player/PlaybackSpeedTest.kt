package com.bookorbit.feature.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackSpeedTest {
    @Test
    fun `a book's own speed wins over the default`() {
        assertEquals(1.5f, resolveSpeed(globalSpeed = 1.0f, bookSpeed = 1.5f), 1e-6f)
    }

    @Test
    fun `a book without its own speed follows the default`() {
        assertEquals(1.25f, resolveSpeed(globalSpeed = 1.25f, bookSpeed = null), 1e-6f)
    }

    @Test
    fun `resolved speed is kept inside the supported range`() {
        assertEquals(MAX_SPEED, resolveSpeed(1.0f, 9f), 1e-6f)
        assertEquals(MIN_SPEED, resolveSpeed(0.1f, null), 1e-6f)
    }

    @Test
    fun `snapping rounds to 0_05 steps`() {
        assertEquals(1.3f, snapSpeed(1.2999999f), 1e-6f)
        assertEquals(1.25f, snapSpeed(1.2501f), 1e-6f)
        assertEquals(0.5f, snapSpeed(0.2f), 1e-6f)
        assertEquals(3.0f, snapSpeed(3.4f), 1e-6f)
    }

    @Test
    fun `labels drop trailing zeros`() {
        assertEquals("1x", speedLabel(1.0f))
        assertEquals("1.25x", speedLabel(1.25f))
        assertEquals("1.3x", speedLabel(1.3f))
        assertEquals("0.75x", speedLabel(0.75f))
        assertEquals("2x", speedLabel(2.0f))
    }
}
