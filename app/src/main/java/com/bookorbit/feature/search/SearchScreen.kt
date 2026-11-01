package com.bookorbit.feature.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bookorbit.R
import com.bookorbit.ui.LocalImageUrls
import com.bookorbit.ui.components.BookRow
import com.bookorbit.ui.components.EmptyState
import com.bookorbit.ui.components.PageHeader
import com.bookorbit.ui.components.Pill
import com.bookorbit.ui.components.SearchField
import com.bookorbit.ui.theme.BookOrbitTheme

@Composable
fun SearchScreen(
    onBookClick: (Int) -> Unit,
    vm: SearchViewModel = hiltViewModel(),
) {
    val query by vm.query.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    SearchContent(query, state, vm::onQueryChange, onBookClick)
}

@Composable
internal fun SearchContent(
    query: String,
    state: SearchViewModel.UiState,
    onQueryChange: (String) -> Unit,
    onBookClick: (Int) -> Unit,
) {
    val imageUrls = LocalImageUrls.current
    val tokens = BookOrbitTheme.tokens
    Column(Modifier.fillMaxSize()) {
        PageHeader(title = stringResource(R.string.title_search))
        SearchField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = stringResource(R.string.search_books),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (state.loading) LinearProgressIndicator(Modifier.padding(horizontal = 16.dp).fillMaxWidth())

        when {
            state.showHint -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                EmptyState(Icons.Outlined.Search, stringResource(R.string.search_hint))
            }
            state.results.isEmpty() && !state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                EmptyState(Icons.Outlined.SearchOff, stringResource(R.string.search_no_results, query.trim()))
            }
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(state.results, key = { it.id }) { result ->
                    BookRow(
                        title = result.title ?: stringResource(R.string.card_unknown_title),
                        subtitle = result.authors.joinToString(", "),
                        coverModel = imageUrls.cover(result.id),
                        onClick = { onBookClick(result.id) },
                        extra = {
                            result.seriesName?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = tokens.mutedForeground, maxLines = 1)
                            }
                        },
                        trailing = {
                            Column(horizontalAlignment = Alignment.End) {
                                result.formats.firstOrNull()?.let { Pill(it.uppercase(), tokens.formatColor(it)) }
                                Text(result.libraryName, style = MaterialTheme.typography.labelSmall, color = tokens.mutedForeground, modifier = Modifier.padding(top = 4.dp))
                            }
                        },
                    )
                    HorizontalDivider(Modifier.padding(start = 74.dp), color = tokens.border)
                }
            }
        }
    }
}
