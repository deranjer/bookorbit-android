package com.bookorbit.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val DURATION_PRESETS_MIN = listOf(5, 10, 15, 30, 45, 60)

/** Duration presets plus "end of chapter"; picking any option (re)arms the timer, replacing whatever ran before. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SleepTimerSheet(
    remainingSec: Long?,
    endOfChapter: Boolean,
    hasChapters: Boolean,
    onSetMinutes: (Int) -> Unit,
    onSetEndOfChapter: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Sleep timer", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

            if (remainingSec != null) {
                val label = if (endOfChapter) "Pausing at the end of this chapter" else "Pausing in ${formatTime(remainingSec.toDouble())}"
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { onCancel(); onDismiss() }) { Text("Cancel timer") }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DURATION_PRESETS_MIN.forEach { minutes ->
                    FilterChip(
                        selected = false,
                        onClick = { onSetMinutes(minutes); onDismiss() },
                        label = { Text("$minutes min") },
                    )
                }
                FilterChip(
                    selected = endOfChapter,
                    enabled = hasChapters,
                    onClick = { onSetEndOfChapter(); onDismiss() },
                    label = { Text("End of chapter") },
                )
            }
        }
    }
}
