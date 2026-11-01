package com.bookorbit.feature.main

import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.window.core.layout.WindowSizeClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellLayoutTest {
    /** A window whose size class starts at [minWidthDp] by [minHeightDp]. */
    private fun window(minWidthDp: Int, minHeightDp: Int, tabletop: Boolean = false) =
        WindowAdaptiveInfo(WindowSizeClass(minWidthDp, minHeightDp), Posture(isTabletop = tabletop))

    @Test
    fun `phones get the short bar`() {
        assertEquals(NavigationSuiteType.ShortNavigationBarCompact, shellNavigationType(window(0, 900)))
    }

    @Test
    fun `medium widths get the collapsed rail`() {
        assertEquals(NavigationSuiteType.WideNavigationRailCollapsed, shellNavigationType(window(600, 900)))
    }

    @Test
    fun `expanded widths get the sidebar`() {
        assertEquals(NavigationSuiteType.WideNavigationRailExpanded, shellNavigationType(window(840, 480)))
        assertEquals(NavigationSuiteType.WideNavigationRailExpanded, shellNavigationType(window(1200, 900)))
    }

    @Test
    fun `short windows keep a bar, however wide`() {
        // A phone on its side: too short for a rail's items.
        assertEquals(NavigationSuiteType.ShortNavigationBarMedium, shellNavigationType(window(840, 0)))
        assertEquals(NavigationSuiteType.ShortNavigationBarMedium, shellNavigationType(window(600, 900, tabletop = true)))
    }

    @Test
    fun `list screens are the ones that can show a book beside them`() {
        for (route in listOf("library", "search", "notes", "downloads", "author/{id}?name={name}", "series/{id}?name={name}")) {
            assertTrue(route, route in LIST_ROUTES)
        }
        for (route in listOf("home", "you", "stats", "settings", "bookdrop", "book/{id}")) {
            assertFalse(route, route in LIST_ROUTES)
        }
    }
}
