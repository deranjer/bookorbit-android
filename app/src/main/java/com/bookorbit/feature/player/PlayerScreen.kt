package com.bookorbit.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.bookorbit.ui.LocalImageUrls

internal fun formatTime(totalSeconds: Double): String {
    val s = totalSeconds.toLong().coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    vm: PlayerViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val imageUrls = LocalImageUrls.current
    var scrubbing by remember { mutableStateOf<Float?>(null) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }

    // Re-check the server for progress made elsewhere (e.g. web) whenever this screen becomes
    // visible again — covers both navigating in via the mini-player and resuming the app in place.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshIfStale() }

    val book = state.currentBook
    if (book == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Nothing playing", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val total = state.totalDurationSec
    val displayPos = scrubbing?.toDouble() ?: state.positionSec
    val remaining = (total - displayPos).coerceAtLeast(0.0)
    val chapterIdx = PlaybackQueue.currentChapterIndex(state.chapters, displayPos)
    val currentChapter = state.chapters.getOrNull(chapterIdx)?.title

    fun prevChapter() {
        if (state.chapters.isEmpty()) return
        val idx = PlaybackQueue.currentChapterIndex(state.chapters, state.positionSec)
        val atStart = idx >= 0 && state.positionSec - state.chapters[idx].startSec < 3
        val target = if (atStart && idx > 0) state.chapters[idx - 1] else state.chapters[idx.coerceAtLeast(0)]
        vm.seekToAbsolute(target.startSec)
    }

    fun nextChapter() {
        if (state.chapters.isEmpty()) return
        val idx = PlaybackQueue.currentChapterIndex(state.chapters, state.positionSec)
        state.chapters.getOrNull(idx + 1)?.let { vm.seekToAbsolute(it.startSec) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Close")
            }
            Text(
                "NOW PLAYING",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            val timerActive = state.sleepTimerRemainingSec != null
            IconButton(onClick = { showSleepTimerSheet = true }) {
                Icon(
                    Icons.Filled.Bedtime,
                    contentDescription = if (timerActive) "Sleep timer active" else "Sleep timer",
                    tint = if (timerActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AsyncImage(
                model = imageUrls.cover(book.id),
                contentDescription = book.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth(0.78f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface),
            )
            Text(
                book.title ?: "Audiobook",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 24.dp),
            )
            Text(
                PlaybackQueue.performerLabel(book),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 6.dp),
            )
            currentChapter?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        Slider(
            value = displayPos.toFloat(),
            onValueChange = { scrubbing = it },
            onValueChangeFinished = {
                scrubbing?.let { vm.seekToAbsolute(it.toDouble()) }
                scrubbing = null
            },
            valueRange = 0f..(total.toFloat().coerceAtLeast(1f)),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(displayPos), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("-${formatTime(remaining)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { prevChapter() }, enabled = state.chapters.isNotEmpty()) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous chapter")
            }
            IconButton(onClick = { vm.skipBack() }) {
                Icon(Icons.Filled.Replay10, contentDescription = "Skip back")
            }
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(36.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                if (state.buffering) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(28.dp))
                } else {
                    IconButton(onClick = { vm.togglePlay() }) {
                        Icon(
                            if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(38.dp),
                        )
                    }
                }
            }
            IconButton(onClick = { vm.skipForward() }) {
                Icon(Icons.Filled.Forward30, contentDescription = "Skip forward")
            }
            IconButton(onClick = { nextChapter() }, enabled = state.chapters.isNotEmpty()) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Next chapter")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            SPEED_PRESETS.forEach { preset ->
                FilterChip(
                    selected = kotlin.math.abs(preset - state.speed) < 0.001f,
                    onClick = { vm.setSpeed(preset) },
                    label = { Text("${preset}x") },
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }

    if (showSleepTimerSheet) {
        SleepTimerSheet(
            remainingSec = state.sleepTimerRemainingSec,
            endOfChapter = state.sleepTimerEndOfChapter,
            hasChapters = state.chapters.isNotEmpty(),
            onSetMinutes = { vm.setSleepTimer(it) },
            onSetEndOfChapter = { vm.setSleepTimerEndOfChapter() },
            onCancel = { vm.cancelSleepTimer() },
            onDismiss = { showSleepTimerSheet = false },
        )
    }
}
