package com.bookorbit.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import com.bookorbit.core.model.BookCard
import com.bookorbit.feature.main.LibrarySection
import com.bookorbit.ui.components.BookGrid
import com.bookorbit.ui.components.ChoiceChip
import com.bookorbit.ui.components.EmptyState
import com.bookorbit.ui.components.PageHeader

data class ChipItem(val id: Int, val label: String, val count: Int?)

/**
 * A Library section that is a set of named lists (collections, smart scopes): the lists as chips
 * with their counts, and the chosen one's books as a grid below.
 */
@Composable
fun PickedBooksContent(
    title: String,
    eyebrow: String,
    tabs: @Composable () -> Unit,
    chips: List<ChipItem>,
    selectedId: Int?,
    onSelect: (Int) -> Unit,
    books: LazyPagingItems<BookCard>,
    onBookClick: (Int) -> Unit,
    emptyIcon: ImageVector,
    emptyTitle: String,
    emptyBody: String,
    emptyBooksText: String,
) {
    LibrarySection(header = { PageHeader(title = title, eyebrow = eyebrow) }, tabs = tabs) {
        if (chips.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { EmptyState(emptyIcon, emptyTitle, message = emptyBody) }
            return@LibrarySection
        }
        Column(Modifier.fillMaxSize()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(chips, key = { it.id }) { chip ->
                    ChoiceChip(
                        label = if (chip.count != null) "${chip.label} · ${chip.count}" else chip.label,
                        selected = chip.id == selectedId,
                        onClick = { onSelect(chip.id) },
                    )
                }
            }
            Box(Modifier.weight(1f)) {
                BookGrid(items = books, onBookClick = onBookClick, emptyText = emptyBooksText)
            }
        }
    }
}
