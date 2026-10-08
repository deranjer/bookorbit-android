package com.bookorbit.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/** Search the open book; matches stream in by chapter, and tapping one jumps to it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSearchSheet(
    state: ReaderViewModel.SearchState?,
    onSearch: (String) -> Unit,
    onClear: () -> Unit,
    onJump: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(state?.query.orEmpty()) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            singleLine = true,
            placeholder = { Text("Search in this book") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (text.isNotEmpty()) {
                    IconButton(onClick = {
                        text = ""
                        onClear()
                    }) { Icon(Icons.Filled.Clear, contentDescription = "Clear search") }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch(text) }),
        )

        if (state != null) {
            if (state.running) {
                LinearProgressIndicator(
                    progress = { state.progress.toFloat() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            val summary = when {
                state.running -> "Searching… ${state.total} so far"
                state.total == 0 -> "No matches"
                state.capped -> "First ${state.total} matches"
                else -> "${state.total} match${if (state.total == 1) "" else "es"}"
            }
            Text(
                summary,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                state.groups.forEach { group ->
                    if (group.label.isNotBlank()) {
                        item(key = "h-${group.label}-${group.hits.firstOrNull()?.cfi}") {
                            Text(
                                group.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            )
                        }
                    }
                    items(group.hits, key = { it.cfi }) { hit ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onJump(hit.cfi) }
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                        ) {
                            val matchColor = MaterialTheme.colorScheme.primary
                            Text(
                                buildAnnotatedString {
                                    append(hit.pre)
                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = matchColor)) { append(hit.match) }
                                    append(hit.post)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
