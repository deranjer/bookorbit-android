package com.bookorbit.feature.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bookorbit.feature.authors.AuthorsScreen
import com.bookorbit.feature.collections.CollectionsScreen
import com.bookorbit.feature.library.LibrariesScreen
import com.bookorbit.feature.scopes.SmartScopesScreen
import com.bookorbit.feature.series.SeriesScreen

private val SECTIONS = listOf("Books", "Series", "Authors", "Collections", "Scopes")

/** The Library tab: one place for every way to browse the collection, switched with chips. */
@Composable
fun LibraryHubScreen(onBookClick: (Int) -> Unit) {
    var section by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(SECTIONS) { index, label ->
                FilterChip(selected = section == index, onClick = { section = index }, label = { Text(label) })
            }
        }
        Box(Modifier.weight(1f).fillMaxSize().padding(0.dp)) {
            when (section) {
                0 -> LibrariesScreen(onBookClick = onBookClick)
                1 -> SeriesScreen(onBookClick = onBookClick)
                2 -> AuthorsScreen(onBookClick = onBookClick)
                3 -> CollectionsScreen(onBookClick = onBookClick)
                else -> SmartScopesScreen(onBookClick = onBookClick)
            }
        }
    }
}
