package com.bookorbit.feature.player

import androidx.compose.ui.res.stringResource
import com.bookorbit.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bookorbit.core.model.AudiobookBookmark

/** Bookmarks for the playing audiobook: add one at the current position, tap one to jump, or delete it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksSheet(
    bookmarks: List<AudiobookBookmark>,
    positionSec: Double,
    onAdd: () -> Unit,
    onJump: (AudiobookBookmark) -> Unit,
    onDelete: (AudiobookBookmark) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(stringResource(R.string.bookmarks), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
            Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.BookmarkAdd, contentDescription = null)
                Text(stringResource(R.string.bookmark, BookmarkTitles.time(positionSec)), modifier = Modifier.padding(start = 8.dp))
            }
        }
        if (bookmarks.isEmpty()) {
            Text(
                stringResource(R.string.no_bookmarks_yet),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
                items(bookmarks, key = { it.id }) { b ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onJump(b) }
                            .padding(start = 20.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(b.title.ifBlank { "Bookmark" }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                BookmarkTitles.time(b.positionMs / 1000.0),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { onDelete(b) }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete_bookmark))
                        }
                    }
                }
            }
        }
    }
}
