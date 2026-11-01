package com.bookorbit.feature.collections

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CollectionsBookmark
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
fun CollectionsScreen(
    onBookClick: (Int) -> Unit,
    tabs: @Composable () -> Unit,
    vm: CollectionsViewModel = hiltViewModel(),
) {
    val collections by vm.collections.collectAsStateWithLifecycle()
    val selectedId by vm.selectedId.collectAsStateWithLifecycle()
    val books = vm.books.collectAsLazyPagingItems()

    PickedBooksContent(
        title = stringResource(R.string.lib_chip_collections),
        eyebrow = stringResource(R.string.title_library),
        tabs = tabs,
        chips = collections.map { ChipItem(it.id, it.name, it.bookCount) },
        selectedId = selectedId,
        onSelect = vm::select,
        books = books,
        onBookClick = onBookClick,
        emptyIcon = Icons.Outlined.CollectionsBookmark,
        emptyTitle = stringResource(R.string.no_collections),
        emptyBody = stringResource(R.string.create_collections_in_the_web),
        emptyBooksText = stringResource(R.string.no_books_in_this_collection),
    )
}
