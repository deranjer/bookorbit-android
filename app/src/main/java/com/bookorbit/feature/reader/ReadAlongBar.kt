package com.bookorbit.feature.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bookorbit.R

/** One control surface for foreground web overlays and the service-owned native SMIL player. */
@Composable
internal fun ReadAlongBar(state: ReaderEvent.ReadAlong, onCommand: (String, Double?) -> Unit, modifier: Modifier = Modifier, background: Boolean = false) {
    Surface(modifier = modifier, shape = RoundedCornerShape(24.dp), tonalElevation = 4.dp, shadowElevation = 4.dp) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(stringResource(R.string.read_along_title), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(if (background) R.string.read_along_background else R.string.read_along_foreground), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { onCommand("prev", null) }, enabled = state.state in setOf("playing", "paused")) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = stringResource(R.string.read_along_previous_fragment))
                }
                IconButton(onClick = {
                    onCommand(when (state.state) { "playing" -> "pause"; "paused" -> "resume"; else -> "start" }, null)
                }, enabled = state.state != "loading") {
                    if (state.state == "loading") CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    else Icon(if (state.state == "playing") Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(if (state.state == "playing") R.string.read_along_pause else R.string.read_along_play))
                }
                IconButton(onClick = { onCommand("next", null) }, enabled = state.state in setOf("playing", "paused")) {
                    Icon(Icons.Default.SkipNext, contentDescription = stringResource(R.string.read_along_next_fragment))
                }
                TextButton(onClick = { onCommand("rate", if (state.rate >= 2.0) 0.75 else state.rate + 0.25) }) {
                    Text(stringResource(R.string.read_along_rate, state.rate.toString()))
                }
            }
            if (state.error != null) Text(stringResource(R.string.read_along_audio_error),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp))
            if (state.position != null || state.error != null) TextButton(onClick = { onCommand("restart", null) }, enabled = state.state != "loading") {
                Text(stringResource(R.string.read_along_from_chapter))
            }
        }
    }
}
