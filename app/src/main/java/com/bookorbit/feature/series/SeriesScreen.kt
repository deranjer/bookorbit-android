package com.bookorbit.feature.series

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.ViewCarousel
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.bookorbit.R
import com.bookorbit.core.model.SeriesSummary
import com.bookorbit.core.settings.SeriesViewMode
import com.bookorbit.feature.main.LibrarySection
import com.bookorbit.ui.LocalImageUrls
import com.bookorbit.ui.components.BookGrid
import com.bookorbit.ui.components.BookRow
import com.bookorbit.ui.components.EmptyState
import com.bookorbit.ui.components.PageHeader
import com.bookorbit.ui.theme.BookOrbitTheme

/** Every series; tapping one opens its books ([SeriesBooksScreen]) as its own route. */
@Composable
fun SeriesScreen(
    onSeriesClick: (id: Int, name: String) -> Unit,
    tabs: @Composable () -> Unit,
    vm: SeriesViewModel = hiltViewModel(),
) {
    val series = vm.series.collectAsLazyPagingItems()
    val viewMode by vm.viewMode.collectAsStateWithLifecycle()
    SeriesContent(series, viewMode, vm::setViewMode, tabs, onSelect = { onSeriesClick(it.id, it.name) })
}

@Composable
internal fun SeriesContent(
    series: LazyPagingItems<SeriesSummary>,
    viewMode: SeriesViewMode,
    onViewMode: (SeriesViewMode) -> Unit,
    tabs: @Composable () -> Unit,
    onSelect: (SeriesSummary) -> Unit,
) {
    val tokens = BookOrbitTheme.tokens
    LibrarySection(
        header = {
            PageHeader(
                title = stringResource(R.string.lib_chip_series),
                eyebrow = stringResource(R.string.title_library),
                actions = {
                    val grid = viewMode == SeriesViewMode.GRID
                    IconButton(onClick = { onViewMode(if (grid) SeriesViewMode.LIST else SeriesViewMode.GRID) }) {
                        Icon(
                            if (grid) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.GridView,
                            contentDescription = stringResource(if (grid) R.string.list_view else R.string.grid_view),
                            tint = tokens.mutedForeground,
                        )
                    }
                },
            )
        },
        tabs = tabs,
    ) {
        val empty = series.loadState.refresh is LoadState.NotLoading && series.itemCount == 0
        when {
            empty -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                EmptyState(Icons.Outlined.ViewCarousel, stringResource(R.string.no_series_found))
            }
            viewMode == SeriesViewMode.GRID -> SeriesGrid(series = series, onSelect = onSelect)
            else -> SeriesList(series = series, onSelect = onSelect)
        }
    }
}

/** One series' books, as a grid. */
@Composable
fun SeriesBooksScreen(
    seriesId: Int,
    name: String,
    onBookClick: (Int) -> Unit,
    vm: SeriesViewModel = hiltViewModel(),
) {
    val books = remember(seriesId) { vm.seriesBooks(seriesId) }.collectAsLazyPagingItems()
    BookGrid(
        items = books,
        onBookClick = onBookClick,
        emptyText = stringResource(R.string.no_books_in_this_series),
        header = {
            item(span = { GridItemSpan(maxLineSpan) }) {
                PageHeader(
                    title = name,
                    eyebrow = stringResource(R.string.lib_chip_series),
                    subtitle = books.itemCount.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.lib_book_count, it, it) },
                    modifier = Modifier.padding(start = 0.dp),
                )
            }
        },
    )
}

internal fun seriesSubtitle(series: SeriesSummary): String =
    "${series.readCount}/${series.bookCount} read" +
        if (series.authors.isNotEmpty()) " · ${series.authors.joinToString(", ")}" else ""

/** How far through a series you are, as a thin bar. */
@Composable
internal fun SeriesProgress(series: SeriesSummary, modifier: Modifier = Modifier) {
    val tokens = BookOrbitTheme.tokens
    val fraction = if (series.bookCount > 0) series.readCount.toFloat() / series.bookCount else 0f
    Box(modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(tokens.muted)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(3.dp).clip(CircleShape).background(tokens.success))
    }
}

@Composable
private fun SeriesList(series: LazyPagingItems<SeriesSummary>, onSelect: (SeriesSummary) -> Unit) {
    val imageUrls = LocalImageUrls.current
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        items(count = series.itemCount, key = series.itemKey { it.id }) { index ->
            val item = series[index] ?: return@items
            BookRow(
                title = item.name,
                subtitle = seriesSubtitle(item),
                coverModel = item.coverBookIds.firstOrNull()?.let { imageUrls.cover(it) },
                onClick = { onSelect(item) },
                extra = { SeriesProgress(item, Modifier.padding(top = 6.dp)) },
            )
            HorizontalDivider(Modifier.padding(start = 74.dp), color = BookOrbitTheme.tokens.border)
        }
    }
}
