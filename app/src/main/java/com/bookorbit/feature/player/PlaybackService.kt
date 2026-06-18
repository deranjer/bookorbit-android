package com.bookorbit.feature.player

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Foreground service owning the ExoPlayer + [MediaLibraryService.MediaLibrarySession] for system
 * media controls (lock screen, notification, headset buttons) and Android Auto. As a
 * MediaLibraryService it also exposes a browsable content tree ([MediaLibraryCallback]) so the car
 * head unit can list and start audiobooks. The UI drives playback through a MediaController bound to
 * this session ([PlayerManager]).
 */
@UnstableApi
@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {

    @Inject
    lateinit var player: ExoPlayer

    @Inject
    lateinit var callback: MediaLibraryCallback

    private var mediaSession: MediaLibrarySession? = null

    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaLibrarySession.Builder(this, player, callback).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        // Stop the service if the user swipes the app away while paused.
        val sessionPlayer = mediaSession?.player
        if (sessionPlayer == null || !sessionPlayer.playWhenReady || sessionPlayer.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
