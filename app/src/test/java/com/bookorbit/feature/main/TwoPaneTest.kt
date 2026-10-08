package com.bookorbit.feature.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TwoPaneTest {
    @Test
    fun `list tabs use two panes once the window is wide enough`() {
        for (route in listOf("library", "search", "notes", "downloads", "author/{id}?name={name}", "series/{id}?name={name}")) {
            assertTrue(route, TwoPane.isActive(TWO_PANE_BREAKPOINT_DP, route))
            assertTrue(route, TwoPane.isActive(1280, route))
        }
    }

    @Test
    fun `narrower windows keep a single pane`() {
        assertFalse(TwoPane.isActive(TWO_PANE_BREAKPOINT_DP - 1, "library"))
        assertFalse(TwoPane.isActive(700, "search"))
        assertFalse(TwoPane.isActive(411, "notes"))
    }

    @Test
    fun `non-list screens never split, however wide the window is`() {
        for (route in listOf("home", "you", "stats", "settings", "bookdrop", "book/{id}", null)) {
            assertFalse(route.toString(), TwoPane.isActive(2000, route))
        }
    }

    @Test
    fun `the breakpoint is above the rail breakpoint so there is a rail-only range`() {
        assertEquals(true, TWO_PANE_BREAKPOINT_DP > WIDE_BREAKPOINT_DP)
    }
}
