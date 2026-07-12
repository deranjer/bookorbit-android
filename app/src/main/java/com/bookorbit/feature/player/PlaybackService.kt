package com.bookorbit.feature.player

import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.bookorbit.feature.cast.CastProxyServer
import com.bookorbit.feature.cast.sharedCastContextOrNull
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Foreground service owning the ExoPlayer + [MediaLibraryService.MediaLibrarySession] for system
 * media controls (lock screen, notification, headset buttons) and Android Auto. As a
 * MediaLibraryService it also exposes a browsable content tree ([MediaLibraryCallback]) so the car
 * head unit can list and start audiobooks. The UI drives playback through a MediaController bound to
 * this session ([PlayerManager]).
 *
 * Also owns the Cast handoff: [castPlayer] (built lazily, guarded for devices without Google Play
 * Services) is swapped in for [player] as the session's active player whenever a Cast session
 * connects, and swapped back out on disconnect - see [switchToCast]/[switchToLocal]. Every bound
 * [androidx.media3.session.MediaController] (including [PlayerManager]'s) keeps working
 * transparently across the swap since it only ever talks to the session, never a concrete player.
 */
@UnstableApi
@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {

    @Inject
    lateinit var player: ExoPlayer

    @Inject
    lateinit var callback: MediaLibraryCallback

    @Inject
    lateinit var castProxyServer: CastProxyServer

    private var mediaSession: MediaLibrarySession? = null
    private var castPlayer: CastPlayer? = null

    private val castSessionListener = object : SessionAvailabilityListener {
        override fun onCastSessionAvailable() = switchToCast()
        override fun onCastSessionUnavailable() = switchToLocal()
    }

    override fun onCreate() {
        super.onCreate()
        // Wraps `player` so the session (and everything observing it - Bluetooth AVRCP, Android Auto,
        // lock screen, and PlayerManager's own MediaController) sees whole-book position/duration
        // instead of ExoPlayer's real per-file numbers. See BookAggregatingPlayer's kdoc.
        mediaSession = MediaLibrarySession.Builder(this, BookAggregatingPlayer(player), callback).build()

        sharedCastContextOrNull(this)?.let { ctx ->
            castPlayer = CastPlayer(ctx).apply { setSessionAvailabilityListener(castSessionListener) }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaSession

    /** Moves playback from the local [player] to [castPlayer], preserving position and play state. */
    private fun switchToCast() {
        val cast = castPlayer ?: return
        val session = mediaSession ?: return
        val current = session.player

        val absoluteMs = current.currentPosition
        val wasPlaying = current.playWhenReady
        val items = current.currentTimeline.mediaItems()
        if (items.isEmpty()) return

        castProxyServer.start()
        val rewritten = castProxyServer.rewriteForCast(items)

        cast.setMediaItems(rewritten)
        cast.prepare()

        session.setPlayer(BookAggregatingPlayer(cast))
        session.player.seekTo(absoluteMs)
        session.player.playWhenReady = wasPlaying
    }

    /** Moves playback back from [castPlayer] to the local [player], preserving position and play state. */
    private fun switchToLocal() {
        val session = mediaSession ?: return
        val current = session.player

        val absoluteMs = current.currentPosition
        val wasPlaying = current.playWhenReady

        session.setPlayer(BookAggregatingPlayer(player))
        session.player.seekTo(absoluteMs)
        session.player.playWhenReady = wasPlaying

        castProxyServer.stop()
    }

    private fun Timeline.mediaItems(): List<androidx.media3.common.MediaItem> {
        if (isEmpty) return emptyList()
        val window = Timeline.Window()
        return (0 until windowCount).map { getWindow(it, window).mediaItem }
    }

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        // Stop the service if the user swipes the app away while paused.
        val sessionPlayer = mediaSession?.player
        if (sessionPlayer == null || !sessionPlayer.playWhenReady || sessionPlayer.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        // Release both possible players explicitly rather than through `mediaSession.player.release()`
        // - if the service is destroyed while casting, the session's active player is `castPlayer`,
        // and that call alone would leak the local `player` (ExoPlayer) instance.
        castPlayer?.setSessionAvailabilityListener(null)
        mediaSession?.release()
        mediaSession = null
        castPlayer?.release()
        player.release()
        castProxyServer.stop()
        super.onDestroy()
    }
}
