package com.bookorbit.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.bookorbit.core.model.BookCard
import com.bookorbit.core.model.Library
import com.bookorbit.core.paging.BookPagingSource
import com.bookorbit.core.paging.DEFAULT_PAGE_SIZE
import com.bookorbit.feature.browse.BrowseRepository
import com.bookorbit.feature.library.filters.CatalogKind
import com.bookorbit.feature.library.filters.LibraryFilters
import com.bookorbit.feature.library.filters.LibrarySort
import com.bookorbit.feature.library.filters.StoredFilterPrefs
import com.bookorbit.feature.library.filters.FilterPrefsStore
import com.bookorbit.feature.library.filters.buildBookQuery
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibrariesViewModel @Inject constructor(
    private val repo: BrowseRepository,
    private val prefsStore: FilterPrefsStore,
) : ViewModel() {

    private val _libraries = MutableStateFlow<List<Library>>(emptyList())
    val libraries = _libraries.asStateFlow()

    private val _selectedId = MutableStateFlow<Int?>(null)
    val selectedId = _selectedId.asStateFlow()

    private val _filters = MutableStateFlow(LibraryFilters())
    val filters = _filters.asStateFlow()

    private val _sort = MutableStateFlow(LibrarySort())
    val sort = _sort.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val books: Flow<PagingData<BookCard>> =
        combine(_selectedId, _filters, _sort) { id, filters, sort -> Triple(id, filters, sort) }
            .flatMapLatest { (id, filters, sort) ->
                if (id == null) emptyFlow() else pagerFor(id, filters, sort)
            }
            .cachedIn(viewModelScope)

    init {
        loadLibraries()
    }

    fun loadLibraries() {
        viewModelScope.launch {
            val libs = runCatching { repo.libraries() }.getOrDefault(emptyList())
            _libraries.value = libs
            if (_selectedId.value == null) {
                libs.firstOrNull()?.id?.let { selectLibrary(it) }
            }
        }
    }

    fun selectLibrary(id: Int) {
        _selectedId.value = id
        viewModelScope.launch {
            val prefs = prefsStore.load(id)
            _filters.value = prefs.filters
            _sort.value = prefs.sort
        }
    }

    fun applyFilters(filters: LibraryFilters, sort: LibrarySort) {
        _filters.value = filters
        _sort.value = sort
        _selectedId.value?.let { id ->
            viewModelScope.launch { prefsStore.save(id, StoredFilterPrefs(filters, sort)) }
        }
    }

    suspend fun searchCatalog(kind: CatalogKind, query: String): List<String> =
        repo.searchCatalog(kind.path, query)

    private fun pagerFor(libraryId: Int, filters: LibraryFilters, sort: LibrarySort): Flow<PagingData<BookCard>> =
        Pager(
            config = PagingConfig(DEFAULT_PAGE_SIZE, initialLoadSize = DEFAULT_PAGE_SIZE, enablePlaceholders = false),
        ) {
            BookPagingSource(DEFAULT_PAGE_SIZE) { page, size ->
                val query = buildBookQuery(filters, sort, page, size)
                val result = repo.libraryBooks(libraryId, query)
                Triple(result.items, result.total, result.size)
            }
        }.flow
}
