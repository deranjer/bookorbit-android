package com.bookorbit.feature.reader

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ReaderPagerMotionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun slowSmallDragReturnsAndFastFlickMovesOnePage() {
        lateinit var pager: PagerState
        compose.setContent { MaterialTheme {
            pager = rememberPagerState(initialPage = 2) { 5 }
            ReaderPager(pager, Modifier.fillMaxSize().testTag("pager")) { page -> Box(Modifier.fillMaxSize()) { Text("Page $page") } }
        } }
        compose.onNodeWithTag("pager").performTouchInput {
            swipe(Offset(width * 0.8f, height * 0.5f), Offset(width * 0.73f, height * 0.5f), 1000)
        }
        compose.runOnIdle { assertEquals(2, pager.settledPage) }
        compose.onNodeWithTag("pager").performTouchInput {
            swipe(Offset(width * 0.8f, height * 0.5f), Offset(width * 0.55f, height * 0.5f), 80)
        }
        compose.runOnIdle { assertEquals(3, pager.settledPage) }
        compose.onNodeWithTag("pager").performTouchInput {
            swipe(Offset(width * 0.2f, height * 0.5f), Offset(width * 0.8f, height * 0.5f), 400)
        }
        compose.runOnIdle { assertEquals(2, pager.settledPage) }
    }

    @Test fun mangaMirrorsSwipeDirectionAndStaysInsideTheBook() {
        lateinit var pager: PagerState
        compose.setContent { MaterialTheme {
            pager = rememberPagerState(initialPage = 0) { 2 }
            ReaderPager(pager, Modifier.fillMaxSize().testTag("pager"), rightToLeft = true, animated = false) { page ->
                Box(Modifier.fillMaxSize()) { Text("Page $page") }
            }
        } }
        repeat(2) {
            compose.onNodeWithTag("pager").performTouchInput {
                swipe(Offset(width * 0.2f, height * 0.5f), Offset(width * 0.8f, height * 0.5f), 300)
            }
            compose.runOnIdle { assertEquals(1, pager.settledPage) }
        }
        compose.onNodeWithTag("pager").performTouchInput {
            swipe(Offset(width * 0.8f, height * 0.5f), Offset(width * 0.2f, height * 0.5f), 300)
        }
        compose.runOnIdle { assertEquals(0, pager.settledPage) }
    }
}
