package com.bookorbit.feature.bookdetail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.bookorbit.R
import com.bookorbit.core.model.BookDetail
import com.bookorbit.core.model.BookFiles

@Composable
internal fun ReadingFileDialog(book: BookDetail, offlineIds: Set<Int>, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    val files = book.files.filter { BookFiles.readingTarget(book, it.id) != BookFiles.ReadingTarget.None }
        .sortedByDescending { it.id in offlineIds }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reading_choose_file)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                files.forEach { file ->
                    TextButton(onClick = { onSelect(file.id) }, modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Text(file.filename ?: stringResource(R.string.offline_file_id, file.id))
                            Text(file.format.orEmpty().uppercase())
                            if (file.id in offlineIds) Text(stringResource(R.string.downloaded))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
