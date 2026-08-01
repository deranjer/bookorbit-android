package com.bookorbit.feature.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.bookorbit.core.model.BookCard
import com.bookorbit.core.model.SeriesSummary
import com.bookorbit.core.paging.BookPagingSource
import com.bookorbit.core.paging.SeriesPagingSource
import com.bookorbit.core.settings.AppSettingsStore
import com.bookorbit.core.settings.SeriesViewMode
import com.bookorbit.feature.browse.BrowseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val LIST_PAGE_SIZE = 100

@HiltViewModel
class SeriesViewModel @Inject constructor(
    private val repo: BrowseRepository,
    private val appSettings: AppSettingsStore,
) : ViewModel() {

    val viewMode: StateFlow<SeriesViewMode> = appSettings.seriesViewMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SeriesViewMode.LIST)

    fun setViewMode(mode: SeriesViewMode) {
        viewModelScope.launch { appSettings.setSeriesViewMode(mode) }
    }

    val series: Flow<PagingData<SeriesSummary>> = Pager(
        config = PagingConfig(LIST_PAGE_SIZE, initialLoadSize = LIST_PAGE_SIZE, enablePlaceholders = false),
    ) {
        SeriesPagingSource(LIST_PAGE_SIZE) { page, size ->
            val p = repo.series(page, size)
            Triple(p.items, p.total, p.size)
        }
    }.flow.cachedIn(viewModelScope)

    fun seriesBooks(seriesId: Int): Flow<PagingData<BookCard>> = Pager(
        config = PagingConfig(LIST_PAGE_SIZE, initialLoadSize = LIST_PAGE_SIZE, enablePlaceholders = false),
    ) {
        BookPagingSource(LIST_PAGE_SIZE) { page, size ->
            val p = repo.seriesBooks(seriesId, page, size)
            Triple(p.items, p.total, p.size)
        }
    }.flow
}
