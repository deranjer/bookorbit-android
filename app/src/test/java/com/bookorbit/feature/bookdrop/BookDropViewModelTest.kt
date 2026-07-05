package com.bookorbit.feature.bookdrop

import com.bookorbit.core.model.BookDockBulkCounts
import com.bookorbit.core.model.BookDockFile
import com.bookorbit.core.model.BookDockFinalizeResult
import com.bookorbit.core.model.BookDockMetadata
import com.bookorbit.core.model.BookDockSelection
import com.bookorbit.core.model.BookDockSummary
import com.bookorbit.core.model.FinalizeRequest
import com.bookorbit.core.model.SetTargetRequest
import com.bookorbit.core.model.UpdateBookDockFileRequest
import com.bookorbit.util.MainDispatcherRule
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BookDropViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repo: BookDropRepository
    private lateinit var vm: BookDropViewModel

    @Before
    fun setUp() {
        repo = mockk(relaxed = false)
        coEvery { repo.summary() } returns BookDockSummary(pending = 4, ready = 5, error = 1, total = 10)
        vm = BookDropViewModel(repo)
    }

    @Test
    fun `init loads summary`() {
        assertEquals(10, vm.summary.value.total)
        assertEquals(10, vm.filteredTotal())
    }

    @Test
    fun `filteredTotal follows the status filter`() {
        vm.setStatusFilter("ready")
        assertEquals(5, vm.filteredTotal())
        vm.setStatusFilter("error")
        assertEquals(1, vm.filteredTotal())
    }

    @Test
    fun `toggleItem builds and clears an explicit id selection`() {
        vm.startSelection(1)
        vm.toggleItem(2)
        assertTrue(vm.selection.value.active)
        assertEquals(setOf(1, 2), vm.selection.value.ids)
        assertEquals(2, vm.selectedCount())

        vm.toggleItem(1)
        assertEquals(setOf(2), vm.selection.value.ids)
        assertEquals(1, vm.selectedCount())

        vm.exitSelection()
        assertFalse(vm.selection.value.active)
        assertEquals(0, vm.selectedCount())
    }

    @Test
    fun `selectAll counts the filtered total minus exclusions`() {
        vm.selectAllInFilter()
        assertEquals(10, vm.selectedCount())
        vm.toggleItem(7) // excludes 7 while in selectAll mode
        assertTrue(vm.selection.value.selectAll)
        assertEquals(setOf(7), vm.selection.value.excluded)
        assertEquals(9, vm.selectedCount())
    }

    @Test
    fun `applyFetchedSelection sends the explicit id selection`() = runTest {
        val slot = slot<BookDockSelection>()
        coEvery { repo.applyFetched(capture(slot)) } returns BookDockBulkCounts(total = 2, applied = 2)

        vm.startSelection(3)
        vm.toggleItem(4)
        vm.applyFetchedSelection()

        assertEquals(listOf(3, 4), slot.captured.fileIds)
        assertNull(slot.captured.selectAll)
        // selection is cleared and a result message is surfaced
        assertFalse(vm.selection.value.active)
        assertTrue(vm.action.value.message!!.contains("2"))
    }

    @Test
    fun `setTargetSelection forwards destination and selectAll exclusions`() = runTest {
        val slot = slot<SetTargetRequest>()
        coEvery { repo.setTarget(capture(slot)) } returns BookDockBulkCounts(total = 9, updated = 9)

        vm.selectAllInFilter()
        vm.toggleItem(7)
        vm.setTargetSelection(libraryId = 2, folderId = 8)

        val req = slot.captured
        assertEquals(true, req.selectAll)
        assertEquals(listOf(7), req.excludedIds)
        assertNull(req.fileIds)
        assertEquals(2, req.targetLibraryId)
        assertEquals(8, req.targetFolderId)
    }

    @Test
    fun `finalizeSelection passes defaults through`() = runTest {
        val slot = slot<FinalizeRequest>()
        coEvery { repo.finalize(capture(slot)) } returns
            BookDockFinalizeResult(total = 2, succeeded = 2, failed = 0)

        vm.startSelection(1)
        vm.toggleItem(2)
        vm.finalizeSelection(libraryId = 3, folderId = 9)

        val req = slot.captured
        assertEquals(listOf(1, 2), req.fileIds)
        assertEquals(3, req.defaultLibraryId)
        assertEquals(9, req.defaultFolderId)
        assertTrue(vm.action.value.message!!.contains("Approved 2 of 2"))
    }

    @Test
    fun `saveMetadata patches selectedMetadata only`() = runTest {
        val slot = slot<UpdateBookDockFileRequest>()
        coEvery { repo.update(eq(5), capture(slot)) } returns sampleFile(5)

        val result = vm.saveMetadata(5, BookDockMetadata(title = "Edited"))

        assertEquals("Edited", slot.captured.selectedMetadata?.title)
        assertNull(slot.captured.targetLibraryId)
        assertEquals(5, result?.id)
    }

    @Test
    fun `approveSingle finalizes just that file`() = runTest {
        val slot = slot<FinalizeRequest>()
        coEvery { repo.finalize(capture(slot)) } returns
            BookDockFinalizeResult(total = 1, succeeded = 1, failed = 0)

        vm.approveSingle(id = 6, libraryId = 1, folderId = 2)

        assertEquals(listOf(6), slot.captured.fileIds)
        assertEquals(1, slot.captured.defaultLibraryId)
        assertEquals(2, slot.captured.defaultFolderId)
    }

    @Test
    fun `discardSelection routes through the bulk endpoint`() = runTest {
        coEvery { repo.bulkDiscard(any()) } just Runs

        vm.startSelection(1)
        vm.discardSelection()

        coVerify { repo.bulkDiscard(any()) }
        assertFalse(vm.selection.value.active)
    }

    @Test
    fun `a failing action surfaces an error and keeps the selection`() = runTest {
        coEvery { repo.applyFetched(any()) } throws RuntimeException("boom")

        vm.startSelection(1)
        vm.applyFetchedSelection()

        assertEquals("boom", vm.action.value.error)
        assertTrue(vm.selection.value.active)
    }

    private fun sampleFile(id: Int) = BookDockFile(
        id = id,
        fileName = "book.epub",
        status = "ready",
        createdAt = "2026-06-21T00:00:00Z",
        updatedAt = "2026-06-21T00:00:00Z",
    )
}
