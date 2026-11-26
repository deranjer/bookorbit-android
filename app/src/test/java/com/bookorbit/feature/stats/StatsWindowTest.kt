package com.bookorbit.feature.stats

import com.bookorbit.core.model.UserDailyReading
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class StatsWindowTest {
    @Test fun `daily dates are exact account day keys not device dates`() {
        val today = LocalDate.parse("2026-01-01")
        val rows = listOf(UserDailyReading("2025-12-25", 40), UserDailyReading("2025-12-26", 10),
            UserDailyReading("2026-01-01", 20), UserDailyReading("2026-01-02", 100))
        val current = statsDays(rows, today, 7)
        assertEquals(7, current.size)
        assertEquals(30L, current.values.sum())
        assertEquals(40L, statsDays(rows, today.minusDays(7), 7).values.sum())
        assertEquals(0L, current[LocalDate.parse("2025-12-27")])
    }

    @Test fun `ISO week across calendar year uses week based year`() {
        val monday = historyMonday(LocalDate.parse("2021-01-01"))
        assertEquals(LocalDate.parse("2020-12-28"), monday)
        assertEquals(2020, monday.isoYear)
        assertEquals(53, monday.isoWeek)
        assertEquals(1, monday.plusWeeks(1).isoWeek)
    }

    @Test fun `timezone validation does not silently replace unknown zone with phone timezone`() {
        assertNull(statsZone("Not/AZone"))
        assertNull(statsZone(null))
        assertEquals("Pacific/Kiritimati", statsZone("Pacific/Kiritimati")!!.id)
    }

    @Test fun `duplicate and malformed days cannot become invented totals`() {
        val today = LocalDate.parse("2026-01-01")
        for (rows in listOf(listOf(UserDailyReading("2026-01-01", -1)),
            listOf(UserDailyReading("2026-01-01", 1), UserDailyReading("2026-01-01", 2)),
            listOf(UserDailyReading("invalid", 1)))) {
            assertTrue(runCatching { statsDays(rows, today, 7) }.isFailure)
        }
    }
}
