package com.bookorbit.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private data class FlatTocItem(val label: String, val href: String, val depth: Int)

private fun flatten(items: List<TocItem>, depth: Int = 0, out: MutableList<FlatTocItem> = mutableListOf()): List<FlatTocItem> {
    for (item in items) {
        if (item.href != null) out.add(FlatTocItem(item.label, item.href, depth))
        if (item.subitems.isNotEmpty()) flatten(item.subitems, depth + 1, out)
    }
    return out
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderTocSheet(
    toc: List<TocItem>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val flat = flatten(toc)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "Contents",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
        )
        if (flat.isEmpty()) {
            Text(
                "No table of contents.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
        } else {
            LazyColumn {
                items(flat.size) { index ->
                    val item = flat[index]
                    Text(
                        item.label,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(item.href) }
                            .padding(start = (20 + item.depth * 16).dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
                    )
                }
            }
        }
    }
}
