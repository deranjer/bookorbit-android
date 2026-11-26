package com.bookorbit.feature.search

import com.bookorbit.feature.browse.BrowseRepository
import com.bookorbit.feature.visionscan.VisionScanRepository
import com.bookorbit.util.MainDispatcherRule
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test fun `network failure is not an empty successful search and retry keeps exact query`() = runTest {
        val repo = mockk<BrowseRepository>()
        val vision = mockk<VisionScanRepository>()
        coEvery { repo.search("Орбита", 20) } throws java.io.IOException("offline")
        val vm = SearchViewModel(repo, vision)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect() }
        vm.onQueryChange("  Орбита  ")
        advanceTimeBy(301); runCurrent()
        assertTrue(vm.state.value.error)
        coEvery { repo.search("Орбита", 20) } returns emptyList()
        vm.retry(); runCurrent()
        assertFalse(vm.state.value.error)
        assertFalse(vm.state.value.showHint)
        coVerify(exactly = 2) { repo.search("Орбита", 20) }
        coVerify(exactly = 0) { vision.scan(any(), any()) }
    }

    @Test fun `clearing query returns search root without network or photo requests`() = runTest {
        val repo = mockk<BrowseRepository>()
        val vision = mockk<VisionScanRepository>()
        coEvery { repo.search(any(), any()) } returns emptyList()
        val vm = SearchViewModel(repo, vision)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect() }
        vm.onQueryChange("Orbit"); advanceTimeBy(301); runCurrent()
        vm.onQueryChange(""); advanceTimeBy(301); runCurrent()
        assertTrue(vm.state.value.showHint)
        coVerify(exactly = 1) { repo.search(any(), any()) }
        coVerify(exactly = 0) { vision.access() }
    }
}
