package com.bookorbit.feature.collections

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
fun CollectionsScreen(
    onBookClick: (Int) -> Unit,
    vm: CollectionsViewModel = hiltViewModel(),
) {
    val collections by vm.collections.collectAsStateWithLifecycle()
    val selectedId by vm.selectedId.collectAsStateWithLifecycle()
    val books = vm.books.collectAsLazyPagingItems()

    ChipBooksScreen(
        chips = collections.map { ChipItem(it.id, it.name, it.bookCount) },
        selectedId = selectedId,
        onSelect = vm::select,
        books = books,
        onBookClick = onBookClick,
        emptyTitle = stringResource(R.string.no_collections),
        emptyBody = stringResource(R.string.create_collections_in_the_web),
        emptyBooksText = stringResource(R.string.no_books_in_this_collection),
    )
}
