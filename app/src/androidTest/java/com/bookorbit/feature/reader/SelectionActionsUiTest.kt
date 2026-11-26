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
import com.bookorbit.R
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

class SelectionActionsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun listeningActionAppearsOnlyForNarratedSelectionAndOnlyInsideMoreMenu() {
        var narrated by mutableStateOf(false)
        var starts = 0
        val label = compose.activity.getString(R.string.read_along_from_selection)
        val more = compose.activity.getString(R.string.reader_selection_more)
        compose.setContent { MaterialTheme {
            HighlightToolbar(null, {}, {}, null, onListenFromHere = if (narrated) ({ starts++ }) else null)
        } }
        compose.onNodeWithContentDescription(more).performClick()
        compose.onNodeWithText(label).assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(R.string.add_note)).performClick()
        compose.runOnIdle { narrated = true }
        compose.onNodeWithText(label).assertDoesNotExist()
        compose.onNodeWithContentDescription(more).performClick()
        compose.onNodeWithText(label).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, starts) }
    }

    @Test fun narrationBarHasNoChapterRestartButton() {
        compose.setContent { MaterialTheme {
            ReadAlongBar(ReaderEvent.ReadAlong(true, "paused", position = ReadAlongPosition(0, "OPS/ch#one", "OPS/voice", 1.0)), { _, _ -> })
        } }
        compose.onAllNodes(hasClickAction()).assertCountEquals(4)
    }

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
