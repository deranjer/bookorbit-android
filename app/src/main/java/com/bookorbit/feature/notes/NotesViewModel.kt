package com.bookorbit.feature.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bookorbit.core.model.HubAnnotation
import com.bookorbit.core.network.ApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val PAGE_SIZE = 30

/** Library-wide highlights and notes (the web's Annotations hub), newest first, loaded a page at a time. */
@HiltViewModel
class NotesViewModel @Inject constructor(
    private val api: ApiService,
) : ViewModel() {

    data class UiState(
        val items: List<HubAnnotation> = emptyList(),
        val total: Int = 0,
        val query: String = "",
        val onlyWithNotes: Boolean = false,
        val loading: Boolean = true,
        val loadingMore: Boolean = false,
        val error: Boolean = false,
    ) {
        val canLoadMore: Boolean get() = items.size < total
    }

    private val _ui = MutableStateFlow(UiState())
    val ui = _ui.asStateFlow()

    private var page = 1
    private var job: Job? = null

    fun setQuery(query: String) {
        _ui.update { it.copy(query = query) }
        // Debounce typing so each keystroke isn't a request.
        job?.cancel()
        job = viewModelScope.launch {
            delay(350)
            reload()
        }
    }

    fun setOnlyWithNotes(value: Boolean) {
        _ui.update { it.copy(onlyWithNotes = value) }
        reload()
    }

    /**
     * Called whenever the tab is shown, so highlights made since the last visit appear. Keeps the
     * current list on screen (no spinner) while it refetches.
     */
    fun refresh() = reload(showSpinner = _ui.value.items.isEmpty())

    fun reload(showSpinner: Boolean = true) {
        job?.cancel()
        page = 1
        _ui.update { it.copy(loading = showSpinner, error = false) }
        job = viewModelScope.launch { fetch(replace = true) }
    }

    fun loadMore() {
        val s = _ui.value
        if (s.loading || s.loadingMore || !s.canLoadMore) return
        _ui.update { it.copy(loadingMore = true) }
        job = viewModelScope.launch { fetch(replace = false) }
    }

    private suspend fun fetch(replace: Boolean) {
        val s = _ui.value
        runCatching {
            api.getAnnotationHub(
                page = if (replace) 1 else page + 1,
                pageSize = PAGE_SIZE,
                search = s.query.trim().ifBlank { null },
                hasNote = if (s.onlyWithNotes) true else null,
            )
        }.onSuccess { result ->
            page = result.page
            _ui.update {
                it.copy(
                    items = if (replace) result.items else it.items + result.items,
                    total = result.total,
                    loading = false,
                    loadingMore = false,
                    error = false,
                )
            }
        }.onFailure {
            _ui.update { it.copy(loading = false, loadingMore = false, error = replace) }
        }
    }
}
