package com.bookorbit.feature.collections

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
        emptyTitle = "No Collections",
        emptyBody = "Create collections in the web app to group books together.",
        emptyBooksText = "No books in this collection.",
    )
}
