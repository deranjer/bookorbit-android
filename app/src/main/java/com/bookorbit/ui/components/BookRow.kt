package com.bookorbit.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bookorbit.ui.theme.BookOrbitTheme

/**
 * One book as a list row, the web's `BookListRow`: a small cover, the title over the author, a line
 * of extra detail, and whatever belongs at the end (a size, a status, a delete button).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookRow(
    title: String,
    coverModel: Any?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onLongClick: (() -> Unit)? = null,
    leading: (@Composable RowScope.() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val tokens = BookOrbitTheme.tokens
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke(this)
        BookCover(
            model = coverModel,
            title = null,
            shape = MaterialTheme.shapes.extraSmall,
            modifier = Modifier.size(width = 44.dp, height = 66.dp),
        )
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = tokens.mutedForeground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            extra?.invoke(this)
        }
        trailing?.invoke(this)
    }
}
