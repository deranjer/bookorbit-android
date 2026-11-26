package com.bookorbit.feature.reader

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.bookorbit.R
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** UI-only fixtures; never open books, use credentials or contact a server. */
class ReaderControlsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun tapZoneHelpMirrorsMangaAndClosesWithoutNavigation() {
        val rtl = mutableStateOf(false)
        val shown = mutableStateOf(true)
        var dismissed = 0
        compose.setContent { MaterialTheme {
            if (shown.value) ReaderTapZonesHelp(rightToLeft = rtl.value, comic = true, onDismiss = { dismissed++; shown.value = false })
        } }
        val previous = compose.activity.getString(R.string.previous_page)
        val next = compose.activity.getString(R.string.next_page)
        compose.onNodeWithText(previous).assertIsDisplayed()
        assertTrue(compose.onNodeWithText(previous).fetchSemanticsNode().boundsInRoot.left <
            compose.onNodeWithText(next).fetchSemanticsNode().boundsInRoot.left)
        compose.runOnIdle { rtl.value = true }
        assertTrue(compose.onNodeWithText(previous).fetchSemanticsNode().boundsInRoot.left >
            compose.onNodeWithText(next).fetchSemanticsNode().boundsInRoot.left)
        compose.onNodeWithText(compose.activity.getString(R.string.reader_zones_close)).performClick()
        compose.onAllNodesWithText(previous).assertCountEquals(0)
        compose.runOnIdle { assertEquals(1, dismissed) }
    }

    @Test fun scrollingHelpShowsMenuEverywhereInsteadOfPageCommands() {
        compose.setContent { MaterialTheme { ReaderTapZonesHelp(paginated = false, onDismiss = {}) } }
        compose.onAllNodesWithText(compose.activity.getString(R.string.tap_for_menu)).assertCountEquals(3)
        compose.onAllNodesWithText(compose.activity.getString(R.string.previous_page)).assertCountEquals(0)
        compose.onAllNodesWithText(compose.activity.getString(R.string.next_page)).assertCountEquals(0)
        compose.onNodeWithText(compose.activity.getString(R.string.reader_zones_scroll)).assertIsDisplayed()
    }

    @Test fun settingsApplyFontFlowMarginsAndReset() {
        val settings = mutableStateOf(ReaderSettings())
        compose.setContent { MaterialTheme {
            ReaderSettingsSheet(settings.value, onChange = { settings.value = it }, onDismiss = {})
        } }
        fun text(id: Int) = compose.activity.getString(id)
        val increaseFont = compose.activity.getString(R.string.reader_increase_setting, text(R.string.font_size))
        compose.onNodeWithContentDescription(increaseFont).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(17, settings.value.fontSize) }
        compose.onNodeWithText(text(R.string.reader_continuous)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals("scrolled", settings.value.flow) }
        compose.onAllNodesWithText(text(R.string.reader_page_margins)).assertCountEquals(0)
        compose.onNodeWithText(text(R.string.paginated)).performScrollTo().performClick()
        val increaseMargin = compose.activity.getString(R.string.reader_increase_setting, text(R.string.reader_page_margins))
        compose.onNodeWithContentDescription(increaseMargin).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(44, settings.value.pageMargin) }
        compose.onNodeWithText(text(R.string.reader_settings_reset)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(ReaderSettings(), settings.value) }
    }
}
