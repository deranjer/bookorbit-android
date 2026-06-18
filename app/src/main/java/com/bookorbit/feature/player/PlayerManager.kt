package com.bookorbit.feature.player

import android.content.ComponentName
import android.content.Context
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.bookorbit.core.model.BookDetail
import com.bookorbit.core.model.BookFileRef
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Bridges the UI to the Media3 [MediaController] connected to [PlaybackService]. Owns the now-playing
 * state, the transport controls, and the offline-first progress reporting. A single instance is
 * shared by the player screen, mini-player, and book detail.
 */
@Singleton
class PlayerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: PlayerRepository,
    private val audioProgress: AudioProgressRepository,
    private val settingsStore: AudioSettingsStore,
) {
    data class UiState(
        val currentBook: BookDetail? = null,
        val files: List<BookFileRef> = emptyList(),
        val chapters: List<ResolvedChapter> = emptyList(),
        val totalDurationSec: Double = 0.0,
        val positionSec: Double = 0.0,
        val isPlaying: Boolean = false,
        val buffering: Boolean = false,
        val speed: Float = DEFAULT_SPEED,
        val skipBackSeconds: Int = DEFAULT_SKIP_BACK,
        val skipForwardSeconds: Int = DEFAULT_SKIP_FORWARD,
    )

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private var controller: MediaController? = null
    private var pollJob: Job? = null
    private var settings = AudioSettings()
    private var lastReport = 0L

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _state.update { it.copy(buffering = playbackState == Player.STATE_BUFFERING) }
        }
    }

    init {
        scope.launch {
            settings = settingsStore.load()
            _state.update {
                it.copy(
                    speed = settings.speed,
                    skipBackSeconds = settings.skipBackSeconds,
                    skipForwardSeconds = settings.skipForwardSeconds,
                )
            }
        }
    }

    @OptIn(UnstableApi::class)
    private suspend fun controller(): MediaController {
        controller?.let { return it }
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val created = suspendCancellableCoroutine { cont ->
            val future = MediaController.Builder(context, token).buildAsync()
            future.addListener({ cont.resume(future.get()) }, ContextCompat.getMainExecutor(context))
        }
        created.addListener(listener)
        controller = created
        return created
    }

    fun loadAndPlay(bookId: Int) {
        scope.launch {
            val data = repo.resolve(bookId) ?: return@launch
            val c = controller()
            val resume = audioProgress.resolveResume(bookId)

            c.setMediaItems(data.mediaItems)
            c.prepare()
            c.setPlaybackSpeed(settings.speed)
            if (resume != null) {
                val idx = data.files.indexOfFirst { it.id == resume.currentFileId }.coerceAtLeast(0)
                c.seekTo(idx, (resume.positionSeconds * 1000).toLong())
            }
            c.play()

            _state.update {
                it.copy(
                    currentBook = data.book,
                    files = data.files,
                    chapters = PlaybackQueue.resolveChapters(data.book),
                    totalDurationSec = PlaybackQueue.totalDurationSec(data.files),
                )
            }
            startPoller()
        }
    }

    /**
     * Adopt a queue that was started outside the UI (e.g. Android Auto set the media items on the
     * shared player directly). Seeds the now-playing state and starts the progress poller so a single
     * sync path covers both phone- and car-initiated playback. The car already set + prepared the
     * items, so this only observes — it does not touch the controller's queue.
     */
    fun adoptExternalQueue(book: BookDetail, files: List<BookFileRef>) {
        scope.launch {
            controller() // ensure the listener is attached so play/pause state tracks
            _state.update {
                it.copy(
                    currentBook = book,
                    files = files,
                    chapters = PlaybackQueue.resolveChapters(book),
                    totalDurationSec = PlaybackQueue.totalDurationSec(files),
                )
            }
            startPoller()
        }
    }

    fun togglePlay() = scope.launch {
        val c = controller()
        if (c.isPlaying) c.pause() else c.play()
    }

    fun skipBack() = scope.launch {
        val c = controller()
        c.seekTo((c.currentPosition - settings.skipBackSeconds * 1000L).coerceAtLeast(0))
    }

    fun skipForward() = scope.launch {
        val c = controller()
        val target = c.currentPosition + settings.skipForwardSeconds * 1000L
        val duration = c.duration
        c.seekTo(if (duration > 0) minOf(duration, target) else target)
    }

    fun seekToAbsolute(absoluteSec: Double) = scope.launch {
        val c = controller()
        val files = _state.value.files
        if (files.isEmpty()) return@launch
        val loc = PlaybackQueue.locateAbsolute(files, absoluteSec)
        c.seekTo(loc.index, (loc.offsetSec * 1000).toLong())
    }

    fun setSpeed(value: Float) = scope.launch {
        settings = settings.copy(speed = clampSpeed(value))
        controller().setPlaybackSpeed(settings.speed)
        settingsStore.saveSpeed(settings.speed)
        _state.update { it.copy(speed = settings.speed) }
    }

    fun setSkipBack(value: Int) = scope.launch {
        settings = settings.copy(skipBackSeconds = value)
        settingsStore.saveSkipBack(value)
        _state.update { it.copy(skipBackSeconds = value) }
    }

    fun setSkipForward(value: Int) = scope.launch {
        settings = settings.copy(skipForwardSeconds = value)
        settingsStore.saveSkipForward(value)
        _state.update { it.copy(skipForwardSeconds = value) }
    }

    fun stop() = scope.launch {
        report(force = true)
        controller?.run {
            stop()
            clearMediaItems()
        }
        pollJob?.cancel()
        _state.update { it.copy(currentBook = null, isPlaying = false) }
    }

    private fun startPoller() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                updatePosition()
                if (System.currentTimeMillis() - lastReport >= 5_000) report(force = false)
                delay(500)
            }
        }
    }

    private fun updatePosition() {
        val c = controller ?: return
        val files = _state.value.files
        if (files.isEmpty()) return
        val abs = PlaybackQueue.toAbsoluteSec(files, c.currentMediaItemIndex, c.currentPosition / 1000.0)
        _state.update { it.copy(positionSec = abs) }
    }

    private suspend fun report(force: Boolean) {
        val c = controller ?: return
        val book = _state.value.currentBook ?: return
        val files = _state.value.files
        if (files.isEmpty()) return
        if (!force && !c.isPlaying) return
        val idx = c.currentMediaItemIndex.coerceIn(0, files.lastIndex)
        val posSec = c.currentPosition / 1000.0
        val pct = PlaybackQueue.percentageFor(files, idx, posSec)
        lastReport = System.currentTimeMillis()
        audioProgress.report(book.id, files[idx].id, posSec, pct)
    }
}
