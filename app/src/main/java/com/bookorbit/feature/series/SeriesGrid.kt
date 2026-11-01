package com.bookorbit.feature.series

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import com.bookorbit.ui.components.Panel
import com.bookorbit.ui.theme.BookOrbitTheme

/** Grid counterpart to the series list: a card per series with its covers fanned out. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SeriesGrid(
    series: LazyPagingItems<SeriesSummary>,
    onSelect: (SeriesSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Only a pull-to-refresh over series already shown spins; see BookGrid.
    val refreshing = series.loadState.refresh is LoadState.Loading && series.itemCount > 0
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { series.refresh() },
        modifier = modifier.fillMaxSize(),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 168.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(count = series.itemCount, key = series.itemKey { it.id }) { index ->
                series[index]?.let { item -> SeriesGridCard(series = item, onClick = { onSelect(item) }) }
            }
            if (series.loadState.append is LoadState.Loading) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

@Composable
private fun SeriesGridCard(series: SeriesSummary, onClick: () -> Unit) {
    Panel(onClick = onClick, contentPadding = PaddingValues(10.dp)) {
        FannedSeriesCover(coverBookIds = series.coverBookIds, seriesName = series.name)
        Text(
            series.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            seriesSubtitle(series),
            style = MaterialTheme.typography.bodySmall,
            color = BookOrbitTheme.tokens.mutedForeground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        SeriesProgress(series, Modifier.padding(top = 8.dp))
    }
}
