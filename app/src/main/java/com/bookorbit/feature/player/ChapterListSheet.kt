package com.bookorbit.feature.player

import androidx.compose.ui.res.stringResource
import com.bookorbit.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** The book's chapters; tapping one jumps to its start. Current chapter highlighted, finished ones dimmed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterListSheet(
    ranges: List<ChapterRange>,
    currentIndex: Int,
    positionSec: Double,
    onSelect: (ChapterRange) -> Unit,
    onDismiss: () -> Unit,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (currentIndex - 2).coerceAtLeast(0))
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.chapters),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyColumn(state = listState, modifier = Modifier.padding(bottom = 16.dp)) {
            itemsIndexed(ranges, key = { _, r -> r.index }) { i, range ->
                val isCurrent = i == currentIndex
                val finished = range.endSec <= positionSec
                val color = when {
                    isCurrent -> MaterialTheme.colorScheme.primary
                    finished -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    else -> MaterialTheme.colorScheme.onSurface
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(range) }
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                ) {
                    Text(
                        range.title,
                        color = color,
                        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        formatTime(range.lengthSec),
                        color = color,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }
    }
}
