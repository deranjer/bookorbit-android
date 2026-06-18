package com.bookorbit.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bookorbit.core.model.BookCard
import com.bookorbit.core.model.ScrollerType
import com.bookorbit.feature.browse.BrowseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repo: BrowseRepository,
) : ViewModel() {

    data class UiState(
        val continueReading: List<BookCard> = emptyList(),
        val recentlyAdded: List<BookCard> = emptyList(),
        val loading: Boolean = true,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui = _ui.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _ui.value = _ui.value.copy(loading = true)
        viewModelScope.launch {
            val continueReading = async { runCatching { repo.scroller(ScrollerType.CONTINUE_READING) }.getOrDefault(emptyList()) }
            val recentlyAdded = async { runCatching { repo.scroller(ScrollerType.RECENTLY_ADDED) }.getOrDefault(emptyList()) }
            _ui.value = UiState(continueReading.await(), recentlyAdded.await(), loading = false)
        }
    }
}
