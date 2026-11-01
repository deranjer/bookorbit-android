package com.bookorbit.feature.downloads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bookorbit.R
import com.bookorbit.core.db.DownloadEntity
import com.bookorbit.core.storage.LocalRef
import com.bookorbit.core.storage.toUri
import com.bookorbit.feature.dashboard.ProgressLine
import com.bookorbit.ui.components.BookRow
import com.bookorbit.ui.components.EmptyState
import com.bookorbit.ui.components.GhostButton
import com.bookorbit.ui.components.Pill
import com.bookorbit.ui.theme.BookOrbitTheme

@Composable
fun DownloadsScreen(
    onBookClick: (Int) -> Unit,
    vm: DownloadsViewModel = hiltViewModel(),
) {
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    DownloadsContent(downloads, onBookClick, onRetry = vm::retry, onDelete = vm::delete)
}

/** Books saved for offline reading, each with its state: done, saving (with progress) or failed. */
@Composable
internal fun DownloadsContent(
    downloads: List<DownloadEntity>,
    onBookClick: (Int) -> Unit,
    onRetry: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    coverModel: (DownloadEntity) -> Any? = { item -> item.coverLocalPath?.let { LocalRef.parse(it).toUri() } },
) {
    val tokens = BookOrbitTheme.tokens
    if (downloads.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            EmptyState(Icons.Outlined.DownloadForOffline, stringResource(R.string.no_downloads_yet))
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
        items(downloads, key = { it.bookId }) { item ->
            BookRow(
                title = item.title ?: stringResource(R.string.fallback_untitled),
                subtitle = item.authors.ifBlank { null },
                coverModel = coverModel(item),
                onClick = { onBookClick(item.bookId) },
                extra = {
                    item.format?.let { Pill(it.uppercase(), tokens.formatColor(it), Modifier.padding(top = 4.dp)) }
                    when (item.status) {
                        DownloadStatus.COMPLETE_FALLBACK.name -> Text(
                            stringResource(R.string.saved_to_app_storage_chosen),
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.warning,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        DownloadStatus.DOWNLOADING.name -> ProgressLine(item.progress, Modifier.padding(top = 6.dp))
                        DownloadStatus.FAILED.name -> {
                            Text(
                                item.lastError ?: stringResource(R.string.download_failed),
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.destructive,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            GhostButton(stringResource(R.string.retry), onClick = { onRetry(item.bookId) })
                        }
                    }
                },
                trailing = {
                    IconButton(onClick = { onDelete(item.bookId) }) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.delete), tint = tokens.mutedForeground)
                    }
                },
            )
            HorizontalDivider(Modifier.padding(start = 74.dp).fillMaxWidth(), color = tokens.border)
        }
    }
}
