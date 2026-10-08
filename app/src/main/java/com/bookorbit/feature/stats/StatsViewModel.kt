package com.bookorbit.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bookorbit.core.model.ReadingGoalWidget
import com.bookorbit.core.model.ReadingStreakWidget
import com.bookorbit.core.model.SourceDistribution
import com.bookorbit.core.model.UserDailyReading
import com.bookorbit.core.model.UserStatsSummary
import com.bookorbit.core.network.ApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Days of history behind the heatmap (twelve weeks) and the source split (a year). */
const val HEATMAP_DAYS = 84
private const val SOURCE_DAYS = 365

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val api: ApiService,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val summary: UserStatsSummary? = null,
        val streak: ReadingStreakWidget? = null,
        val goal: ReadingGoalWidget? = null,
        /** Seconds read per day over the last [HEATMAP_DAYS] days, keyed by date, zero-filled. */
        val daily: Map<LocalDate, Long> = emptyMap(),
        val sources: SourceDistribution? = null,
        /** True when nothing at all came back, so the screen can offer a retry. */
        val failed: Boolean = false,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui = _ui.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _ui.update { it.copy(loading = true, failed = false) }
        viewModelScope.launch {
            // Each section loads on its own: an older server may lack some, and that shouldn't blank the rest.
            val summary = async { runCatching { api.getUserStatsSummary() }.getOrNull() }
            val streak = async { runCatching { api.getReadingStreak() }.getOrNull() }
            val goal = async { runCatching { api.getReadingGoal() }.getOrNull() }
            val daily = async { runCatching { api.getDailyReading(HEATMAP_DAYS) }.getOrNull() }
            val sources = async { runCatching { api.getReadingSources(SOURCE_DAYS) }.getOrNull() }

            val s = summary.await()
            val dailyList = daily.await()
            val state = UiState(
                loading = false,
                summary = s,
                streak = streak.await(),
                goal = goal.await(),
                daily = dailyList?.let(::fillDays).orEmpty(),
                sources = sources.await(),
            )
            _ui.value = state.copy(failed = s == null && dailyList == null && state.streak == null)
        }
    }

    /** Spread the sparse server rows over a continuous run of days ending today. */
    private fun fillDays(rows: List<UserDailyReading>): Map<LocalDate, Long> {
        val byDay = rows.mapNotNull { r -> runCatching { LocalDate.parse(r.day.take(10)) }.getOrNull()?.let { it to r.readingSeconds } }.toMap()
        val today = LocalDate.now()
        return (HEATMAP_DAYS - 1 downTo 0).associate { back ->
            val d = today.minusDays(back.toLong())
            d to (byDay[d] ?: 0L)
        }
    }
}
