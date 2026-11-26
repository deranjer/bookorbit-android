package com.bookorbit.feature.bookdetail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bookorbit.R
import com.bookorbit.core.model.BookDetail
import com.bookorbit.core.model.BookEditions
import com.bookorbit.core.model.BookFiles

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReadingFileDialog(book: BookDetail, offlineIds: Set<Int>, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    val editions = BookEditions.of(book).filter { it.kind == BookEditions.Kind.TEXT && BookFiles.readingTarget(book, it.files.single().id) != BookFiles.ReadingTarget.None }
        .sortedByDescending { it.isOffline(offlineIds) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.edition_read_title), style = MaterialTheme.typography.headlineMedium)
            editions.forEach { edition -> EditionCard(edition, book, offlineIds, onClick = { onSelect(edition.files.single().id) }) }
            Spacer(Modifier.height(24.dp))
        }
    }
}
