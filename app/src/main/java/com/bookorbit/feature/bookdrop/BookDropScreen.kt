package com.bookorbit.feature.bookdrop

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil.compose.AsyncImage
import com.bookorbit.core.model.BookDockFile
import com.bookorbit.core.model.Library
import com.bookorbit.ui.LocalImageUrls
import kotlinx.coroutines.launch

private data class StatusTab(val value: String?, val label: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDropScreen(vm: BookDropViewModel = hiltViewModel()) {
    val files = vm.files.collectAsLazyPagingItems()
    val summary by vm.summary.collectAsStateWithLifecycle()
    val statusFilter by vm.statusFilter.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val action by vm.action.collectAsStateWithLifecycle()

    val imageUrls = LocalImageUrls.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var detailFile by remember { mutableStateOf<BookDockFile?>(null) }
    var pickerLibraries by remember { mutableStateOf<List<Library>?>(null) }

    val uploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.upload(uri)
    }

    // Surface action results / errors as a snackbar, then clear them.
    LaunchedEffect(action.message) {
        action.message?.let {
            snackbarHostState.showSnackbar(it)
            vm.consumeMessage()
        }
    }
    LaunchedEffect(action.error) {
        action.error?.let {
            snackbarHostState.showSnackbar(it)
            vm.consumeError()
        }
    }

    val filteredTotal = when (statusFilter) {
        "pending" -> summary.pending
        "ready" -> summary.ready
        "error" -> summary.error
        else -> summary.total
    }
    val selectedCount = if (selection.selectAll) {
        (filteredTotal - selection.excluded.size).coerceAtLeast(0)
    } else {
        selection.ids.size
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!selection.active) {
                FloatingActionButton(onClick = { uploadLauncher.launch("*/*") }) {
                    Icon(Icons.Filled.Upload, contentDescription = "Upload a file")
                }
            }
        },
        bottomBar = {
            if (selection.active) {
                BulkActionBar(
                    selectedCount = selectedCount,
                    busy = action.inProgress,
                    onSetDestination = { scope.launch { pickerLibraries = vm.loadLibraries() } },
                    onApplyFetched = vm::applyFetchedSelection,
                    onApprove = { vm.finalizeSelection(null, null) },
                    onDiscard = vm::discardSelection,
                    onCancel = vm::exitSelection,
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            StatusFilterRow(
                summary = summary.total,
                ready = summary.ready,
                pending = summary.pending,
                error = summary.error,
                selected = statusFilter,
                onSelect = vm::setStatusFilter,
            )

            if (selection.active) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("$selectedCount selected", style = MaterialTheme.typography.labelLarge)
                    TextButton(onClick = vm::selectAllInFilter) { Text("Select all ($filteredTotal)") }
                }
            }

            HorizontalDivider()

            PullToRefreshBox(
                isRefreshing = files.loadState.refresh is LoadState.Loading,
                onRefresh = { vm.refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                if (files.itemCount == 0 && files.loadState.refresh !is LoadState.Loading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Nothing in the Book Dock.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(count = files.itemCount, key = files.itemKey { it.id }) { index ->
                            val file = files[index] ?: return@items
                            BookDockRow(
                                file = file,
                                coverUrl = imageUrls.bookDockCover(file.id),
                                selectionActive = selection.active,
                                selected = selection.isSelected(file.id),
                                onClick = {
                                    if (selection.active) vm.toggleItem(file.id) else detailFile = file
                                },
                                onLongClick = { vm.startSelection(file.id) },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    detailFile?.let { file ->
        BookDropDetailSheet(file = file, vm = vm, onDismiss = { detailFile = null })
    }

    pickerLibraries?.let { libs ->
        DestinationPickerSheet(
            libraries = libs,
            applyLabel = "Set destination",
            onApply = { libraryId, folderId ->
                vm.setTargetSelection(libraryId, folderId)
                pickerLibraries = null
            },
            onDismiss = { pickerLibraries = null },
        )
    }
}

@Composable
private fun StatusFilterRow(
    summary: Int,
    ready: Int,
    pending: Int,
    error: Int,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    val tabs = listOf(
        StatusTab(null, "All ($summary)"),
        StatusTab("ready", "Ready ($ready)"),
        StatusTab("pending", "Pending ($pending)"),
        StatusTab("error", "Error ($error)"),
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tabs.forEach { tab ->
            FilterChip(
                selected = selected == tab.value,
                onClick = { onSelect(tab.value) },
                label = { Text(tab.label) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookDockRow(
    file: BookDockFile,
    coverUrl: String,
    selectionActive: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val title = file.displayMetadata?.title?.takeIf { it.isNotBlank() } ?: file.fileName
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionActive) {
            Checkbox(checked = selected, onCheckedChange = { onClick() })
        }
        AsyncImage(
            model = coverUrl,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 40.dp, height = 60.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                file.fileName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BookDockStatusBadge(file.status)
                file.format?.let {
                    Text(it.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (file.targetFolderId != null) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Destination set",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BulkActionBar(
    selectedCount: Int,
    busy: Boolean,
    onSetDestination: () -> Unit,
    onApplyFetched: () -> Unit,
    onApprove: () -> Unit,
    onDiscard: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Box(modifier = Modifier.weight(1f))
            IconButton(onClick = onApplyFetched, enabled = !busy && selectedCount > 0) {
                Icon(Icons.Filled.AutoFixHigh, contentDescription = "Apply fetched metadata")
            }
            IconButton(onClick = onSetDestination, enabled = !busy && selectedCount > 0) {
                Icon(Icons.Outlined.FolderOpen, contentDescription = "Set destination")
            }
            IconButton(onClick = onDiscard, enabled = !busy && selectedCount > 0) {
                Icon(Icons.Filled.Delete, contentDescription = "Discard", tint = MaterialTheme.colorScheme.error)
            }
            TextButton(onClick = onApprove, enabled = !busy && selectedCount > 0) { Text("Approve") }
        }
    }
}
