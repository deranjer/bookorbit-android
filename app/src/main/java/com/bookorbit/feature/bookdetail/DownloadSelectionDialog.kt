package com.bookorbit.feature.bookdetail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bookorbit.R
import com.bookorbit.core.model.BookDetail
import com.bookorbit.core.model.BookEditions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DownloadSelectionDialog(book: BookDetail, onConfirm: (Set<Int>) -> Unit, onDismiss: () -> Unit, existingFileIds: Set<Int> = emptySet()) {
    val editions = remember(book) { BookEditions.of(book) }
    var selected by rememberSaveable(book.id) { mutableStateOf(arrayListOf<String>()) }
    val additional = if (selected.isEmpty()) emptySet() else BookEditions.additionalFiles(editions, selected.toSet(), existingFileIds)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.edition_download_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.edition_download_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            editions.forEach { edition ->
                val existing = edition.isOffline(existingFileIds)
                val checked = existing || edition.key in selected
                EditionCard(edition, book, existingFileIds, selected = checked, enabled = !existing,
                    onClick = { selected = ArrayList(if (edition.key in selected) selected - edition.key else selected + edition.key) },
                    trailing = { Checkbox(checked = checked, onCheckedChange = null, enabled = !existing) })
            }
            Button(onClick = { onConfirm(additional) }, enabled = additional.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 56.dp)) {
                Text(stringResource(R.string.edition_download_selected))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
