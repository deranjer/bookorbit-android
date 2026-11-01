package com.bookorbit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.bookorbit.R
import com.bookorbit.core.model.BookCard

private const val PLACEHOLDER_COUNT = 12

/** A blank cover and two blank lines where a book will be, while the first page loads. */
@Composable
fun CoverPlaceholder(modifier: Modifier = Modifier) {
    val tokens = com.bookorbit.ui.theme.BookOrbitTheme.tokens
    androidx.compose.foundation.layout.Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(androidx.compose.material3.MaterialTheme.shapes.medium)
                .background(tokens.muted),
        )
        Box(Modifier.padding(top = 10.dp).fillMaxWidth(0.8f).height(10.dp).clip(androidx.compose.material3.MaterialTheme.shapes.extraSmall).background(tokens.muted))
        Box(Modifier.padding(top = 6.dp).fillMaxWidth(0.5f).height(8.dp).clip(androidx.compose.material3.MaterialTheme.shapes.extraSmall).background(tokens.muted))
    }
}

/**
 * The paged cover grid every book list uses: as many columns as fit at about 120dp each, gutters
 * like the web's, pull-to-refresh, a spinner while the next page loads, and empty and error states.
 * [header] scrolls with the grid, across its full width.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BookGrid(
    items: LazyPagingItems<BookCard>,
    onBookClick: (Int) -> Unit,
    emptyText: String,
    modifier: Modifier = Modifier,
    header: (LazyGridScope.() -> Unit)? = null,
) {
    // The spinner is for a pull-to-refresh over books already shown. A first load shows placeholder
    // covers instead, as the web does, and a still frame rather than an endless animation.
    val loading = items.loadState.refresh is LoadState.Loading
    val refreshing = loading && items.itemCount > 0
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { items.refresh() },
        modifier = modifier.fillMaxSize(),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 104.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            header?.invoke(this)
            if (loading && items.itemCount == 0) {
                items(PLACEHOLDER_COUNT) { CoverPlaceholder() }
            }
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
                        GhostButton(stringResource(R.string.couldn_t_load_more_retry), onClick = { items.retry() })
                    }
                }
            }

            if (items.loadState.append is LoadState.Loading) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }

            val refreshError = items.loadState.refresh is LoadState.Error && items.itemCount == 0
            val empty = items.loadState.refresh is LoadState.NotLoading && items.itemCount == 0
            if (refreshError || empty) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                        if (refreshError) {
                            ErrorState(stringResource(R.string.couldn_t_load_books), onRetry = { items.retry() })
                        } else {
                            EmptyState(Icons.Outlined.AutoStories, emptyText)
                        }
                    }
                }
            }
        }
    }
}
