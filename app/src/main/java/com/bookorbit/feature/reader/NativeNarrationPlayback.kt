package com.bookorbit.feature.reader

import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.bookorbit.core.auth.SessionManager
import com.bookorbit.feature.sessions.NarrationHandle
import com.bookorbit.feature.sessions.ReadingSessionTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Owns narration on the service's existing player; never creates a second audio output. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class NativeNarrationPlayback(
    private val player: ExoPlayer,
    private val mediaSession: MediaSession,
    private val session: SessionManager,
    private val positions: ReadAlongPositionStore,
    private val activities: ReadingSessionTracker,
    private val shared: NativeNarrationSession,
    private val scope: CoroutineScope,
    private val beforePlay: () -> Unit,
    private val beforeNarration: () -> Unit,
    private val restoreAudiobook: () -> Unit,
) : NativeNarrationSession.Port {
    private var queue: NativeNarrationQueue? = null
    private var owner: ReadAlongOwner? = null
    private var wrapper: NativeNarrationPlayer? = null
    private var activity: NarrationHandle? = null
    private var poll: Job? = null
    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) { update() }
        override fun onPlaybackStateChanged(playbackState: Int) { update() }
        override fun onPlayerError(error: PlaybackException) { update("audio") }
    }
    private val accountObserver = scope.launch {
        session.state.collect { queue?.let { if (!session.owns(it.plan.owner)) leave() } }
    }

    fun release() { leave(); accountObserver.cancel() }

    override fun start(queue: NativeNarrationQueue, owner: ReadAlongOwner, saved: ReadAlongPosition?, section: Int) {
        require(session.owns(queue.plan.owner) && owner.fileId == queue.plan.timeline.fileId &&
            owner.server == queue.plan.owner.serverUrl && owner.userId == queue.plan.owner.userId)
        val location = queue.initialPosition(saved?.takeUnless { it.completed }, section)
            ?: error("No narrated section at this position")
        val factory = queue.sourceFactory()
        val sources = queue.items.map(factory::createMediaSource)
        leave()
        beforeNarration()
        player.pause()
        this.queue = queue
        this.owner = owner
        activity = activities.beginNarration(owner.fileId, queue.plan.owner)
        player.setMediaSources(sources, location.index, location.clipOffsetMs)
        val native = NativeNarrationPlayer(player, queue, { session.owns(queue.plan.owner) }, beforePlay)
        wrapper = native
        mediaSession.setPlayer(native)
        player.addListener(listener)
        player.setPlaybackSpeed((saved?.rate ?: 1.0).toFloat())
        player.prepare()
        native.play()
        update()
        poll = scope.launch {
            while (isActive) {
                if (!session.owns(queue.plan.owner)) { leave(); break }
                update()
                delay(1000)
            }
        }
    }

    override fun listenFrom(position: ReadAlongPosition): Boolean {
        val current = wrapper ?: return false
        val queue = queue ?: return false
        if (!session.owns(queue.plan.owner) || !current.seekToSelection(position)) return false
        if (current.playerError != null) current.prepare()
        current.play()
        update()
        return true
    }

    override fun command(action: String, rate: Double?) {
        val current = wrapper ?: return
        val queue = queue ?: return
        if (!session.owns(queue.plan.owner)) { leave(); return }
        when (action) {
            "pause" -> current.pause()
            "start", "resume" -> {
                if (current.playerError != null) current.prepare()
                if (current.playbackState == Player.STATE_ENDED) {
                    queue.plan.timeline.clips.getOrNull(current.currentMediaItemIndex)?.let { current.seekToSection(it.sectionIndex) }
                }
                current.play()
            }
            "stop" -> { leave(); return }
            "restart" -> {
                queue.plan.timeline.clips.getOrNull(current.currentMediaItemIndex)?.let { current.seekToSection(it.sectionIndex) }
                current.play()
            }
            "next" -> if (current.currentMediaItemIndex < current.mediaItemCount - 1) current.seekTo(current.currentMediaItemIndex + 1, 0)
            "prev" -> if (current.currentMediaItemIndex > 0) current.seekTo(current.currentMediaItemIndex - 1, 0)
            "rate" -> if (rate != null && rate.isFinite() && rate in 0.5..2.0) current.setPlaybackSpeed(rate.toFloat())
        }
        update()
    }

    private fun update(error: String? = null) {
        val native = wrapper ?: return
        val queue = queue ?: return
        val owner = owner ?: return
        if (!session.owns(queue.plan.owner)) return
        val position = native.checkpoint()
        if (position != null) positions.record(owner, position)
        activity?.let { activities.setNarrationActive(it, native.isPlaying) }
        val state = when {
            error != null || native.playerError != null -> "error"
            native.playbackState == Player.STATE_ENDED -> "ended"
            native.playbackState == Player.STATE_BUFFERING -> "loading"
            native.isPlaying -> "playing"
            else -> "paused"
        }
        shared.publish(NativeNarrationSession.State(owner, queue.plan.timeline.bookId,
            ReaderEvent.ReadAlong(true, state, native.playbackParameters.speed.toDouble(), error, position)))
    }

    override fun leave() {
        if (wrapper == null && queue == null) return
        update()
        poll?.cancel(); poll = null
        player.removeListener(listener)
        player.pause()
        wrapper?.detach(); wrapper = null
        activity?.let(activities::endNarration); activity = null
        val released = queue
        queue = null; owner = null
        player.clearMediaItems()
        released?.plan?.close()
        shared.publish(null)
        restoreAudiobook()
    }
}
