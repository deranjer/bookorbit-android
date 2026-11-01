package com.bookorbit.feature.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.bookorbit.R
import com.bookorbit.feature.authors.AuthorsScreen
import com.bookorbit.feature.collections.CollectionsScreen
import com.bookorbit.feature.library.LibrariesScreen
import com.bookorbit.feature.scopes.SmartScopesScreen
import com.bookorbit.feature.series.SeriesScreen
import com.bookorbit.ui.components.ChoiceChipRow

internal val LIBRARY_SECTIONS = listOf(
    R.string.lib_chip_books,
    R.string.lib_chip_series,
    R.string.lib_chip_authors,
    R.string.lib_chip_collections,
    R.string.lib_chip_scopes,
)

/**
 * The Library tab: one place for every way to browse the collection. Each section draws its own
 * header with the section chips under it, so the title can be the library's name on Books and the
 * section's name elsewhere.
 */
@Composable
fun LibraryHubScreen(
    onBookClick: (Int) -> Unit,
    onAuthorClick: (id: Int, name: String) -> Unit,
    onSeriesClick: (id: Int, name: String) -> Unit,
) {
    var section by rememberSaveable { mutableIntStateOf(0) }
    val tabs: @Composable () -> Unit = { LibraryTabs(section, onSelect = { section = it }) }
    when (section) {
        0 -> LibrariesScreen(onBookClick = onBookClick, tabs = tabs)
        1 -> SeriesScreen(onSeriesClick = onSeriesClick, tabs = tabs)
        2 -> AuthorsScreen(onAuthorClick = onAuthorClick, tabs = tabs)
        3 -> CollectionsScreen(onBookClick = onBookClick, tabs = tabs)
        else -> SmartScopesScreen(onBookClick = onBookClick, tabs = tabs)
    }
}

/** The chips that switch between the Library sections. */
@Composable
fun LibraryTabs(selected: Int, onSelect: (Int) -> Unit) {
    ChoiceChipRow(LIBRARY_SECTIONS.map { stringResource(it) }, selected, onSelect = onSelect)
}

/** A Library section: its header and the section chips above, the content filling the rest. */
@Composable
fun LibrarySection(
    header: @Composable () -> Unit,
    tabs: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        header()
        tabs()
        Box(Modifier.weight(1f).fillMaxSize()) { content() }
    }
}
