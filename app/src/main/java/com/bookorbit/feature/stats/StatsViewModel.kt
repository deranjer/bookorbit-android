package com.bookorbit.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bookorbit.core.model.ReadingGoalWidget
import com.bookorbit.core.model.ReadingStreakWidget
import com.bookorbit.core.model.SourceDistribution
import com.bookorbit.core.model.UserStatsSummary
import com.bookorbit.core.model.ReadingHistory
import com.bookorbit.core.auth.SessionManager
import com.bookorbit.core.network.ApiService
import com.bookorbit.core.network.ReadingWidgetsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val api: ApiService,
    private val widgets: ReadingWidgetsRepository,
    private val session: SessionManager,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val summary: UserStatsSummary? = null,
        val streak: ReadingStreakWidget? = null,
        val goal: ReadingGoalWidget? = null,
        val days: Int = 30,
        val timezone: String? = null,
        val daily: Map<LocalDate, Long> = emptyMap(),
        val previousSeconds: Long? = null,
        val sources: SourceDistribution? = null,
        val partial: Boolean = false,
        val failed: Boolean = false,
        val week: LocalDate = historyMonday(),
        val history: ReadingHistory? = null,
        val historyLoading: Boolean = true,
        val historyFailed: Boolean = false,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui = _ui.asStateFlow()

    private var refreshJob: Job? = null
    private var historyJob: Job? = null
    private var revision = 0L
    private var historyRevision = 0L

    init {
        viewModelScope.launch {
            session.owners.collect {
                refreshJob?.cancel()
                historyJob?.cancel()
                revision++
                historyRevision++
                _ui.value = UiState(loading = session.owner() != null, historyLoading = session.owner() != null)
                if (session.owner() != null) refresh()
            }
        }
    }

    fun selectPeriod(days: Int) {
        if (days !in STATS_PERIODS || days == _ui.value.days) return
        _ui.update { it.copy(days = days) }
        refresh()
    }

    fun refresh() {
        val owner = session.owner() ?: return
        refreshJob?.cancel()
        val ticket = ++revision
        val days = _ui.value.days
        _ui.update { it.copy(loading = true, failed = false, partial = false, daily = emptyMap(), previousSeconds = null, sources = null) }
        refreshJob = viewModelScope.launch {
            val summary = async { optional { api.getUserStatsSummary() } }
            val readingWidgets = async { optional { widgets.load() } }
            // UTC range floor and account-local day keys can differ by a day at either edge.
            val daily = async { optional { api.getDailyReading(days * 2 + 2) } }
            val sources = async { optional { api.getReadingSources(days) } }
            val profile = async { optional { api.getMe() } }
            val s = summary.await()
            val w = readingWidgets.await()
            val rows = daily.await()
            val source = sources.await()
            val me = profile.await()?.takeIf { it.id == owner.userId }
            val zone = if (me != null) statsZone(me.settings?.timezone ?: "UTC") else null
            val today = zone?.let { LocalDate.now(it) }
            val current = optional { if (rows != null && today != null) statsDays(rows, today, days) else null }
            val previous = optional { if (rows != null && today != null) statsDays(rows, today.minusDays(days.toLong()), days).values.sum() else null }
            if (ticket != revision || !session.owns(owner)) return@launch
            _ui.update { it.copy(loading = false, summary = s, streak = w?.streak, goal = w?.goal,
                daily = current.orEmpty(), previousSeconds = previous, timezone = zone?.id, sources = source,
                partial = s == null || w?.streak == null || w.goal == null || current == null || source == null,
                failed = s == null && w?.streak == null && w?.goal == null && current == null && source == null) }
        }
        loadHistory()
    }

    fun moveWeek(delta: Int) {
        if (delta !in listOf(-1, 1)) return
        val next = _ui.value.week.plusWeeks(delta.toLong())
        if (next > historyMonday() || next.isoYear !in 1970..2100) return
        _ui.update { it.copy(week = next) }
        loadHistory()
    }

    fun loadHistory() {
        val owner = session.owner() ?: return
        historyJob?.cancel()
        val ticket = ++historyRevision
        val week = _ui.value.week
        _ui.update { it.copy(historyLoading = true, history = null, historyFailed = false) }
        historyJob = viewModelScope.launch {
            val result = optional { api.getReadingHistory(week.isoYear, week.isoWeek).also {
                require(it.year == week.isoYear && it.week == week.isoWeek && it.items.size <= 3000)
                require(it.items.map { row -> row.sessionId }.distinct().size == it.items.size)
                require(it.items.all { row -> row.sessionId > 0 && row.bookId > 0 && row.durationSeconds >= 0 })
            } }
            if (ticket != historyRevision || !session.owns(owner)) return@launch
            _ui.update { it.copy(historyLoading = false, history = result, historyFailed = result == null) }
        }
    }
}

private suspend fun <T> optional(block: suspend () -> T): T? = try { block() }
catch (error: CancellationException) { throw error }
catch (_: Exception) { null }
