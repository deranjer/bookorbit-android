package com.bookorbit.feature.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bookorbit.R
import com.bookorbit.feature.bookdetail.HighlightCard
import com.bookorbit.ui.components.ChoiceChip
import com.bookorbit.ui.components.EmptyState
import com.bookorbit.ui.components.ErrorState
import com.bookorbit.ui.components.PageHeader
import com.bookorbit.ui.components.SearchField

/** Every highlight and note across the library, searchable; tap one to open its book. */
@Composable
fun NotesScreen(
    onBookClick: (Int) -> Unit,
    vm: NotesViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refresh() }
    NotesContent(ui, vm::setQuery, vm::setOnlyWithNotes, vm::loadMore, vm::reload, onBookClick)
}

@Composable
internal fun NotesContent(
    ui: NotesViewModel.UiState,
    onQuery: (String) -> Unit,
    onOnlyWithNotes: (Boolean) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onBookClick: (Int) -> Unit,
) {
    val listState = rememberLazyListState()
    // Load the next page when the end of the list scrolls into view.
    LaunchedEffect(listState, ui.items.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { last -> if (last != null && last >= ui.items.size - 4) onLoadMore() }
    }

    Column(Modifier.fillMaxSize()) {
        PageHeader(
            title = stringResource(R.string.title_notes),
            subtitle = ui.total.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.notes_count, it, it) },
        )
        SearchField(
            value = ui.query,
            onValueChange = onQuery,
            placeholder = stringResource(R.string.search_your_highlights),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip(stringResource(R.string.all), selected = !ui.onlyWithNotes, onClick = { onOnlyWithNotes(false) })
            ChoiceChip(stringResource(R.string.with_notes), selected = ui.onlyWithNotes, onClick = { onOnlyWithNotes(true) })
        }

        when {
            ui.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            ui.error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                ErrorState(stringResource(R.string.couldn_t_load_your_highlights), onRetry = onRetry)
            }
            ui.items.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                EmptyState(
                    Icons.Outlined.EditNote,
                    stringResource(if (ui.query.isNotBlank() || ui.onlyWithNotes) R.string.notes_empty_filtered else R.string.notes_empty),
                )
            }
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(ui.items, key = { it.id }) { item ->
                    HighlightCard(
                        text = item.text,
                        note = item.note,
                        color = item.color,
                        source = listOfNotNull(item.bookTitle, item.author).joinToString(" · "),
                        onClick = { onBookClick(item.bookId) },
                    )
                }
                if (ui.loadingMore) {
                    item { Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) { CircularProgressIndicator() } }
                }
            }
        }
    }
}
