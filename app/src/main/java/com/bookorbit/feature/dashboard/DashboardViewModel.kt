package com.bookorbit.feature.dashboard

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bookorbit.core.model.BookCard
import com.bookorbit.core.model.ReadingGoalWidget
import com.bookorbit.core.model.ReadingStreakWidget
import com.bookorbit.core.model.ScrollerType
import com.bookorbit.feature.browse.BrowseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repo: BrowseRepository,
) : ViewModel() {

    data class UiState(
        val continueReading: List<BookCard> = emptyList(),
        val continueListening: List<BookCard> = emptyList(),
        val recentlyAdded: List<BookCard> = emptyList(),
        val loading: Boolean = true,
        /** True when every scroller request failed — distinct from a genuinely empty library. */
        val error: Boolean = false,
        /** Null when the server predates the widget endpoints (or the call failed): the strip is simply hidden. */
        val streak: ReadingStreakWidget? = null,
        val goal: ReadingGoalWidget? = null,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui = _ui.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _ui.value = _ui.value.copy(loading = true)
        viewModelScope.launch {
            var failures = 0
            suspend fun load(type: String): List<BookCard> =
                runCatching { repo.scroller(type) }
                    .onFailure {
                        failures++
                        Log.w("Dashboard", "scroller $type failed", it)
                    }
                    .getOrDefault(emptyList())

            val streak = async { runCatching { repo.readingStreak() }.getOrNull() }
            val goal = async { runCatching { repo.readingGoal() }.getOrNull() }
            val continueReading = async { load(ScrollerType.CONTINUE_READING) }
            val continueListening = async { load(ScrollerType.CONTINUE_LISTENING) }
            val recentlyAdded = async { load(ScrollerType.RECENTLY_ADDED) }
            val reading = continueReading.await()
            val listening = continueListening.await()
            val recent = recentlyAdded.await()
            _ui.value = UiState(
                reading,
                listening,
                recent,
                loading = false,
                error = failures == 3,
                streak = streak.await(),
                goal = goal.await(),
            )
        }
    }
}
