package com.bookorbit.feature.reader

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SelectionActionsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun measuredToolbarStaysInSmallViewportAndClearsSelectionWithLargeText() {
        lateinit var selection: Rect
        compose.setContent { MaterialTheme {
            val density = LocalDensity.current
            selection = with(density) { Rect(30.dp.toPx(), 220.dp.toPx(), 230.dp.toPx(), 260.dp.toPx()) }
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.4f)) {
                Box(Modifier.size(280.dp, 400.dp).testTag("viewport")) {
                    SelectionActions(selection, Modifier.fillMaxSize()) {
                        HighlightToolbar(null, {}, {}, null, Modifier.testTag("actions"), onTranslate = {}, onDictionary = {})
                    }
                }
            }
        } }
        val viewport = compose.onNodeWithTag("viewport").fetchSemanticsNode().boundsInRoot
        val panel = compose.onNodeWithTag("actions").fetchSemanticsNode().boundsInRoot
        assertTrue(panel.left >= viewport.left)
        assertTrue(panel.right <= viewport.right)
        assertTrue(panel.top >= viewport.top)
        assertTrue(panel.bottom <= viewport.bottom)
        assertTrue(panel.bottom < viewport.top + selection.top || panel.top > viewport.top + selection.bottom)
    }
}
