package com.bookorbit.feature.bookdrop

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.bookorbit.core.model.BookDockFile
import com.bookorbit.core.model.BookDockFinalizeResult
import com.bookorbit.core.model.BookDockMetadata
import com.bookorbit.core.model.BookDockSelection
import com.bookorbit.core.model.BookDockSummary
import com.bookorbit.core.model.FinalizeRequest
import com.bookorbit.core.model.Library
import com.bookorbit.core.model.SetTargetRequest
import com.bookorbit.core.model.UpdateBookDockFileRequest
import com.bookorbit.core.paging.BookDockPagingSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val PAGE_SIZE = 30

@HiltViewModel
class BookDropViewModel @Inject constructor(
    private val repo: BookDropRepository,
) : ViewModel() {

    /** Current status filter (`pending`/`ready`/`error`), or null for all. */
    private val _statusFilter = MutableStateFlow<String?>(null)
    val statusFilter = _statusFilter.asStateFlow()

    /** Bumped to force the pager to re-fetch after a mutation. */
    private val refreshKey = MutableStateFlow(0)

    private val _summary = MutableStateFlow(BookDockSummary())
    val summary = _summary.asStateFlow()

    private val _selection = MutableStateFlow(SelectionState())
    val selection = _selection.asStateFlow()

    private val _action = MutableStateFlow(ActionState())
    val action = _action.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val files: Flow<PagingData<BookDockFile>> =
        combine(_statusFilter, refreshKey) { status, _ -> status }
            .flatMapLatest { status -> pagerFor(status) }
            .cachedIn(viewModelScope)

    init {
        loadSummary()
    }

    private fun pagerFor(status: String?): Flow<PagingData<BookDockFile>> =
        Pager(PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false)) {
            BookDockPagingSource(PAGE_SIZE) { page, size ->
                val result = repo.files(status = status, page = page, limit = size, search = null)
                Triple(result.items, result.total, result.size)
            }
        }.flow

    fun loadSummary() {
        viewModelScope.launch {
            runCatching { repo.summary() }.getOrNull()?.let { _summary.value = it }
        }
    }

    /** Re-fetch the list (new pager) and refresh the summary counts. */
    fun refresh() {
        refreshKey.update { it + 1 }
        loadSummary()
    }

    fun setStatusFilter(status: String?) {
        if (_statusFilter.value == status) return
        exitSelection()
        _statusFilter.value = status
    }

    /** Total files matching the active status filter, for "N selected" / select-all UI. */
    fun filteredTotal(): Int = when (_statusFilter.value) {
        "pending" -> _summary.value.pending
        "ready" -> _summary.value.ready
        "error" -> _summary.value.error
        else -> _summary.value.total
    }

    // --- Selection -----------------------------------------------------------

    data class SelectionState(
        val active: Boolean = false,
        val selectAll: Boolean = false,
        val ids: Set<Int> = emptySet(),
        val excluded: Set<Int> = emptySet(),
    ) {
        fun isSelected(id: Int): Boolean = if (selectAll) id !in excluded else id in ids
    }

    fun startSelection(id: Int) {
        _selection.value = SelectionState(active = true, ids = setOf(id))
    }

    fun toggleItem(id: Int) {
        _selection.update { s ->
            val next = if (s.selectAll) {
                if (id in s.excluded) s.copy(excluded = s.excluded - id) else s.copy(excluded = s.excluded + id)
            } else {
                if (id in s.ids) s.copy(ids = s.ids - id) else s.copy(ids = s.ids + id)
            }
            next.copy(active = true)
        }
    }

    fun selectAllInFilter() {
        _selection.update { it.copy(active = true, selectAll = true, ids = emptySet(), excluded = emptySet()) }
    }

    fun exitSelection() {
        _selection.value = SelectionState()
    }

    /** Number of files the current selection resolves to. */
    fun selectedCount(): Int {
        val s = _selection.value
        return if (s.selectAll) (filteredTotal() - s.excluded.size).coerceAtLeast(0) else s.ids.size
    }

    private fun currentSelection(): BookDockSelection {
        val s = _selection.value
        return if (s.selectAll) {
            BookDockSelection(
                selectAll = true,
                excludedIds = s.excluded.toList().ifEmpty { null },
                status = _statusFilter.value,
            )
        } else {
            BookDockSelection(fileIds = s.ids.toList(), status = _statusFilter.value)
        }
    }

    // --- Bulk actions --------------------------------------------------------

    fun applyFetchedSelection() = runAction {
        val counts = repo.applyFetched(currentSelection())
        "Applied fetched metadata to ${counts.applied} file(s)"
    }

    fun setTargetSelection(libraryId: Int, folderId: Int) = runAction {
        val s = _selection.value
        repo.setTarget(
            SetTargetRequest(
                fileIds = if (s.selectAll) null else s.ids.toList(),
                selectAll = if (s.selectAll) true else null,
                excludedIds = if (s.selectAll) s.excluded.toList().ifEmpty { null } else null,
                status = _statusFilter.value,
                targetLibraryId = libraryId,
                targetFolderId = folderId,
            ),
        )
        "Destination set"
    }

    fun finalizeSelection(libraryId: Int?, folderId: Int?) = runAction {
        val s = _selection.value
        val result = repo.finalize(
            FinalizeRequest(
                fileIds = if (s.selectAll) null else s.ids.toList(),
                selectAll = if (s.selectAll) true else null,
                excludedIds = if (s.selectAll) s.excluded.toList().ifEmpty { null } else null,
                status = _statusFilter.value,
                defaultLibraryId = libraryId,
                defaultFolderId = folderId,
            ),
        )
        messageFor(result)
    }

    fun discardSelection() = runAction {
        repo.bulkDiscard(currentSelection())
        "Discarded"
    }

    fun upload(uri: Uri) = runAction(exitSelection = false) {
        repo.upload(uri)
        "Uploaded"
    }

    // --- Single-file actions (used by the detail sheet) ----------------------

    suspend fun loadLibraries(): List<Library> =
        runCatching { repo.libraries() }.getOrDefault(emptyList())

    suspend fun saveMetadata(id: Int, metadata: BookDockMetadata): BookDockFile? =
        mutateFile { repo.update(id, UpdateBookDockFileRequest(selectedMetadata = metadata)) }

    suspend fun setDestination(id: Int, libraryId: Int, folderId: Int): BookDockFile? =
        mutateFile {
            repo.update(id, UpdateBookDockFileRequest(targetLibraryId = libraryId, targetFolderId = folderId))
        }

    suspend fun applyFetchedSingle(file: BookDockFile): BookDockFile? {
        val fetched = file.fetchedMetadata ?: return null
        return mutateFile { repo.update(file.id, UpdateBookDockFileRequest(selectedMetadata = fetched)) }
    }

    suspend fun approveSingle(id: Int, libraryId: Int?, folderId: Int?): BookDockFinalizeResult? {
        val result = runCatching {
            repo.finalize(FinalizeRequest(fileIds = listOf(id), defaultLibraryId = libraryId, defaultFolderId = folderId))
        }.getOrNull()
        if (result != null) refresh()
        return result
    }

    suspend fun discardSingle(id: Int) {
        runCatching { repo.discard(id) }
        refresh()
    }

    fun consumeMessage() = _action.update { it.copy(message = null) }
    fun consumeError() = _action.update { it.copy(error = null) }

    // --- Internals -----------------------------------------------------------

    data class ActionState(
        val inProgress: Boolean = false,
        val message: String? = null,
        val error: String? = null,
    )

    private fun runAction(exitSelection: Boolean = true, block: suspend () -> String?) {
        if (_action.value.inProgress) return
        _action.update { it.copy(inProgress = true, error = null) }
        viewModelScope.launch {
            runCatching { block() }.fold(
                onSuccess = { msg ->
                    if (exitSelection) exitSelection()
                    _action.update { it.copy(inProgress = false, message = msg) }
                    refresh()
                },
                onFailure = { e ->
                    _action.update { it.copy(inProgress = false, error = e.message ?: "Something went wrong") }
                },
            )
        }
    }

    private suspend fun mutateFile(block: suspend () -> BookDockFile): BookDockFile? {
        val updated = runCatching { block() }.getOrNull()
        if (updated != null) refresh()
        return updated
    }

    private fun messageFor(result: BookDockFinalizeResult): String {
        val dupes = result.results.count { it.isDuplicate == true }
        return buildString {
            append("Approved ${result.succeeded} of ${result.total}")
            if (result.failed > 0) append(", ${result.failed} failed")
            if (dupes > 0) append(" ($dupes duplicate${if (dupes == 1) "" else "s"})")
        }
    }
}
