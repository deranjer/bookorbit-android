package com.bookorbit.feature.bookdetail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bookorbit.R
import com.bookorbit.core.model.BookDetail
import com.bookorbit.core.model.BookEditions

@Composable
internal fun EditionCard(
    edition: BookEditions.Edition,
    book: BookDetail,
    offlineIds: Set<Int>,
    onClick: () -> Unit,
    selected: Boolean = false,
    enabled: Boolean = true,
    trailing: @Composable () -> Unit = {},
) {
    val audio = edition.kind == BookEditions.Kind.AUDIO
    Surface(
        onClick = onClick, enabled = enabled, shape = RoundedCornerShape(24.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Icon(if (audio) Icons.Default.Headset else Icons.Default.Book, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(12.dp).size(24.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(if (audio) R.string.edition_audio else R.string.edition_text), style = MaterialTheme.typography.titleMedium)
                val details = buildList {
                    if (audio) add(stringResource(R.string.edition_tracks, edition.files.size))
                    else add(edition.files.single().format.orEmpty().uppercase())
                    edition.durationSeconds?.let { add(formatDuration(it)) }
                    edition.sizeBytes?.let { add(formatBytes(it)) }
                }
                Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (audio) {
                    book.audioMetadata?.narrators?.takeIf { it.isNotEmpty() }?.let { narrators ->
                        Text(narrators.joinToString(", ") { it.name }, style = MaterialTheme.typography.bodyMedium)
                    }
                } else if (BookEditions.of(book).count { it.kind == BookEditions.Kind.TEXT } > 1) {
                    edition.files.single().filename?.substringBeforeLast('.')?.replace('_', ' ')?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    }
                }
                val saved = edition.fileIds.count { it in offlineIds }
                if (saved > 0) Text(
                    if (edition.isOffline(offlineIds)) stringResource(R.string.downloaded)
                    else stringResource(R.string.edition_partially_saved, saved, edition.files.size),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                )
            }
            trailing()
        }
    }
}
