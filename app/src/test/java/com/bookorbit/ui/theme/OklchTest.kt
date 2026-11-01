package com.bookorbit.ui.theme

import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class OklchTest {
    /** Within one step per channel of [expected]: the reference rounds differently in the last bit. */
    private fun assertNear(expected: String, l: Float, c: Float, h: Float) {
        val actual = oklch(l, c, h).toArgb()
        val want = expected.removePrefix("#").toInt(16)
        val near = listOf(16, 8, 0).all { shift -> abs((actual shr shift and 0xFF) - (want shr shift and 0xFF)) <= 1 }
        assertTrue("oklch($l $c $h) was #%06X, expected $expected".format(actual and 0xFFFFFF), near)
    }

    // Expected values from the CSS Color 4 Oklab matrices, computed outside Compose.
    @Test
    fun `token colours match the web client's`() {
        assertNear("#FEFBF7", 0.99f, 0.006f, 80f) // light background
        assertNear("#0E0903", 0.145f, 0.0175f, 80f) // dark background
        assertNear("#1A1815", 0.21f, 0.006f, 80f) // light primary
        assertNear("#E7E0D5", 0.91f, 0.0175f, 80f) // dark primary
        assertNear("#187E36", 0.52f, 0.14f, 148f) // light success
    }

    @Test
    fun `colours outside sRGB are clipped, not wrapped`() {
        assertNear("#E7000B", 0.577f, 0.245f, 27.325f) // light destructive
    }
}
