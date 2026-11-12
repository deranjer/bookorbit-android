package com.bookorbit.feature.player

import android.util.Log
import android.app.PendingIntent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.session.DefaultMediaNotificationProvider
import com.bookorbit.R
import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.bookorbit.app.MainActivity
import com.bookorbit.feature.cast.CastHandoff
import com.bookorbit.feature.cast.CastProxyServer
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import com.bookorbit.feature.downloads.DownloadsRepository
import com.bookorbit.feature.downloads.isCompleteDownloadStatus
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service owning the ExoPlayer + [MediaLibraryService.MediaLibrarySession] for system
 * media controls (lock screen, notification, headset buttons) and Android Auto. As a
 * MediaLibraryService it also exposes a browsable content tree ([MediaLibraryCallback]) so the car
 * head unit can list and start audiobooks. The UI drives playback through a MediaController bound to
 * this session ([PlayerManager]).
 *
 * Also owns the Cast handoff, via [castHandoff]: the cast player it hands [switchToCast] is swapped
 * in for [player] as the session's active player whenever a Cast session connects, and swapped back
 * out on disconnect - see [switchToCast]/[switchToLocal]. Every bound
 * [androidx.media3.session.MediaController] (including [PlayerManager]'s) keeps working
 * transparently across the swap since it only ever talks to the session, never a concrete player.
 * [CastHandoff] itself is flavor-specific (see its kdoc) so this class never references the Cast SDK
 * directly.
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

    @Inject
    lateinit var castHandoff: CastHandoff

    @Inject
    lateinit var activeBookPlayer: ActiveBookPlayer

    @Inject
    lateinit var livePrefs: LivePlaybackPrefs

    @Inject
    lateinit var playerManager: PlayerManager

    @Inject lateinit var downloads: DownloadsRepository
    @Inject lateinit var audioProgress: AudioProgressRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var lastNotifiedBookId: Int? = null

    private var mediaSession: MediaLibrarySession? = null
    private var usingCast = false
    private var earlyForeground = false

    override fun onCreate() {
        super.onCreate()
        // Wraps `player` so the session (and everything observing it - Bluetooth AVRCP, Android Auto,
        // lock screen, and PlayerManager's own MediaController) sees whole-book position/duration
        // instead of ExoPlayer's real per-file numbers. See BookAggregatingPlayer's kdoc.
        // Tapping the media notification / lock-screen controls must open the app.
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        mediaSession = MediaLibrarySession.Builder(this, wrap(player), callback)
            .setSessionActivity(openApp)
            .build()

        serviceScope.launch {
            playerManager.state
                .map { s -> s.currentBook?.id to PlaybackQueue.chapterRange(s.chapters, s.totalDurationSec, s.positionSec)?.index }
                .distinctUntilChanged()
                .collect { (bookId, _) ->
                    val session = mediaSession ?: return@collect
                    if (bookId != lastNotifiedBookId) {
                        lastNotifiedBookId = bookId
                        callback.refresh(session, setOf(AutoBrowseTree.ROOT_ID, AutoBrowseTree.CONTINUE_ID))
                    }
                    callback.refresh(session, setOf(AutoBrowseTree.NOW_PLAYING_CHAPTERS_ID))
                }
        }

        serviceScope.launch {
            combine(
                downloads.downloads.map { rows -> rows.filter { it.isAudiobook && it.status.isCompleteDownloadStatus() } },
                audioProgress.observeRecent().map { rows -> rows.map { it.bookId to (it.percentage >= ResumeCandidates.FINISHED_PERCENT) } },
            ) { books, history -> books to history }.distinctUntilChanged().drop(1).collect {
                mediaSession?.let { callback.refresh(it, setOf(AutoBrowseTree.DOWNLOADS_ID, AutoBrowseTree.CONTINUE_ID)) }
            }
        }

        castHandoff.initialize(this, onSessionAvailable = ::switchToCast, onSessionUnavailable = ::switchToLocal)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaSession

    private fun wrap(p: Player, cast: Boolean = false) = BookAggregatingPlayer(
        p, livePrefs.settings, if (cast) ({ castProxyServer.sourceQueue?.castItems }) else null,
        beforePlay = ::ensureForegroundBeforePlay,
    ).also { activeBookPlayer.current = it }

    /** API 35+ denies audio focus to a cold background service before the first play call. */
    private fun ensureForegroundBeforePlay() {
        if (Build.VERSION.SDK_INT < 35) return
        val session = mediaSession ?: return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = DefaultMediaNotificationProvider.DEFAULT_CHANNEL_ID
        manager.createNotificationChannel(NotificationChannel(channel, getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_orbit)
            .setContentTitle(session.player.mediaMetadata.title ?: getString(R.string.app_name))
            .setContentIntent(session.sessionActivity)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setOngoing(true).setSilent(true).build()
        // Reuse Media3's notification ID; its regular media controls replace this immediately.
        ServiceCompat.startForeground(this, DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID,
            notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        earlyForeground = true
    }

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        super.onUpdateNotification(session, startInForegroundRequired)
        if (earlyForeground && !startInForegroundRequired && !session.player.playWhenReady) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
            earlyForeground = false
        }
    }

    /** Moves playback from the local [player] to [cast], preserving position and play state. */
    private fun switchToCast(cast: Player) {
        if (usingCast) return
        val session = mediaSession ?: return
        val current = session.player

        val outgoing = activeBookPlayer.current ?: return
        val items = current.currentTimeline.mediaItems()
        if (items.isEmpty()) return

        // A failure here (no LAN address, unconvertible item) must not take the app down from inside a
        // Cast SDK callback; stay on the local player instead.
        val prepared = runCatching {
            castProxyServer.start()
            transferQueueToCast(player, cast, castProxyServer.rewriteForCast(items, player.currentMediaItemIndex, player.currentPosition))
        }
        if (prepared.isFailure) {
            Log.w("PlaybackService", "Cast handoff failed; retaining local playback", prepared.exceptionOrNull())
            castProxyServer.stop()
            return
        }

        outgoing.detach()
        val incoming = wrap(cast, cast = true)
        session.setPlayer(incoming)
        usingCast = true
    }

    /** Moves playback back from the cast player to the local [player], preserving position and play state. */
    private fun switchToLocal() {
        if (!usingCast) return
        val session = mediaSession ?: return
        val current = session.player

        val outgoing = activeBookPlayer.current ?: return
        val queue = castProxyServer.sourceQueue ?: return
        // Cast can still expose the previous book while its replacement is loading. In that case
        // restore the requested new queue and its resume point, never the stale receiver timeline.
        val location = castReturnPosition(queue, current.currentTimeline.mediaItems(), current.currentMediaItemIndex, outgoing.filePositionMs())
        val wasPlaying = current.playWhenReady

        current.pause()
        outgoing.detach()
        player.setMediaItems(queue.items, location.first, location.second)
        player.playbackParameters = current.playbackParameters
        player.prepare()
        val incoming = wrap(player)
        session.setPlayer(incoming)
        incoming.playWhenReady = wasPlaying
        usingCast = false

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
        serviceScope.cancel()
        if (earlyForeground) ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        // Release both possible players explicitly rather than through `mediaSession.player.release()`
        // - if the service is destroyed while casting, the session's active player is the cast one,
        // and that call alone would leak the local `player` (ExoPlayer) instance.
        castHandoff.release()
        activeBookPlayer.current?.detach()
        activeBookPlayer.current = null
        mediaSession?.release()
        mediaSession = null
        player.release()
        castProxyServer.stop()
        super.onDestroy()
    }
}
