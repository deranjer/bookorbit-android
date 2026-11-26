package com.bookorbit.feature.reader

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ComicOcrOverlayTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun fittedImageCoordinatesSupportWordSentenceAndDoubleTapZoom() {
        val page = OcrPage(100, 100, listOf(OcrWord("Hello", 10f, 10f, 30f, 30f, 0)))
        val selections = mutableListOf<Pair<Int, Boolean>>()
        var zooms = 0
        compose.setContent { MaterialTheme {
            ComicOcrOverlay(page, Modifier.size(200.dp, 100.dp).testTag("ocr"), null,
                onDoubleTap = { zooms++ }, onSelect = { index, sentence -> selections += index to sentence })
        } }
        compose.onNodeWithTag("ocr").performTouchInput { click(Offset(width * 0.35f, height * 0.2f)) }
        compose.mainClock.advanceTimeBy(400)
        compose.runOnIdle { assertEquals(listOf(0 to false), selections) }
        compose.onNodeWithTag("ocr").performTouchInput { longClick(Offset(width * 0.35f, height * 0.2f)) }
        compose.runOnIdle { assertEquals(0 to true, selections.last()) }
        compose.onNodeWithTag("ocr").performTouchInput { doubleClick(Offset(width * 0.35f, height * 0.2f)) }
        compose.runOnIdle { assertEquals(1, zooms) }
    }

    @Test fun stationaryHoldAndHeldDragSelectButOrdinarySwipeDoesNotExtend() {
        val page = OcrPage(100, 100, listOf(
            OcrWord("Hello", 10f, 10f, 30f, 30f, 0),
            OcrWord("world!", 35f, 10f, 60f, 30f, 0)))
        val selections = mutableListOf<Pair<Int, Boolean>>()
        val extensions = mutableListOf<Pair<Int, Int>>()
        compose.setContent { MaterialTheme {
            ComicOcrOverlay(page, Modifier.size(200.dp).testTag("ocr"), null,
                onExtend = { start, end -> extensions += start to end },
                onSelect = { index, sentence -> selections += index to sentence })
        } }
        compose.onNodeWithTag("ocr").performTouchInput {
            down(Offset(width * 0.2f, height * 0.2f))
            advanceEventTime(800)
            moveTo(Offset(width * 0.45f, height * 0.2f))
            up()
        }
        compose.runOnIdle {
            assertTrue(selections.contains(0 to true))
            assertEquals(0 to 1, extensions.last())
            extensions.clear()
        }
        compose.onNodeWithTag("ocr").performTouchInput {
            swipe(Offset(width * 0.2f, height * 0.2f), Offset(width * 0.45f, height * 0.2f), 100)
        }
        compose.runOnIdle { assertTrue(extensions.isEmpty()) }
    }
}
