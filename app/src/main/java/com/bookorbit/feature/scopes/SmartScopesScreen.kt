package com.bookorbit.feature.scopes

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.bookorbit.R
import com.bookorbit.feature.library.ChipItem
import com.bookorbit.feature.library.PickedBooksContent

@Composable
fun SmartScopesScreen(
    onBookClick: (Int) -> Unit,
    tabs: @Composable () -> Unit,
    vm: SmartScopesViewModel = hiltViewModel(),
) {
    val scopes by vm.scopes.collectAsStateWithLifecycle()
    val selectedId by vm.selectedId.collectAsStateWithLifecycle()
    val books = vm.books.collectAsLazyPagingItems()

    PickedBooksContent(
        title = stringResource(R.string.lib_chip_scopes),
        eyebrow = stringResource(R.string.title_library),
        tabs = tabs,
        chips = scopes.map { ChipItem(it.id, it.name, it.bookCount) },
        selectedId = selectedId,
        onSelect = vm::select,
        books = books,
        onBookClick = onBookClick,
        emptyIcon = Icons.Outlined.FilterAlt,
        emptyTitle = stringResource(R.string.no_smart_scopes),
        emptyBody = stringResource(R.string.create_smart_scopes_in_the),
        emptyBooksText = stringResource(R.string.no_books_match_this_scope),
    )
}
