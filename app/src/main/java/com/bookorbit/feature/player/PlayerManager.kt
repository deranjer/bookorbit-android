package com.bookorbit.feature.player

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.bookorbit.core.model.AudioProgress
import com.bookorbit.core.model.BookDetail
import com.bookorbit.core.model.BookFileRef
import com.bookorbit.feature.cast.CastSessionController
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
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
    private val castSessionController: CastSessionController,
    private val restoreCandidates: RestoreCandidateFinder,
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
        val sleepTimerRemainingSec: Long? = null,
        val sleepTimerEndOfChapter: Boolean = false,
        val isCasting: Boolean = false,
        val castDeviceName: String? = null,
        /** Set on a load or playback failure so the UI can explain why nothing is playing, instead
         * of silently doing nothing (see issue #38). Cleared on the next successful load/resume. */
        val playerError: String? = null,
    )

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private var controller: MediaController? = null
    /** Serializes [controller] so a launch restore and a user tap can't each build one. */
    private val controllerLock = Mutex()
    private var pollJob: Job? = null
    private var settings = AudioSettings()
    private var lastReport = 0L
    private val settingsLoaded = CompletableDeferred<Unit>()

    /** Bumped whenever the user, the car, or [stop] changes the queue; an in-flight launch restore
     * that sees it move gives way instead of replacing what was just started. Atomic because Android
     * Auto calls [adoptExternalQueue] off the main thread. */
    private val loadGeneration = AtomicInteger()
    private var restoreAttempted = false

    /** Wall-clock deadline for a duration-based sleep timer; null when off or in end-of-chapter mode. */
    private var sleepTimerEndAtMs: Long? = null
    private var sleepTimerEndOfChapter = false

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            // A successful resume clears any earlier error - it's no longer the reason nothing is playing.
            _state.update { it.copy(isPlaying = isPlaying, playerError = if (isPlaying) null else it.playerError) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _state.update { it.copy(buffering = playbackState == Player.STATE_BUFFERING) }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "playback error bookId=${_state.value.currentBook?.id} code=${error.errorCodeName}", error)
            _state.update { it.copy(buffering = false, isPlaying = false, playerError = describePlayerError(error)) }
        }
    }

    init {
        scope.launch {
            try {
                settings = settingsStore.load()
                _state.update {
                    it.copy(
                        speed = settings.speed,
                        skipBackSeconds = settings.skipBackSeconds,
                        skipForwardSeconds = settings.skipForwardSeconds,
                    )
                }
            } finally {
                // Defaults stand in if loading failed; either way a waiting restore may proceed.
                settingsLoaded.complete(Unit)
            }
        }
        scope.launch {
            castSessionController.state.collect { cast ->
                _state.update { it.copy(isCasting = cast.isConnected, castDeviceName = cast.deviceName) }
            }
        }
    }

    @OptIn(UnstableApi::class)
    private suspend fun controller(): MediaController {
        controller?.let { return it }
        return controllerLock.withLock {
            controller ?: run {
                val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
                val created = suspendCancellableCoroutine { cont ->
                    val future = MediaController.Builder(context, token).buildAsync()
                    future.addListener({ cont.resume(future.get()) }, ContextCompat.getMainExecutor(context))
                }
                created.addListener(listener)
                controller = created
                created
            }
        }
    }

    fun loadAndPlay(bookId: Int) {
        loadGeneration.incrementAndGet()
        cancelSleepTimer()
        _state.update { it.copy(playerError = null) }
        scope.launch {
            val data = repo.resolve(bookId)
            if (data == null) {
                Log.e(TAG, "unable to resolve playback data for bookId=$bookId")
                _state.update {
                    it.copy(playerError = "Couldn't load this audiobook. Check your connection, " +
                        "or that it finished downloading for offline use.")
                }
                return@launch
            }
            val c = controller()
            val resume = audioProgress.resolveResume(bookId)
            applyQueue(c, data, resume, autoPlay = true)
        }
    }

    /**
     * Brings the mini-player back after the app was closed: when the app starts with nothing loaded,
     * loads the most recently played unfinished audiobook (this device's history and the server's
     * Continue Listening, whichever was played last) paused at its saved position. Runs at most once
     * per process, stays silent if nothing can be loaded (e.g. offline and not downloaded), and gives
     * way if the user or Android Auto loads something while it's still looking.
     */
    fun restoreLastBook() {
        if (restoreAttempted) return
        restoreAttempted = true
        if (_state.value.currentBook != null) return
        val generation = loadGeneration.get()
        scope.launch {
            try {
                for ((bookId, resume) in restoreCandidates.find()) {
                    // Downloads resolve locally; a streamed book needs the server, so don't wait long on it.
                    val data = withTimeoutOrNull(RESTORE_RESOLVE_TIMEOUT_MS) { repo.resolve(bookId) } ?: continue
                    settingsLoaded.await()
                    if (generation != loadGeneration.get() || _state.value.currentBook != null) return@launch
                    val c = controller()
                    // Re-checked after the last suspension point: from here to applyQueue runs without yielding.
                    if (generation != loadGeneration.get() || _state.value.currentBook != null || c.mediaItemCount > 0) return@launch
                    applyQueue(c, data, resume, autoPlay = false)
                    return@launch
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Best effort: a failed restore just leaves the app as it was before this feature.
                Log.w(TAG, "couldn't restore the last audiobook", e)
            }
        }
    }

    /** Loads [data] into the session player at [resume]; plays only when [autoPlay]. */
    private fun applyQueue(c: MediaController, data: PlayerRepository.PlayerData, resume: AudioProgress?, autoPlay: Boolean) {
        c.setMediaItems(data.mediaItems)
        c.prepare()
        c.setPlaybackSpeed(settings.speed)
        if (resume != null) {
            val idx = data.files.indexOfFirst { it.id == resume.currentFileId }.coerceAtLeast(0)
            c.seekTo(idx, (resume.positionSeconds * 1000).toLong())
        }
        if (autoPlay) c.play()

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

    /**
     * Adopt a queue that was started outside the UI (e.g. Android Auto set the media items on the
     * shared player directly). Seeds the now-playing state and starts the progress poller so a single
     * sync path covers both phone- and car-initiated playback. The car already set + prepared the
     * items, so this only observes — it does not touch the controller's queue.
     */
    fun adoptExternalQueue(book: BookDetail, files: List<BookFileRef>) {
        loadGeneration.incrementAndGet()
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

    /** Arms a countdown that pauses playback after [minutes] of wall-clock time. */
    fun setSleepTimer(minutes: Int) {
        sleepTimerEndOfChapter = false
        sleepTimerEndAtMs = System.currentTimeMillis() + minutes * 60_000L
        _state.update { it.copy(sleepTimerRemainingSec = minutes * 60L, sleepTimerEndOfChapter = false) }
    }

    /** Arms a countdown that pauses playback when the current chapter ends. */
    fun setSleepTimerEndOfChapter() {
        sleepTimerEndOfChapter = true
        sleepTimerEndAtMs = null
        _state.update { it.copy(sleepTimerEndOfChapter = true) }
        tickSleepTimer()
    }

    fun cancelSleepTimer() {
        sleepTimerEndAtMs = null
        sleepTimerEndOfChapter = false
        _state.update { it.copy(sleepTimerRemainingSec = null, sleepTimerEndOfChapter = false) }
    }

    /** Recomputes the remaining time and pauses playback once it lapses. Driven by the poller tick. */
    private fun tickSleepTimer() {
        val endAt = sleepTimerEndAtMs
        if (endAt == null && !sleepTimerEndOfChapter) return

        val remainingSec = if (sleepTimerEndOfChapter) {
            val s = _state.value
            val idx = PlaybackQueue.currentChapterIndex(s.chapters, s.positionSec)
            val chapterEnd = s.chapters.getOrNull(idx + 1)?.startSec ?: s.totalDurationSec
            (chapterEnd - s.positionSec).toLong().coerceAtLeast(0)
        } else {
            ((endAt!! - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
        }

        _state.update { it.copy(sleepTimerRemainingSec = remainingSec) }
        if (remainingSec <= 0) {
            controller?.pause()
            sleepTimerEndAtMs = null
            sleepTimerEndOfChapter = false
            _state.update { it.copy(sleepTimerRemainingSec = null, sleepTimerEndOfChapter = false) }
        }
    }

    /**
     * Re-checks the server for a newer position (e.g. progress made on another device) and seeks
     * there if it differs meaningfully from where this device's session currently sits. Only acts
     * while paused — an actively playing session is always the source of truth for itself. Called
     * whenever the player screen (or mini-player) becomes visible again, since a long-lived app
     * process otherwise never re-fetches after the initial [loadAndPlay].
     */
    fun refreshIfStale() = scope.launch {
        val c = controller ?: return@launch
        if (c.isPlaying) return@launch
        val book = _state.value.currentBook ?: return@launch
        val files = _state.value.files
        if (files.isEmpty()) return@launch
        val resume = audioProgress.resolveResume(book.id) ?: return@launch
        val resumeIdx = files.indexOfFirst { it.id == resume.currentFileId }.coerceAtLeast(0)
        val resumeAbs = PlaybackQueue.toAbsoluteSec(files, resumeIdx, resume.positionSeconds)
        val currentAbs = c.currentPosition / 1000.0
        if (kotlin.math.abs(resumeAbs - currentAbs) > STALE_THRESHOLD_SEC) {
            c.seekTo(resumeIdx, (resume.positionSeconds * 1000).toLong())
            updatePosition()
        }
    }

    fun stop() = scope.launch {
        loadGeneration.incrementAndGet()
        report(force = true)
        controller?.run {
            stop()
            clearMediaItems()
        }
        pollJob?.cancel()
        cancelSleepTimer()
        _state.update { it.copy(currentBook = null, isPlaying = false) }
    }

    private fun startPoller() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                updatePosition()
                tickSleepTimer()
                if (System.currentTimeMillis() - lastReport >= 5_000) report(force = false)
                delay(500)
            }
        }
    }

    private fun updatePosition() {
        val c = controller ?: return
        if (_state.value.files.isEmpty()) return
        _state.update { it.copy(positionSec = c.currentPosition / 1000.0) }
    }

    private suspend fun report(force: Boolean) {
        val c = controller ?: return
        val book = _state.value.currentBook ?: return
        val files = _state.value.files
        if (files.isEmpty()) return
        if (!force && !c.isPlaying) return
        // c.currentPosition is the whole-book aggregate (see BookAggregatingPlayer); recompute the
        // real file + in-file offset together rather than pairing a stale currentMediaItemIndex with it.
        val loc = PlaybackQueue.locateAbsolute(files, c.currentPosition / 1000.0)
        val pct = PlaybackQueue.percentageFor(files, loc.index, loc.offsetSec)
        lastReport = System.currentTimeMillis()
        audioProgress.report(book.id, files[loc.index].id, loc.offsetSec, pct)
    }

    /** Friendly text for a Media3 playback failure - falls back to the exception's own message
     * (still logged in full above) when the error code isn't one of the common, expected cases. */
    private fun describePlayerError(error: PlaybackException): String = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        -> "Lost connection to the server while playing."
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        -> "The server couldn't serve this audiobook's file."
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        -> "This audio format isn't supported on this device."
        else -> error.message ?: "Playback failed unexpectedly."
    }

    private companion object {
        const val TAG = "BookOrbitPlayer"
        /** Minimum drift (seconds) before [refreshIfStale] bothers seeking to a server-side position. */
        const val STALE_THRESHOLD_SEC = 5.0
        /** How long a launch restore waits to load a book that has to come from the server. */
        const val RESTORE_RESOLVE_TIMEOUT_MS = 8_000L
    }
}
