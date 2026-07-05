package com.bookorbit.feature.bookdrop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bookorbit.core.model.Library
import com.bookorbit.core.model.LibraryFolder

/**
 * Two linked dropdowns (library → folder) used both inline in the detail sheet and inside
 * [DestinationPickerSheet]. Selecting a library resets the folder to that library's first folder.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryFolderSelector(
    libraries: List<Library>,
    selectedLibraryId: Int?,
    selectedFolderId: Int?,
    onLibrarySelected: (Int) -> Unit,
    onFolderSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedLibrary = libraries.firstOrNull { it.id == selectedLibraryId }
    val folders = selectedLibrary?.folders ?: emptyList()
    val selectedFolder = folders.firstOrNull { it.id == selectedFolderId }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DropdownField(
            label = "Destination library",
            value = selectedLibrary?.name ?: "Select a library",
            options = libraries,
            optionLabel = { it.name },
            onSelect = { onLibrarySelected(it.id) },
        )
        DropdownField(
            label = "Destination folder",
            value = selectedFolder?.path ?: if (folders.isEmpty()) "No folders" else "Select a folder",
            options = folders,
            optionLabel = LibraryFolder::path,
            onSelect = { onFolderSelected(it.id) },
            enabled = folders.isNotEmpty(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> DropdownField(
    label: String,
    value: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = it },
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** Bottom sheet for choosing a destination for a bulk selection. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DestinationPickerSheet(
    libraries: List<Library>,
    applyLabel: String,
    onApply: (libraryId: Int, folderId: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var libraryId by remember { mutableStateOf<Int?>(null) }
    var folderId by remember { mutableStateOf<Int?>(null) }

    // Default to the first library + its first folder once libraries load.
    LaunchedEffect(libraries) {
        if (libraryId == null) {
            val first = libraries.firstOrNull()
            libraryId = first?.id
            folderId = first?.folders?.firstOrNull()?.id
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Set Destination", style = MaterialTheme.typography.titleMedium)
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
            Button(
                onClick = {
                    val lib = libraryId
                    val folder = folderId
                    if (lib != null && folder != null) onApply(lib, folder)
                },
                enabled = libraryId != null && folderId != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(applyLabel)
            }
        }
    }
}
