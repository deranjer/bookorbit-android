package com.bookorbit.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.bookorbit.core.model.BookCard

/**
 * Shared adaptive-column, paged book grid with pull-to-refresh, an append spinner, and an empty state,
 * built on Paging 3 + Compose.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BookGrid(
    items: LazyPagingItems<BookCard>,
    onBookClick: (Int) -> Unit,
    emptyText: String,
    modifier: Modifier = Modifier,
) {
    val refreshing = items.loadState.refresh is LoadState.Loading
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { items.refresh() },
        modifier = modifier.fillMaxSize(),
    ) {
        LazyVerticalGrid(
            // ~3 columns on a phone; more on tablets/landscape instead of 3 stretched cards.
            columns = GridCells.Adaptive(minSize = 110.dp),
            contentPadding = PaddingValues(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(
                count = items.itemCount,
                key = items.itemKey { it.id },
            ) { index ->
                items[index]?.let { book ->
                    BookCard(book = book, onClick = { onBookClick(book.id) })
                }
            }

            if (items.loadState.append is LoadState.Error) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        TextButton(onClick = { items.retry() }) { Text("Couldn't load more. Retry") }
                    }
                }
            }

            if (items.loadState.append is LoadState.Loading) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }

        val refreshError = items.loadState.refresh is LoadState.Error && items.itemCount == 0
        if (refreshError) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Couldn't load books",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
                Button(onClick = { items.retry() }, modifier = Modifier.padding(top = 12.dp)) { Text("Retry") }
            }
        }

        val empty = items.loadState.refresh is LoadState.NotLoading && items.itemCount == 0
        if (empty) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    emptyText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
