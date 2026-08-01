package com.bookorbit.feature.series

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.bookorbit.core.model.SeriesSummary

/** Grid counterpart to [SeriesList]: a 2-column grid of [SeriesGridCard]s with fanned covers. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SeriesGrid(
    series: LazyPagingItems<SeriesSummary>,
    onSelect: (SeriesSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val refreshing = series.loadState.refresh is LoadState.Loading
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { series.refresh() },
        modifier = modifier.fillMaxSize(),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(
                count = series.itemCount,
                key = series.itemKey { it.id },
            ) { index ->
                series[index]?.let { item ->
                    SeriesGridCard(series = item, onClick = { onSelect(item) })
                }
            }

            if (series.loadState.append is LoadState.Loading) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }

        val empty = series.loadState.refresh is LoadState.NotLoading && series.itemCount == 0
        if (empty) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No series found.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SeriesGridCard(series: SeriesSummary, onClick: () -> Unit) {
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        FannedSeriesCover(coverBookIds = series.coverBookIds, seriesName = series.name)
        Text(
            series.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            seriesSubtitle(series),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
