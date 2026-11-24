package com.bookorbit.feature.reader

import androidx.compose.ui.res.stringResource
import com.bookorbit.R
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.AlertDialog
import com.bookorbit.core.model.BookBookmark
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.TabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Button
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bookorbit.core.model.BookAnnotation

/** Parse "#RRGGBB" or a CSS colour name from another client; unknown values fall back to yellow. */
fun highlightColor(value: String): Color {
    runCatching { return Color(android.graphics.Color.parseColor(value)) }
    return Color(0xFFFACC15)
}

/** Floating toolbar over the page: colour swatches, add/edit note, and delete for an existing highlight. */
@Composable
fun HighlightToolbar(
    currentColor: String?,
    onColor: (String) -> Unit,
    onNote: () -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onReadingHelp: (() -> Unit)? = null,
    onTranslate: (() -> Unit)? = null,
    onDictionary: (() -> Unit)? = null,
) {
    var more by remember { mutableStateOf(false) }
    var palette by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        color = Color(0xF21E1E1E),
    ) {
        FlowRow(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Box {
                IconButton(onClick = { palette = true }) {
                    Box(Modifier.size(26.dp).clip(CircleShape).background(highlightColor(currentColor ?: "#FACC15"))
                        .border(1.dp, Color.White, CircleShape))
                    Icon(Icons.Filled.EditNote, contentDescription = stringResource(R.string.highlights), tint = Color.Black)
                }
                DropdownMenu(expanded = palette, onDismissRequest = { palette = false }) {
                    ReaderAnnotationRepository.COLORS.forEach { (name, hex) ->
                        DropdownMenuItem(text = { Box(Modifier.size(26.dp).clip(CircleShape).background(highlightColor(hex))) },
                            onClick = { palette = false; onColor(hex) },
                            modifier = Modifier.heightIn(min = 48.dp),
                            leadingIcon = { Text(stringResource(R.string.highlight, name)) })
                    }
                }
            }
            if (onTranslate != null) TextButton(modifier = Modifier.heightIn(min = 48.dp), onClick = onTranslate) { Text(stringResource(R.string.reader_translate), color = Color.White) }
            if (onDictionary != null) TextButton(modifier = Modifier.heightIn(min = 48.dp), onClick = onDictionary) { Text(stringResource(R.string.reader_dictionary), color = Color.White) }
            Box {
                IconButton(onClick = { more = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.reader_selection_more), tint = Color.White)
                }
                DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.add_note)) }, onClick = { more = false; onNote() })
                    if (onReadingHelp != null) DropdownMenuItem(text = { Text(stringResource(R.string.help_title)) }, onClick = { more = false; onReadingHelp() })
                    if (onDelete != null) DropdownMenuItem(text = { Text(stringResource(R.string.delete_highlight)) }, onClick = { more = false; onDelete() })
                }
            }
        }
    }
}

@Composable
fun NoteDialog(
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.note)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                placeholder = { Text(stringResource(R.string.write_a_note)) },
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text.trim()) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Highlights, notes and bookmarks in the book; tap one to jump to it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HighlightsSheet(
    annotations: List<BookAnnotation>,
    bookmarks: List<BookBookmark>,
    onJump: (BookAnnotation) -> Unit,
    onDelete: (BookAnnotation) -> Unit,
    onJumpBookmark: (BookBookmark) -> Unit,
    onAddBookmark: () -> Unit,
    onDeleteBookmark: (BookBookmark) -> Unit,
    onDismiss: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.highlights)) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.bookmarks)) })
        }
        if (tab == 1) {
            BookmarksList(bookmarks, onJumpBookmark, onAddBookmark, onDeleteBookmark)
            return@ModalBottomSheet
        }
        if (annotations.isEmpty()) {
            Text(
                stringResource(R.string.no_highlights_yet_press_and_2),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
            )
        } else {
            LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
                items(annotations, key = { it.id }) { a ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onJump(a) }
                            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .width(4.dp)
                                .height(44.dp)
                                .clip(CircleShape)
                                .background(highlightColor(a.color)),
                        )
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            a.chapterTitle?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(a.text, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            a.note?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontStyle = FontStyle.Italic,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        IconButton(onClick = { onDelete(a) }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete_highlight))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookmarksList(
    bookmarks: List<BookBookmark>,
    onJump: (BookBookmark) -> Unit,
    onAdd: () -> Unit,
    onDelete: (BookBookmark) -> Unit,
) {
    Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Icon(Icons.Filled.BookmarkAdd, contentDescription = null)
        Text(stringResource(R.string.bookmark_this_page), modifier = Modifier.padding(start = 8.dp))
    }
    if (bookmarks.isEmpty()) {
        Text(
            stringResource(R.string.no_bookmarks_yet),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
        )
        return
    }
    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
        items(bookmarks, key = { it.id }) { b ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onJump(b) }
                    .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    b.title.ifBlank { stringResource(R.string.fallback_bookmark) },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onDelete(b) }) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete_bookmark))
                }
            }
        }
    }
}
