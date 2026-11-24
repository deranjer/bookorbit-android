package com.bookorbit.feature.bookdetail

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.bookorbit.R
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ReadEditionButtonTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun mainReadAndEditionArrowAreIndependentActions() {
        var reads = 0
        var choices = 0
        compose.setContent { MaterialTheme { ReadEditionButton({ reads++ }, { choices++ }, true) } }
        compose.onNodeWithText(compose.activity.getString(R.string.read)).performClick()
        compose.runOnIdle { assertEquals(1, reads); assertEquals(0, choices) }
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.edition_read_title)).performClick()
        compose.runOnIdle { assertEquals(1, reads); assertEquals(1, choices) }
    }
}
