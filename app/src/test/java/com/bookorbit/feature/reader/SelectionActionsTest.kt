package com.bookorbit.feature.reader

import org.junit.Assert.*
import org.junit.Test

class SelectionActionsTest {
    @Test fun `actual toolbar height clears selection above or below`() {
        assertEquals(188, selectionActionsTop(800, 100, 300f, 340f, 12))
        assertEquals(82, selectionActionsTop(800, 160, 20f, 70f, 12))
    }

    @Test fun `large panel or offscreen selection cannot leave viewport`() {
        for (height in listOf(100, 400, 800)) for (panel in listOf(48, 100, height)) {
            for (top in listOf(-100f, 0f, 70f, 750f, 1500f)) {
                val y = selectionActionsTop(height, panel, top, top + 30, 12)
                assertTrue(y >= 0)
                assertTrue(y + panel <= height)
            }
        }
    }
}
