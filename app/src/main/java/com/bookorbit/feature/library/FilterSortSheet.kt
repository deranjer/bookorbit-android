package com.bookorbit.feature.library

import androidx.compose.ui.res.stringResource
import com.bookorbit.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bookorbit.feature.library.filters.CatalogKind
import com.bookorbit.feature.library.filters.DEFAULT_FILTERS
import com.bookorbit.feature.library.filters.DEFAULT_SORT
import com.bookorbit.feature.library.filters.FILE_AVAILABILITY_OPTIONS
import com.bookorbit.feature.library.filters.FORMAT_OPTIONS
import com.bookorbit.feature.library.filters.LibraryFilters
import com.bookorbit.feature.library.filters.LibrarySort
import com.bookorbit.feature.library.filters.READ_PROGRESS_OPTIONS
import com.bookorbit.feature.library.filters.READ_STATUS_OPTIONS
import com.bookorbit.feature.library.filters.SORT_OPTIONS
import com.bookorbit.ui.components.TypeaheadMultiSelect

private fun <T> toggle(list: List<T>, item: T): List<T> =
    if (item in list) list - item else list + item

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSortSheet(
    initialFilters: LibraryFilters,
    initialSort: LibrarySort,
    searchCatalog: suspend (CatalogKind, String) -> List<String>,
    onApply: (LibraryFilters, LibrarySort) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var filters by remember { mutableStateOf(initialFilters) }
    var sort by remember { mutableStateOf(initialSort) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionLabel(stringResource(R.string.filter_sort_by))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SORT_OPTIONS.forEach { (field, labelRes) ->
                    FilterChip(
                        selected = sort.field == field,
                        onClick = { sort = sort.copy(field = field) },
                        label = { Text(stringResource(labelRes)) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = sort.dir == "asc",
                    onClick = { sort = sort.copy(dir = "asc") },
                    label = { Text(stringResource(R.string.ascending)) },
                )
                FilterChip(
                    selected = sort.dir == "desc",
                    onClick = { sort = sort.copy(dir = "desc") },
                    label = { Text(stringResource(R.string.descending)) },
                )
            }

            HorizontalDivider()
            SectionLabel(stringResource(R.string.filter_read_status))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                READ_STATUS_OPTIONS.forEach { (value, labelRes) ->
                    FilterChip(
                        selected = value in filters.readStatus,
                        onClick = { filters = filters.copy(readStatus = toggle(filters.readStatus, value)) },
                        label = { Text(stringResource(labelRes)) },
                    )
                }
            }

            SectionLabel(stringResource(R.string.filter_read_progress))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                READ_PROGRESS_OPTIONS.forEach { (value, labelRes) ->
                    FilterChip(
                        selected = filters.readProgress == value,
                        onClick = {
                            filters = filters.copy(readProgress = if (filters.readProgress == value) null else value)
                        },
                        label = { Text(stringResource(labelRes)) },
                    )
                }
            }

            SectionLabel(stringResource(R.string.filter_formats))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FORMAT_OPTIONS.forEach { fmt ->
                    FilterChip(
                        selected = fmt in filters.formats,
                        onClick = { filters = filters.copy(formats = toggle(filters.formats, fmt)) },
                        label = { Text(fmt.uppercase()) },
                    )
                }
            }

            SectionLabel(stringResource(R.string.filter_file_availability))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FILE_AVAILABILITY_OPTIONS.forEach { (value, labelRes) ->
                    FilterChip(
                        selected = filters.fileAvailability == value,
                        onClick = {
                            filters = filters.copy(fileAvailability = if (filters.fileAvailability == value) null else value)
                        },
                        label = { Text(stringResource(labelRes)) },
                    )
                }
            }

            HorizontalDivider()
            TypeaheadMultiSelect(
                label = stringResource(R.string.authors),
                selected = filters.authors,
                onSelectedChange = { filters = filters.copy(authors = it) },
                search = { searchCatalog(CatalogKind.AUTHORS, it) },
            )
            TypeaheadMultiSelect(
                label = stringResource(R.string.genres),
                selected = filters.genres,
                onSelectedChange = { filters = filters.copy(genres = it) },
                search = { searchCatalog(CatalogKind.GENRES, it) },
            )
            TypeaheadMultiSelect(
                label = stringResource(R.string.tags),
                selected = filters.tags,
                onSelectedChange = { filters = filters.copy(tags = it) },
                search = { searchCatalog(CatalogKind.TAGS, it) },
            )
            TypeaheadMultiSelect(
                label = stringResource(R.string.languages),
                selected = filters.languages,
                onSelectedChange = { filters = filters.copy(languages = it) },
                search = { searchCatalog(CatalogKind.LANGUAGES, it) },
            )

            HorizontalDivider()
            SectionLabel(stringResource(R.string.filter_min_rating))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..5).forEach { rating ->
                    FilterChip(
                        selected = filters.minRating == rating,
                        onClick = {
                            filters = filters.copy(minRating = if (filters.minRating == rating) null else rating)
                        },
                        label = { Text(stringResource(R.string.filter_rating_stars, rating)) },
                    )
                }
            }

            SectionLabel(stringResource(R.string.filter_published_year))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = filters.yearFrom?.toString() ?: "",
                    onValueChange = { filters = filters.copy(yearFrom = it.toIntOrNull()) },
                    label = { Text(stringResource(R.string.from)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(140.dp),
                )
                OutlinedTextField(
                    value = filters.yearTo?.toString() ?: "",
                    onValueChange = { filters = filters.copy(yearTo = it.toIntOrNull()) },
                    label = { Text(stringResource(R.string.to)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(140.dp),
                )
            }

            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        filters = DEFAULT_FILTERS
                        sort = DEFAULT_SORT
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.reset)) }
                Button(
                    onClick = { onApply(filters, sort) },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.apply)) }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = androidx.compose.material3.MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
}
