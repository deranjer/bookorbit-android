package com.bookorbit.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.bookorbit.R
import com.bookorbit.core.model.BookCard
import com.bookorbit.core.model.Library
import com.bookorbit.feature.library.filters.countActiveFilters
import com.bookorbit.feature.main.LibrarySection
import com.bookorbit.ui.components.BookGrid
import com.bookorbit.ui.components.EmptyState
import com.bookorbit.ui.components.ErrorState
import com.bookorbit.ui.components.PageHeader

@Composable
fun LibrariesScreen(
    onBookClick: (Int) -> Unit,
    tabs: @Composable () -> Unit,
    vm: LibrariesViewModel = hiltViewModel(),
) {
    val libraries by vm.libraries.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val selectedId by vm.selectedId.collectAsStateWithLifecycle()
    val filters by vm.filters.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val books = vm.books.collectAsLazyPagingItems()
    var sheetOpen by remember { mutableStateOf(false) }

    LibrariesContent(
        libraries = libraries,
        status = status,
        selectedId = selectedId,
        activeFilterCount = countActiveFilters(filters),
        books = books,
        tabs = tabs,
        onSelectLibrary = vm::selectLibrary,
        onOpenFilters = { sheetOpen = true },
        onRetry = vm::loadLibraries,
        onBookClick = onBookClick,
    )

    if (sheetOpen) {
        FilterSortSheet(
            initialFilters = filters,
            initialSort = sort,
            searchCatalog = { kind, q -> vm.searchCatalog(kind, q) },
            onApply = { f, s ->
                vm.applyFilters(f, s)
                sheetOpen = false
            },
            onDismiss = { sheetOpen = false },
        )
    }
}

/**
 * The Books section: the library's name as the page title (a switcher when there are several), its
 * book count, the filter button, and the cover grid.
 */
@Composable
internal fun LibrariesContent(
    libraries: List<Library>,
    status: LibrariesViewModel.Status,
    selectedId: Int?,
    activeFilterCount: Int,
    books: LazyPagingItems<BookCard>,
    tabs: @Composable () -> Unit,
    onSelectLibrary: (Int) -> Unit,
    onOpenFilters: () -> Unit,
    onRetry: () -> Unit,
    onBookClick: (Int) -> Unit,
) {
    val selected = libraries.firstOrNull { it.id == selectedId }
    var pickerOpen by remember { mutableStateOf(false) }

    LibrarySection(
        header = {
            PageHeader(
                title = selected?.name ?: stringResource(R.string.lib_default_title),
                eyebrow = stringResource(R.string.title_library),
                subtitle = selected?.bookCount?.let { pluralStringResource(R.plurals.lib_book_count, it, it) },
                onTitleClick = if (libraries.size > 1) ({ pickerOpen = true }) else null,
                titleMenu = {
                    DropdownMenu(expanded = pickerOpen, onDismissRequest = { pickerOpen = false }) {
                        libraries.forEach { lib ->
                            DropdownMenuItem(
                                text = { Text(lib.name) },
                                onClick = {
                                    onSelectLibrary(lib.id)
                                    pickerOpen = false
                                },
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onOpenFilters, enabled = selectedId != null) {
                        BadgedBox(badge = { if (activeFilterCount > 0) Badge { Text("$activeFilterCount") } }) {
                            Icon(Icons.Outlined.Tune, contentDescription = stringResource(R.string.filter_and_sort))
                        }
                    }
                },
            )
        },
        tabs = tabs,
    ) {
        when (status) {
            LibrariesViewModel.Status.ERROR -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                ErrorState(
                    stringResource(R.string.couldn_t_load_your_libraries),
                    onRetry = onRetry,
                    message = stringResource(R.string.check_your_connection_books_you),
                )
            }
            LibrariesViewModel.Status.EMPTY -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                EmptyState(Icons.Outlined.LibraryBooks, stringResource(R.string.you_don_t_have_access))
            }
            else -> BookGrid(
                items = books,
                onBookClick = onBookClick,
                emptyText = stringResource(R.string.no_books_in_this_library),
            )
        }
    }
}
