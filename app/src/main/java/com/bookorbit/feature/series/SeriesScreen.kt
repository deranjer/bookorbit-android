package com.bookorbit.feature.series

import androidx.compose.ui.res.stringResource
import com.bookorbit.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.bookorbit.core.model.SeriesSummary
import com.bookorbit.core.settings.SeriesViewMode
import com.bookorbit.ui.components.BookGrid

/** Every series; tapping one opens its books ([SeriesBooksScreen]) as its own route. */
@Composable
fun SeriesScreen(
    onSeriesClick: (id: Int, name: String) -> Unit,
    vm: SeriesViewModel = hiltViewModel(),
) {
    val series = vm.series.collectAsLazyPagingItems()
    val viewMode by vm.viewMode.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            IconButton(onClick = { vm.setViewMode(SeriesViewMode.LIST) }) {
                Icon(
                    Icons.AutoMirrored.Filled.List,
                    contentDescription = stringResource(R.string.list_view),
                    tint = if (viewMode == SeriesViewMode.LIST) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { vm.setViewMode(SeriesViewMode.GRID) }) {
                Icon(
                    Icons.Filled.GridView,
                    contentDescription = stringResource(R.string.grid_view),
                    tint = if (viewMode == SeriesViewMode.GRID) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val open: (SeriesSummary) -> Unit = { onSeriesClick(it.id, it.name) }
        when (viewMode) {
            SeriesViewMode.LIST -> SeriesList(series = series, onSelect = open)
            SeriesViewMode.GRID -> SeriesGrid(series = series, onSelect = open)
        }
    }
}

/** One series' books, as a grid. */
@Composable
fun SeriesBooksScreen(
    seriesId: Int,
    onBookClick: (Int) -> Unit,
    vm: SeriesViewModel = hiltViewModel(),
) {
    val books = remember(seriesId) { vm.seriesBooks(seriesId) }.collectAsLazyPagingItems()
    BookGrid(items = books, onBookClick = onBookClick, emptyText = stringResource(R.string.no_books_in_this_series))
}

internal fun seriesSubtitle(series: SeriesSummary): String =
    "${series.readCount}/${series.bookCount} read" +
        if (series.authors.isNotEmpty()) " · ${series.authors.joinToString(", ")}" else ""

@Composable
private fun SeriesList(series: LazyPagingItems<SeriesSummary>, onSelect: (SeriesSummary) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(count = series.itemCount, key = series.itemKey { it.id }) { index ->
            val item = series[index] ?: return@items
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(item) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        seriesSubtitle(item),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
        }
    }
}
