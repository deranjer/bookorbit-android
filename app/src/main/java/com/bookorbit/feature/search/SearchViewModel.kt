package com.bookorbit.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bookorbit.core.model.SearchResult
import com.bookorbit.feature.browse.BrowseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.combine
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
    val visionScan: com.bookorbit.feature.visionscan.VisionScanRepository,
) : ViewModel() {

    data class UiState(
        val results: List<SearchResult> = emptyList(),
        val loading: Boolean = false,
        val showHint: Boolean = true,
        val error: Boolean = false,
    )

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()
    private val refresh = MutableStateFlow(0)

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val state = _query
        .debounce(300)
        .distinctUntilChanged()
        .combine(refresh) { query, _ -> query }
        .flatMapLatest { q ->
            flow {
                val term = q.trim()
                if (term.length <= 1) {
                    emit(UiState(showHint = true))
                } else {
                    emit(UiState(loading = true, showHint = false))
                    try {
                        emit(UiState(results = repo.search(term, 20), loading = false, showHint = false))
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { emit(UiState(showHint = false, error = true)) }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun onQueryChange(value: String) {
        _query.value = value
    }
    fun retry() { refresh.value++ }
}
