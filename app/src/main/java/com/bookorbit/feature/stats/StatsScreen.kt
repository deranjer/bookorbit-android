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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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

/** Your reading at a glance: totals, streak and goal, a 30-day bar chart, a 12-week heatmap, and where you read. */
@Composable
fun StatsScreen(vm: StatsViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()

    when {
        ui.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        ui.failed -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.couldn_t_load_your_reading), color = MaterialTheme.colorScheme.error)
                Button(onClick = vm::refresh, modifier = Modifier.padding(top = 12.dp)) { Text(stringResource(R.string.retry)) }
            }
        }
        else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
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
                val last30 = ui.daily.entries.toList().takeLast(30)
                val total = last30.sumOf { it.value }
                SectionTitle(stringResource(R.string.last_30_days))
                Text(
                    stringResource(R.string.total_a_day, formatSeconds(total), formatSeconds(total / 30)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                BarChart(last30.map { it.value }, Modifier.fillMaxWidth().padding(16.dp).height(110.dp))

                SectionTitle(stringResource(R.string.reading_heatmap))
                Heatmap(ui.daily, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }

            ui.sources?.takeIf { it.totalSeconds > 0 }?.let {
                SectionTitle(stringResource(R.string.where_you_read))
                SourceSplit(it, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }
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

private fun formatSeconds(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m"
        seconds > 0 -> "<1m"
        else -> "0m"
    }
}
