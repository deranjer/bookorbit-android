package com.bookorbit.feature.player

import androidx.compose.ui.res.stringResource
import com.bookorbit.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Playback speed: a fine slider (0.5x to 3x in 0.05x steps), quick presets, and a switch to remember
 * the speed for just this book instead of changing the default for every book.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedSheet(
    speed: Float,
    perBook: Boolean,
    onChange: (speed: Float, forThisBook: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var forThisBook by remember { mutableStateOf(perBook) }
    // Track the slider locally while dragging; commit on release so the player isn't re-tuned per pixel.
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = dragging ?: speed

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                speedLabel(shown),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth(),
            )
            Slider(
                value = shown,
                onValueChange = { dragging = snapSpeed(it) },
                onValueChangeFinished = {
                    dragging?.let { onChange(it, forThisBook) }
                    dragging = null
                },
                valueRange = MIN_SPEED..MAX_SPEED,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SPEED_PRESETS.forEach { preset ->
                    FilterChip(
                        selected = abs(preset - shown) < 0.001f,
                        onClick = { onChange(preset, forThisBook) },
                        label = { Text(speedLabel(preset)) },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.remember_for_this_book), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (forThisBook) "Other books keep your default speed." else "Applies to every book.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = forThisBook,
                    onCheckedChange = {
                        forThisBook = it
                        // Re-apply the current speed under the new scope straight away.
                        onChange(speed, it)
                    },
                )
            }
        }
    }
}
