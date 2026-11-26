package com.bookorbit.feature.reader

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.bookorbit.R
import com.bookorbit.core.model.BookAnnotation
import org.junit.Rule
import org.junit.Test

class AnnotationDeliveryUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun uncertainDeliveryKeepsTextAndNoteVisibleWithoutDestructiveActions() {
        val row = BookAnnotation(-1, 1, text = "Saved selected passage", note = "Saved private note", positionStatus = "delivery-uncertain")
        compose.setContent { MaterialTheme {
            HighlightsSheet(listOf(row), emptyList(), { error("Unconfirmed jump") }, { error("Unconfirmed deletion") }, {}, {}, {}, {})
        } }
        compose.onNodeWithText("Saved selected passage").assertIsDisplayed()
        compose.onNodeWithText("Saved private note").assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.reader_annotation_uncertain)).assertIsDisplayed()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.delete_highlight)).assertIsNotEnabled()
    }
}
