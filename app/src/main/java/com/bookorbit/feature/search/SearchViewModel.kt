package com.bookorbit.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bookorbit.core.model.SearchResult
import com.bookorbit.feature.browse.BrowseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repo: BrowseRepository,
) : ViewModel() {

    data class UiState(
        val results: List<SearchResult> = emptyList(),
        val loading: Boolean = false,
        val showHint: Boolean = true,
    )

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val state = _query
        .debounce(300)
        .distinctUntilChanged()
        .flatMapLatest { q ->
            flow {
                val term = q.trim()
                if (term.length <= 1) {
                    emit(UiState(showHint = true))
                } else {
                    emit(UiState(loading = true, showHint = false))
                    val results = runCatching { repo.search(term, 20) }.getOrDefault(emptyList())
                    emit(UiState(results = results, loading = false, showHint = false))
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun onQueryChange(value: String) {
        _query.value = value
    }
}
