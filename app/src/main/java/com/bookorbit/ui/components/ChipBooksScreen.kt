package com.bookorbit.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import com.bookorbit.core.model.BookCard

data class ChipItem(val id: Int, val label: String, val count: Int?)

/**
 * Generic "row of selectable chips over a paged book grid" screen, shared by Smart Scopes and
 * Collections (the tab + BookGrid pattern).
 */
@Composable
fun ChipBooksScreen(
    chips: List<ChipItem>,
    selectedId: Int?,
    onSelect: (Int) -> Unit,
    books: LazyPagingItems<BookCard>,
    onBookClick: (Int) -> Unit,
    emptyTitle: String,
    emptyBody: String,
    emptyBooksText: String,
    modifier: Modifier = Modifier,
) {
    if (chips.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(emptyTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                emptyBody,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(chips, key = { it.id }) { chip ->
                FilterChip(
                    selected = chip.id == selectedId,
                    onClick = { onSelect(chip.id) },
                    label = {
                        Text(if (chip.count != null) "${chip.label} (${chip.count})" else chip.label)
                    },
                )
            }
        }
        Box(modifier = Modifier.fillMaxSize()) {
            BookGrid(items = books, onBookClick = onBookClick, emptyText = emptyBooksText)
        }
    }
}
