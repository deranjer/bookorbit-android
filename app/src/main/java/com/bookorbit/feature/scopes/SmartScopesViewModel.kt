package com.bookorbit.feature.scopes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.bookorbit.core.model.BookCard
import com.bookorbit.core.model.SmartScope
import com.bookorbit.core.paging.BookPagingSource
import com.bookorbit.core.paging.DEFAULT_PAGE_SIZE
import com.bookorbit.feature.browse.BrowseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SmartScopesViewModel @Inject constructor(
    private val repo: BrowseRepository,
) : ViewModel() {

    private val _scopes = MutableStateFlow<List<SmartScope>>(emptyList())
    val scopes = _scopes.asStateFlow()

    private val _selectedId = MutableStateFlow<Int?>(null)
    val selectedId = _selectedId.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val books: Flow<PagingData<BookCard>> = _selectedId
        .flatMapLatest { id -> if (id == null) emptyFlow() else pagerFor(id) }
        .cachedIn(viewModelScope)

    init {
        viewModelScope.launch {
            val list = runCatching { repo.smartScopes() }.getOrDefault(emptyList())
            _scopes.value = list
            if (_selectedId.value == null) _selectedId.value = list.firstOrNull()?.id
        }
    }

    fun select(id: Int) {
        _selectedId.value = id
    }

    private fun pagerFor(scopeId: Int): Flow<PagingData<BookCard>> = Pager(
        config = PagingConfig(DEFAULT_PAGE_SIZE, initialLoadSize = DEFAULT_PAGE_SIZE, enablePlaceholders = false),
    ) {
        BookPagingSource(DEFAULT_PAGE_SIZE) { page, size ->
            val p = repo.smartScopeBooks(scopeId, page, size)
            Triple(p.items, p.total, p.size)
        }
    }.flow
}
