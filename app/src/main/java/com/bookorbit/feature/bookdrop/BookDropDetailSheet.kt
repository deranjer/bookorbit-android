package com.bookorbit.feature.bookdrop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bookorbit.core.model.BookDockFile
import com.bookorbit.core.model.BookDockMetadata
import com.bookorbit.core.model.Library
import kotlinx.coroutines.launch

/**
 * Review / edit one Book Dock file: edit metadata, apply server-fetched metadata, choose a
 * destination, then approve (finalize into the library) or discard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDropDetailSheet(
    file: BookDockFile,
    vm: BookDropViewModel,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val action by vm.action.collectAsStateWithLifecycle()

    var current by remember { mutableStateOf(file) }
    var libraries by remember { mutableStateOf<List<Library>>(emptyList()) }
    var libraryId by remember { mutableStateOf(file.targetLibraryId) }
    var folderId by remember { mutableStateOf(file.targetFolderId) }

    val form = remember { FormState(file.displayMetadata) }

    LaunchedEffect(Unit) {
        libraries = vm.loadLibraries()
        if (libraryId == null) {
            val first = libraries.firstOrNull()
            libraryId = first?.id
            folderId = first?.folders?.firstOrNull()?.id
        }
    }

    val busy = action.inProgress
    val hasDestination = libraryId != null && folderId != null

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(current.fileName, style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BookDockStatusBadge(current.status)
                current.format?.let { Text(it.uppercase(), style = MaterialTheme.typography.labelSmall) }
            }
            current.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            if (current.fetchedMetadata != null) {
                FilledTonalButton(
                    onClick = {
                        scope.launch {
                            vm.applyFetchedSingle(current)?.let {
                                current = it
                                form.loadFrom(it.displayMetadata)
                            }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.AutoFixHigh, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("Apply fetched metadata")
                }
            }

            HorizontalDivider()

            Field("Title", form.title) { form.title = it }
            Field("Subtitle", form.subtitle) { form.subtitle = it }
            Field("Authors (comma-separated)", form.authors) { form.authors = it }
            Field("Description", form.description, singleLine = false) { form.description = it }
            Field("Publisher", form.publisher) { form.publisher = it }
            Field("Year", form.year, keyboard = KeyboardType.Number) { form.year = it }
            Field("Language", form.language) { form.language = it }
            Field("ISBN-13", form.isbn13) { form.isbn13 = it }
            Field("ISBN-10", form.isbn10) { form.isbn10 = it }
            Field("Series", form.seriesName) { form.seriesName = it }
            Field("Series #", form.seriesIndex, keyboard = KeyboardType.Number) { form.seriesIndex = it }
            Field("Genres (comma-separated)", form.genres) { form.genres = it }

            HorizontalDivider()

            LibraryFolderSelector(
                libraries = libraries,
                selectedLibraryId = libraryId,
                selectedFolderId = folderId,
                onLibrarySelected = { id ->
                    libraryId = id
                    folderId = libraries.firstOrNull { it.id == id }?.folders?.firstOrNull()?.id
                },
                onFolderSelected = { folderId = it },
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { scope.launch { vm.saveMetadata(current.id, form.toMetadata())?.let { current = it } } },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Save")
                }
                Button(
                    onClick = {
                        scope.launch {
                            vm.saveMetadata(current.id, form.toMetadata())
                            val result = vm.approveSingle(current.id, libraryId, folderId)
                            if (result != null && result.failed == 0) onDismiss()
                        }
                    },
                    enabled = !busy && hasDestination,
                    modifier = Modifier.weight(1f),
                ) {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                    }
                    Text("Approve")
                }
            }

            OutlinedButton(
                onClick = { scope.launch { vm.discardSingle(current.id); onDismiss() } },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Discard")
            }
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    singleLine: Boolean = true,
    keyboard: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Mutable text-field backing state mapped to/from [BookDockMetadata]. */
private class FormState(metadata: BookDockMetadata?) {
    var title by mutableStateOf("")
    var subtitle by mutableStateOf("")
    var authors by mutableStateOf("")
    var description by mutableStateOf("")
    var publisher by mutableStateOf("")
    var year by mutableStateOf("")
    var language by mutableStateOf("")
    var isbn13 by mutableStateOf("")
    var isbn10 by mutableStateOf("")
    var seriesName by mutableStateOf("")
    var seriesIndex by mutableStateOf("")
    var genres by mutableStateOf("")

    init {
        loadFrom(metadata)
    }

    fun loadFrom(m: BookDockMetadata?) {
        title = m?.title.orEmpty()
        subtitle = m?.subtitle.orEmpty()
        authors = m?.authors?.joinToString(", ").orEmpty()
        description = m?.description.orEmpty()
        publisher = m?.publisher.orEmpty()
        year = m?.publishedYear?.toString().orEmpty()
        language = m?.language.orEmpty()
        isbn13 = m?.isbn13.orEmpty()
        isbn10 = m?.isbn10.orEmpty()
        seriesName = m?.seriesName.orEmpty()
        seriesIndex = m?.seriesIndex?.toString().orEmpty()
        genres = m?.genres?.joinToString(", ").orEmpty()
    }

    fun toMetadata() = BookDockMetadata(
        title = title.trimToNull(),
        subtitle = subtitle.trimToNull(),
        authors = authors.toListOrNull(),
        description = description.trimToNull(),
        publisher = publisher.trimToNull(),
        publishedYear = year.trim().toIntOrNull(),
        language = language.trimToNull(),
        isbn13 = isbn13.trimToNull(),
        isbn10 = isbn10.trimToNull(),
        seriesName = seriesName.trimToNull(),
        seriesIndex = seriesIndex.trim().toDoubleOrNull(),
        genres = genres.toListOrNull(),
    )
}

private fun String.trimToNull(): String? = trim().ifEmpty { null }

private fun String.toListOrNull(): List<String>? =
    split(",").map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { null }
