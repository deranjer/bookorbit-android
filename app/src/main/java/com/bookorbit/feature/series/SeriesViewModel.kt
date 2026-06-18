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
import com.bookorbit.feature.browse.BrowseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

private const val LIST_PAGE_SIZE = 100

@HiltViewModel
class SeriesViewModel @Inject constructor(
    private val repo: BrowseRepository,
) : ViewModel() {

    val series: Flow<PagingData<SeriesSummary>> = Pager(
        config = PagingConfig(LIST_PAGE_SIZE, initialLoadSize = LIST_PAGE_SIZE, enablePlaceholders = false),
    ) {
        SeriesPagingSource(LIST_PAGE_SIZE) { page, size ->
            val p = repo.series(page, size)
            Triple(p.items, p.total, p.size)
        }
    }.flow.cachedIn(viewModelScope)

    fun seriesBooks(name: String): Flow<PagingData<BookCard>> = Pager(
        config = PagingConfig(LIST_PAGE_SIZE, initialLoadSize = LIST_PAGE_SIZE, enablePlaceholders = false),
    ) {
        BookPagingSource(LIST_PAGE_SIZE) { page, size ->
            val p = repo.seriesBooks(name, page, size)
            Triple(p.items, p.total, p.size)
        }
    }.flow
}
