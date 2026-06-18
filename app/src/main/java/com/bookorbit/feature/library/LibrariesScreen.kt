package com.bookorbit.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.bookorbit.feature.library.filters.countActiveFilters
import com.bookorbit.ui.components.BookGrid

@Composable
fun LibrariesScreen(
    onBookClick: (Int) -> Unit,
    vm: LibrariesViewModel = hiltViewModel(),
) {
    val libraries by vm.libraries.collectAsStateWithLifecycle()
    val selectedId by vm.selectedId.collectAsStateWithLifecycle()
    val filters by vm.filters.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val books = vm.books.collectAsLazyPagingItems()

    val selected = libraries.firstOrNull { it.id == selectedId }
    var pickerOpen by remember { mutableStateOf(false) }
    var sheetOpen by remember { mutableStateOf(false) }
    val activeFilterCount = countActiveFilters(filters)

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = libraries.size > 1) { pickerOpen = true },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        selected?.name ?: "Library",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    selected?.bookCount?.let { count ->
                        Text(
                            "$count ${if (count == 1) "book" else "books"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (libraries.size > 1) {
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = "Choose library")
                }
                DropdownMenu(expanded = pickerOpen, onDismissRequest = { pickerOpen = false }) {
                    libraries.forEach { lib ->
                        DropdownMenuItem(
                            text = { Text(lib.name) },
                            onClick = {
                                vm.selectLibrary(lib.id)
                                pickerOpen = false
                            },
                        )
                    }
                }
            }

            IconButton(onClick = { sheetOpen = true }, enabled = selectedId != null) {
                BadgedBox(badge = { if (activeFilterCount > 0) Badge { Text("$activeFilterCount") } }) {
                    Icon(Icons.Filled.FilterList, contentDescription = "Filter and sort")
                }
            }
        }
        HorizontalDivider()

        BookGrid(
            items = books,
            onBookClick = onBookClick,
            emptyText = "No books in this library.",
            modifier = Modifier.fillMaxSize(),
        )
    }

    if (sheetOpen) {
        FilterSortSheet(
            initialFilters = filters,
            initialSort = sort,
            searchCatalog = { kind, q -> vm.searchCatalog(kind, q) },
            onApply = { f, s ->
                vm.applyFilters(f, s)
                sheetOpen = false
            },
            onDismiss = { sheetOpen = false },
        )
    }
}
