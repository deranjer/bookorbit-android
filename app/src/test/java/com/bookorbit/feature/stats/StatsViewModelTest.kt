package com.bookorbit.feature.stats

import com.bookorbit.core.auth.SessionManager
import com.bookorbit.core.auth.SecureStorage
import com.bookorbit.core.model.*
import com.bookorbit.core.network.ApiService
import com.bookorbit.core.network.ReadingWidgets
import com.bookorbit.core.network.ReadingWidgetsRepository
import com.bookorbit.util.MainDispatcherRule
import io.mockk.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val api = mockk<ApiService>()
    private val widgets = mockk<ReadingWidgetsRepository>()
    private val session = SessionManager(mockk<SecureStorage>(relaxed = true), Json)

    private fun setup() {
        session.setServerUrl("https://books.example")
        session.signIn("token", AuthUser(1, "reader"))
        coEvery { api.getMe() } returns AuthUser(1, "reader", settings = AuthUserSettings("UTC"))
        coEvery { api.getUserStatsSummary() } returns UserStatsSummary(completedBooks = 4)
        coEvery { widgets.load() } returns ReadingWidgets(null, null)
        coEvery { api.getDailyReading(any()) } returns emptyList()
        coEvery { api.getReadingSources(any()) } returns SourceDistribution()
        coEvery { api.getReadingHistory(any(), any()) } answers {
            val monday = historyMonday()
            ReadingHistory(firstArg(), secondArg(), monday.toString(), monday.plusWeeks(1).toString())
        }
    }

    @Test fun `history failure does not erase working sections and period uses bounded buffer`() = runTest {
        setup()
        coEvery { api.getReadingHistory(any(), any()) } throws IllegalStateException("unavailable")
        val vm = StatsViewModel(api, widgets, session)
        advanceUntilIdle()
        assertFalse(vm.ui.value.failed)
        assertTrue(vm.ui.value.historyFailed)
        assertNotNull(vm.ui.value.summary)
        vm.selectPeriod(365)
        advanceUntilIdle()
        assertEquals(365, vm.ui.value.daily.size)
        coVerify { api.getDailyReading(732) }
        coVerify { api.getReadingSources(365) }
    }

    @Test fun `period change cancels old request and cannot publish old totals`() = runTest {
        setup()
        val pending = CompletableDeferred<List<UserDailyReading>>()
        coEvery { api.getDailyReading(62) } coAnswers { pending.await() }
        val vm = StatsViewModel(api, widgets, session)
        runCurrent()
        vm.selectPeriod(7)
        advanceUntilIdle()
        pending.complete(listOf(UserDailyReading(java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString(), 999)))
        advanceUntilIdle()
        assertEquals(7, vm.ui.value.daily.size)
        assertEquals(0L, vm.ui.value.daily.values.sum())
    }

    @Test fun `sign out clears private statistics and pending response cannot restore them`() = runTest {
        setup()
        val pending = CompletableDeferred<List<UserDailyReading>>()
        coEvery { api.getDailyReading(any()) } coAnswers { pending.await() }
        val vm = StatsViewModel(api, widgets, session)
        runCurrent()
        session.signOut()
        advanceUntilIdle()
        pending.complete(emptyList())
        advanceUntilIdle()
        assertNull(vm.ui.value.summary)
        assertNull(vm.ui.value.history)
        assertTrue(vm.ui.value.daily.isEmpty())
    }

    @Test fun `missing profile is not presented as zero activity`() = runTest {
        setup()
        coEvery { api.getMe() } throws IllegalStateException("offline")
        val vm = StatsViewModel(api, widgets, session)
        advanceUntilIdle()
        assertTrue(vm.ui.value.partial)
        assertTrue(vm.ui.value.daily.isEmpty())
        assertNull(vm.ui.value.previousSeconds)
        assertNotNull(vm.ui.value.history)
    }

    @Test fun `duplicate history ids are unavailable rather than duplicate list keys`() = runTest {
        setup()
        val week = historyMonday()
        val row = ReadingHistoryItem(1, 5, "Book", "M4B", "android", "2026-11-23T00:00:00Z", "2026-11-23T00:01:00Z", 60)
        coEvery { api.getReadingHistory(any(), any()) } returns ReadingHistory(week.isoYear, week.isoWeek, week.toString(), week.plusWeeks(1).toString(), listOf(row, row))
        val vm = StatsViewModel(api, widgets, session)
        advanceUntilIdle()
        assertTrue(vm.ui.value.historyFailed)
        assertNull(vm.ui.value.history)
        assertFalse(vm.ui.value.failed)
    }

    @Test fun `future history weeks cannot be requested`() = runTest {
        setup()
        val vm = StatsViewModel(api, widgets, session)
        advanceUntilIdle()
        vm.moveWeek(1)
        advanceUntilIdle()
        assertEquals(historyMonday(), vm.ui.value.week)
        coVerify(exactly = 1) { api.getReadingHistory(any(), any()) }
    }

    @Test fun `same user relogin still refreshes the ownership lease`() = runTest {
        setup()
        val vm = StatsViewModel(api, widgets, session)
        advanceUntilIdle()
        val before = session.owners.value
        session.signIn("replacement", AuthUser(1, "reader"))
        advanceUntilIdle()
        assertNotEquals(before, session.owners.value)
        coVerify(exactly = 2) { api.getUserStatsSummary() }
    }

    @Test fun `server replacement clears old account totals before new response`() = runTest {
        setup()
        val vm = StatsViewModel(api, widgets, session)
        advanceUntilIdle()
        assertNotNull(vm.ui.value.summary)
        val pending = CompletableDeferred<UserStatsSummary>()
        coEvery { api.getUserStatsSummary() } coAnswers { pending.await() }
        session.setServerUrl("https://other.example")
        runCurrent()
        assertNull(vm.ui.value.summary)
        pending.complete(UserStatsSummary(completedBooks = 2))
        advanceUntilIdle()
        assertEquals(2, vm.ui.value.summary!!.completedBooks)
    }
}
