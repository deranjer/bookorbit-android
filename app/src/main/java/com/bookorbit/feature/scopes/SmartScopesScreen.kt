package com.bookorbit.feature.scopes

import androidx.compose.ui.res.stringResource
import com.bookorbit.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.bookorbit.ui.components.ChipBooksScreen
import com.bookorbit.ui.components.ChipItem

@Composable
fun SmartScopesScreen(
    onBookClick: (Int) -> Unit,
    vm: SmartScopesViewModel = hiltViewModel(),
) {
    val scopes by vm.scopes.collectAsStateWithLifecycle()
    val selectedId by vm.selectedId.collectAsStateWithLifecycle()
    val books = vm.books.collectAsLazyPagingItems()

    ChipBooksScreen(
        chips = scopes.map { ChipItem(it.id, it.name, it.bookCount) },
        selectedId = selectedId,
        onSelect = vm::select,
        books = books,
        onBookClick = onBookClick,
        emptyTitle = stringResource(R.string.no_smart_scopes),
        emptyBody = stringResource(R.string.create_smart_scopes_in_the),
        emptyBooksText = stringResource(R.string.no_books_match_this_scope),
    )
}
