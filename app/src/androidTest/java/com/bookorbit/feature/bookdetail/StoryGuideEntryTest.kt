package com.bookorbit.feature.bookdetail

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.bookorbit.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class StoryGuideEntryTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun guideHasDiscoverableDescriptionAndOpensOnlyOnClick() {
        var opened = 0
        compose.setContent { MaterialTheme { StoryGuideEntry { opened++ } } }
        compose.onNodeWithText(compose.activity.getString(R.string.guide_entry_description)).assertIsDisplayed()
        assertEquals(0, opened)
        compose.onNodeWithText(compose.activity.getString(R.string.guide_title)).performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }
}
