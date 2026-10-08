package com.bookorbit.feature.player

import com.bookorbit.ui.asString
import androidx.compose.ui.res.stringResource
import com.bookorbit.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.runtime.LaunchedEffect
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
import com.bookorbit.feature.cast.CastButton
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
    var showSpeedSheet by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val toastContext = androidx.compose.ui.platform.LocalContext.current
    message?.let { msg ->
        LaunchedEffect(msg) {
            android.widget.Toast.makeText(toastContext, msg.resolve(toastContext), android.widget.Toast.LENGTH_SHORT).show()
            vm.consumeMessage()
        }
    }

    // Re-check the server for progress made elsewhere (e.g. web) whenever this screen becomes
    // visible again — covers both navigating in via the mini-player and resuming the app in place.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshIfStale() }

    val book = state.currentBook
    if (book == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                state.playerError?.asString() ?: stringResource(R.string.player_nothing_playing),
                color = if (state.playerError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
        }
        return
    }

    val total = state.totalDurationSec
    val ranges = remember(state.chapters, total) { PlaybackQueue.chapterRanges(state.chapters, total) }
    val chapterMode = state.progressBarMode == ProgressBarMode.CHAPTER && ranges.size >= 2
    var scrubRange by remember { mutableStateOf<ChapterRange?>(null) }
    val liveRange = PlaybackQueue.chapterRange(state.chapters, total, state.positionSec)
    val sliderRange = if (chapterMode) (scrubRange ?: liveRange) else null
    val displayBookPos = scrubbing?.let { s -> sliderRange?.let { PlaybackQueue.toBookTime(it, s.toDouble()) } ?: s.toDouble() } ?: state.positionSec
    val currentChapter = PlaybackQueue.chapterRange(state.chapters, total, displayBookPos)
    var showChapters by remember { mutableStateOf(false) }

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
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.close))
            }
            Text(
                stringResource(R.string.now_playing),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            if (ranges.isNotEmpty()) {
                IconButton(onClick = { showChapters = true }) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.chapters))
                }
            }
            IconButton(onClick = {
                vm.loadBookmarks()
                showBookmarks = true
            }) {
                Icon(Icons.Filled.Bookmarks, contentDescription = stringResource(R.string.bookmarks))
            }
            CastButton()
            val timerActive = state.sleepTimerRemainingSec != null
            IconButton(onClick = { showSleepTimerSheet = true }) {
                Icon(
                    Icons.Filled.Bedtime,
                    contentDescription = stringResource(if (timerActive) R.string.player_sleep_timer_active else R.string.sleep_timer),
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
                book.title ?: stringResource(R.string.player_audiobook),
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
            if (state.isCasting) {
                Text(
                    stringResource(R.string.player_casting_to, state.castDeviceName ?: stringResource(R.string.player_casting_device)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            currentChapter?.let {
                Text(
                    "${it.title} ▾",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { showChapters = true }.padding(top = 8.dp),
                )
            }
            state.playerError?.let {
                Text(
                    it.asString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp),
                )
            }
        }

        val sliderValue = sliderRange?.let { PlaybackQueue.toChapterTime(it, displayBookPos) } ?: displayBookPos
        val sliderMax = sliderRange?.lengthSec ?: total
        Slider(
            value = sliderValue.toFloat(),
            onValueChange = { if (scrubbing == null) scrubRange = liveRange; scrubbing = it },
            onValueChangeFinished = {
                scrubbing?.let { s ->
                    val target = (if (chapterMode) scrubRange ?: liveRange else null)
                        ?.let { PlaybackQueue.toBookTime(it, s.toDouble()) } ?: s.toDouble()
                    vm.seekToAbsolute(target)
                }
                scrubbing = null
                scrubRange = null
            },
            valueRange = 0f..(sliderMax.toFloat().coerceAtLeast(1f)),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(sliderValue), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("-${formatTime((sliderMax - sliderValue).coerceAtLeast(0.0))}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (chapterMode && currentChapter != null) {
            Text(
                stringResource(R.string.ch_of_left_in_book, currentChapter.index + 1, ranges.size, PlaybackQueue.formatDurationShort(total - displayBookPos)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                textAlign = TextAlign.Center,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { prevChapter() }, enabled = state.chapters.isNotEmpty()) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = stringResource(R.string.previous_chapter))
            }
            IconButton(onClick = { vm.skipBack() }) {
                Icon(Icons.Filled.Replay10, contentDescription = stringResource(R.string.skip_back))
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
                            contentDescription = stringResource(if (state.isPlaying) R.string.player_pause else R.string.player_play),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(38.dp),
                        )
                    }
                }
            }
            IconButton(onClick = { vm.skipForward() }) {
                Icon(Icons.Filled.Forward30, contentDescription = stringResource(R.string.skip_forward))
            }
            IconButton(onClick = { nextChapter() }, enabled = state.chapters.isNotEmpty()) {
                Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.next_chapter))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            SPEED_PRESETS.forEach { preset ->
                FilterChip(
                    selected = kotlin.math.abs(preset - state.speed) < 0.001f,
                    onClick = { vm.setSpeed(preset, forThisBook = state.speedIsPerBook) },
                    label = { Text(speedLabel(preset), style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false) },
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
            }
            // A custom speed shows as its own selected chip; otherwise a tune button opens the fine control.
            val custom = SPEED_PRESETS.none { kotlin.math.abs(it - state.speed) < 0.001f }
            if (custom) {
                FilterChip(
                    selected = true,
                    onClick = { showSpeedSheet = true },
                    label = { Text(speedLabel(state.speed), style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false) },
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
            } else {
                IconButton(onClick = { showSpeedSheet = true }) {
                    Icon(Icons.Filled.Tune, contentDescription = stringResource(R.string.custom_speed))
                }
            }
        }
    }

    if (showChapters && ranges.isNotEmpty()) {
        ChapterListSheet(
            ranges = ranges,
            currentIndex = liveRange?.index ?: 0,
            positionSec = state.positionSec,
            onSelect = { vm.seekToAbsolute(it.startSec); showChapters = false },
            onDismiss = { showChapters = false },
        )
    }

    if (showBookmarks) {
        BookmarksSheet(
            bookmarks = bookmarks,
            positionSec = state.positionSec,
            onAdd = vm::addBookmark,
            onJump = {
                vm.seekToAbsolute(it.positionMs / 1000.0)
                showBookmarks = false
            },
            onDelete = { vm.deleteBookmark(it.id) },
            onDismiss = { showBookmarks = false },
        )
    }

    if (showSpeedSheet) {
        SpeedSheet(
            speed = state.speed,
            perBook = state.speedIsPerBook,
            onChange = { speed, forThisBook -> vm.setSpeed(speed, forThisBook) },
            onDismiss = { showSpeedSheet = false },
        )
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
