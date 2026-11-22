package com.bookorbit.feature.bookdetail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.bookorbit.R
import com.bookorbit.core.model.BookDetail
import com.bookorbit.core.model.BookFiles

/** Choosing content does not fetch it; only the explicit confirmation enqueues a download. */
@Composable
internal fun DownloadSelectionDialog(book: BookDetail, onConfirm: (Set<Int>) -> Unit, onDismiss: () -> Unit) {
    val files = remember(book) { BookFiles.downloadCandidates(book) }
    var selected by remember(book) { mutableStateOf(emptySet<Int>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.offline_choose_files)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.offline_choose_hint))
                files.forEach { file ->
                    val checked = file.id in selected
                    Row(
                        Modifier.fillMaxWidth().toggleable(
                            value = checked,
                            role = Role.Checkbox,
                            onValueChange = { selected = if (it) selected + file.id else selected - file.id },
                        ).padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Column(Modifier.padding(start = 8.dp)) {
                            Text(file.filename ?: stringResource(R.string.offline_file_id, file.id))
                            Text(stringResource(
                                if (file.format?.lowercase() in BookFiles.AUDIO_FORMATS) R.string.offline_audio_format
                                else R.string.offline_text_format,
                                file.format.orEmpty().uppercase(),
                            ))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }, enabled = selected.isNotEmpty()) {
                Text(stringResource(R.string.download))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
