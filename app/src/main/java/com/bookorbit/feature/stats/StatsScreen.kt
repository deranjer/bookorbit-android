package com.bookorbit.feature.stats

import androidx.compose.ui.res.stringResource
import com.bookorbit.R
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bookorbit.core.model.SourceDistribution
import com.bookorbit.feature.dashboard.StatsStrip
import java.time.DayOfWeek
import java.time.LocalDate

/** Daily aggregates and session history remain separate server views. */
@Composable
fun StatsScreen(vm: StatsViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()

    StatsContent(ui, vm::selectPeriod, vm::refresh, vm::moveWeek, vm::loadHistory)
}

@Composable
internal fun StatsContent(ui: StatsViewModel.UiState, onPeriod: (Int) -> Unit, onRefresh: () -> Unit,
    onWeek: (Int) -> Unit, onHistoryRetry: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                STATS_PERIODS.forEach { days -> FilterChip(selected = ui.days == days, onClick = { onPeriod(days) },
                    label = { Text(stringResource(R.string.stats_period_days, days)) }) }
                TextButton(onClick = onRefresh) { Text(stringResource(R.string.stats_refresh)) }
            }
            if (ui.loading) CircularProgressIndicator(Modifier.padding(16.dp))
            if (ui.failed || ui.partial) Text(stringResource(if (ui.failed) R.string.couldn_t_load_your_reading else R.string.stats_partial),
                modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
            Text(stringResource(R.string.stats_all_time), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelLarge)
            ui.summary?.let { s ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Tile(stringResource(R.string.started), s.startedBooks.toString(), Modifier.weight(1f))
                    Tile(stringResource(R.string.in_progress), s.inProgressBooks.toString(), Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Tile(stringResource(R.string.completed), s.completedBooks.toString(), Modifier.weight(1f))
                    Tile(stringResource(R.string.avg_progress), "${s.meanProgressPercent.toInt()}%", Modifier.weight(1f))
                }
            }

            StatsStrip(streak = ui.streak, goal = ui.goal)

            if (ui.daily.isNotEmpty()) {
                val values = ui.daily.values.toList()
                val total = values.sum()
                SectionTitle(stringResource(R.string.stats_period_days, ui.days))
                Text(
                    stringResource(R.string.total_a_day, formatSeconds(total), formatSeconds(total / ui.days)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                ui.previousSeconds?.let { previous -> Text(stringResource(R.string.stats_previous, formatSeconds(previous)),
                    Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium) }
                Text(stringResource(R.string.stats_timezone, ui.timezone.orEmpty()), Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodySmall)
                val group = if (ui.days > 90) 14 else if (ui.days > 30) 7 else 1
                Text(stringResource(R.string.stats_chart_group, group), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
                BarChart(values.chunked(group).map { it.sum() }, Modifier.fillMaxWidth().padding(16.dp).height(110.dp))

                SectionTitle(stringResource(R.string.reading_heatmap))
                Heatmap(ui.daily.entries.toList().takeLast(84).associate { it.toPair() }, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }

            ui.sources?.let {
                SectionTitle(stringResource(R.string.where_you_read))
                Text(stringResource(R.string.stats_source_window, ui.days), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
                SourceSplit(it, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            SectionTitle(stringResource(R.string.stats_history))
            Text(stringResource(R.string.stats_history_note), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onWeek(-1) }, enabled = ui.week.minusWeeks(1).isoYear >= 1970) { Text(stringResource(R.string.stats_week_previous)) }
                TextButton(onClick = { onWeek(1) }, enabled = ui.week < historyMonday()) { Text(stringResource(R.string.stats_week_next)) }
            }
            Text("${ui.week} — ${ui.week.plusDays(6)} · UTC", Modifier.padding(horizontal = 16.dp))
            if (ui.historyLoading) CircularProgressIndicator(Modifier.padding(16.dp))
            if (ui.historyFailed) {
                Text(stringResource(R.string.stats_history_failed), Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onHistoryRetry, modifier = Modifier.padding(horizontal = 16.dp)) { Text(stringResource(R.string.retry)) }
            }
            ui.history?.let {
                if (it.items.isEmpty()) Text(stringResource(R.string.stats_history_empty), Modifier.padding(16.dp))
                if (it.items.size == 3000) Text(stringResource(R.string.stats_history_limit), Modifier.padding(16.dp))
            }
        }
        items(ui.history?.items.orEmpty(), key = { it.sessionId }) { row ->
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(row.bookTitle, style = MaterialTheme.typography.titleSmall)
                    Text(historyTime(row.startedAt), style = MaterialTheme.typography.bodySmall)
                    Text(formatSeconds(row.durationSeconds), style = MaterialTheme.typography.bodyMedium)
                    row.bookSource?.let { source -> Text(SOURCE_LABELS[source.lowercase()]?.let { stringResource(it) } ?: source,
                        style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun BarChart(values: List<Long>, modifier: Modifier) {
    val bar = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.outlineVariant
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Canvas(modifier) {
        val gap = 3.dp.toPx()
        val w = (size.width - gap * (values.size - 1)) / values.size
        values.forEachIndexed { i, v ->
            val h = if (v <= 0) 3.dp.toPx() else (size.height * (v.toFloat() / max)).coerceAtLeast(6.dp.toPx())
            drawRoundRect(
                color = if (v <= 0) empty else bar,
                topLeft = Offset(i * (w + gap), size.height - h),
                size = Size(w, h),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
        }
    }
}

/** Twelve weeks as columns of seven days (Monday on top), shaded by how long you read that day. */
@Composable
private fun Heatmap(daily: Map<LocalDate, Long>, modifier: Modifier) {
    val days = daily.keys.sorted()
    if (days.isEmpty()) return
    // Pad the front so the first column starts on a Monday.
    val lead = (days.first().dayOfWeek.value - DayOfWeek.MONDAY.value)
    val cells: List<LocalDate?> = List(lead) { null } + days
    val weeks = cells.chunked(7)
    val base = MaterialTheme.colorScheme.primary
    val none = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        weeks.forEach { week ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (row in 0 until 7) {
                    val date = week.getOrNull(row)
                    val secs = date?.let { daily[it] } ?: 0L
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (date == null) Color.Transparent else shade(secs, base, none)),
                    )
                }
            }
        }
    }
}

/** 0 / under 15 min / under 30 / under 60 / an hour or more, as on the web heatmap. */
private fun shade(seconds: Long, base: Color, none: Color): Color = when {
    seconds <= 0 -> none
    seconds < 15 * 60 -> base.copy(alpha = 0.3f)
    seconds < 30 * 60 -> base.copy(alpha = 0.5f)
    seconds < 60 * 60 -> base.copy(alpha = 0.75f)
    else -> base
}

private val SOURCE_LABELS = mapOf(
    "bookorbit" to R.string.stats_src_bookorbit,
    "android" to R.string.stats_src_android,
    "ios" to R.string.stats_src_ios,
    "watchos" to R.string.stats_src_watchos,
    "koreader" to R.string.stats_src_koreader,
    "kobo" to R.string.stats_src_kobo,
)

private val SOURCE_COLORS = listOf(Color(0xFF4A9EFF), Color(0xFF4ADE80), Color(0xFFFB923C), Color(0xFFF472B6), Color(0xFFC084FC), Color(0xFF22D3EE))

@Composable
private fun SourceSplit(dist: SourceDistribution, modifier: Modifier) {
    val slices = dist.slices.filter { it.readingSeconds > 0 }.sortedByDescending { it.readingSeconds }
    Column(modifier) {
        Row(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape)) {
            slices.forEachIndexed { i, s ->
                Box(
                    Modifier
                        .weight(s.readingSeconds.toFloat())
                        .height(12.dp)
                        .background(SOURCE_COLORS[i % SOURCE_COLORS.size]),
                )
            }
        }
        slices.forEachIndexed { i, s ->
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(SOURCE_COLORS[i % SOURCE_COLORS.size]))
                Text(
                    SOURCE_LABELS[s.bucket]?.let { stringResource(it) } ?: s.bucket.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp).weight(1f),
                )
                Text(formatSeconds(s.readingSeconds), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(0.dp))
    }
}

@Composable
private fun formatSeconds(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return when {
        h > 0 -> stringResource(R.string.stats_duration_hours, h, m)
        m > 0 -> stringResource(R.string.stats_duration_minutes, m)
        seconds > 0 -> stringResource(R.string.stats_duration_under_minute)
        else -> stringResource(R.string.stats_duration_minutes, 0)
    }
}

private fun historyTime(value: String): String = runCatching {
    java.time.format.DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.SHORT)
        .withLocale(java.util.Locale.getDefault()).withZone(java.time.ZoneOffset.UTC)
        .format(java.time.Instant.parse(value)) + " UTC"
}.getOrDefault(value)
