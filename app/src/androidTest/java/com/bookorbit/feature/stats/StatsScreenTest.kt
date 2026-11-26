package com.bookorbit.feature.stats

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.bookorbit.R
import com.bookorbit.core.model.ReadingHistory
import com.bookorbit.core.model.ReadingHistoryItem
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class StatsScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun periodsAndPartialFailureRemainActionable() {
        var selected = 0
        var refreshes = 0
        val state = StatsViewModel.UiState(loading = false, partial = true, historyLoading = false, historyFailed = true)
        compose.setContent { MaterialTheme { StatsContent(state, { selected = it }, { refreshes++ }, {}, {}) } }
        compose.onNodeWithText(compose.activity.getString(R.string.stats_period_days, 7)).performClick()
        compose.runOnIdle { assertEquals(7, selected) }
        compose.onNodeWithText(compose.activity.getString(R.string.stats_refresh)).performClick()
        compose.runOnIdle { assertEquals(1, refreshes) }
        compose.onNodeWithText(compose.activity.getString(R.string.stats_partial)).assertIsDisplayed()
    }

    @Test fun largeHistoryIsLazyAndDoesNotInferListeningFromAudioFormat() {
        val monday = historyMonday()
        val history = ReadingHistory(monday.isoYear, monday.isoWeek, monday.toString(), monday.plusWeeks(1).toString(),
            (1..3000).map { ReadingHistoryItem(it, it, "Fixture $it", "M4B", "android", "2026-11-23T10:00:00Z", "2026-11-23T10:01:00Z", 60) })
        compose.setContent { MaterialTheme { StatsContent(StatsViewModel.UiState(loading = false, historyLoading = false, history = history), {}, {}, {}, {}) } }
        compose.onNodeWithText("Fixture 3000").assertDoesNotExist()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(3000)
        compose.onNodeWithText("Fixture 3000").assertIsDisplayed()
        compose.onNodeWithText("LISTEN").assertDoesNotExist()
    }
}
