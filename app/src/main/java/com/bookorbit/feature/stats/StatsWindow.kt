package com.bookorbit.feature.stats

import com.bookorbit.core.model.UserDailyReading
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek
import java.time.temporal.IsoFields

internal val STATS_PERIODS = listOf(7, 30, 90, 365)

internal fun statsZone(value: String?): ZoneId? = value?.let { runCatching { ZoneId.of(it) }.getOrNull() }

internal fun statsDays(rows: List<UserDailyReading>, today: LocalDate, days: Int): Map<LocalDate, Long> {
    require(days in STATS_PERIODS)
    require(rows.all { it.readingSeconds >= 0 })
    val byDay = rows.associate { LocalDate.parse(it.day) to it.readingSeconds }
    require(byDay.size == rows.size)
    return (days - 1 downTo 0).associate { back ->
        val day = today.minusDays(back.toLong())
        day to (byDay[day] ?: 0L)
    }
}

/** History routes use UTC ISO weeks, independent of account daily-stat timezone. */
internal fun historyMonday(today: LocalDate = LocalDate.now(ZoneOffset.UTC)): LocalDate =
    today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

internal val LocalDate.isoYear: Int get() = get(IsoFields.WEEK_BASED_YEAR)
internal val LocalDate.isoWeek: Int get() = get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
