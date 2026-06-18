package com.bookorbit.feature.authors

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.bookorbit.core.model.AuthorSummary
import com.bookorbit.core.model.BookCard
import com.bookorbit.core.paging.AuthorsPagingSource
import com.bookorbit.core.paging.BookPagingSource
import com.bookorbit.feature.browse.BrowseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

private const val LIST_PAGE_SIZE = 100

@HiltViewModel
class AuthorsViewModel @Inject constructor(
    private val repo: BrowseRepository,
) : ViewModel() {

    val authors: Flow<PagingData<AuthorSummary>> = Pager(
        config = PagingConfig(LIST_PAGE_SIZE, initialLoadSize = LIST_PAGE_SIZE, enablePlaceholders = false),
    ) {
        AuthorsPagingSource(LIST_PAGE_SIZE) { page, size ->
            val p = repo.authors(page, size)
            Triple(p.items, p.total, p.size)
        }
    }.flow.cachedIn(viewModelScope)

    fun authorBooks(authorId: Int): Flow<PagingData<BookCard>> = Pager(
        config = PagingConfig(LIST_PAGE_SIZE, initialLoadSize = LIST_PAGE_SIZE, enablePlaceholders = false),
    ) {
        BookPagingSource(LIST_PAGE_SIZE) { page, size ->
            val p = repo.authorBooks(authorId, page, size)
            Triple(p.items, p.total, p.size)
        }
    }.flow
}
