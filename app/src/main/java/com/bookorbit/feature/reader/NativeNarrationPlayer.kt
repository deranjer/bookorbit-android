package com.bookorbit.feature.reader

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/** Session-facing SMIL player. Keeps Media3's real clip-relative timeline and listener coordinates. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class NativeNarrationPlayer(
    player: Player,
    private val queue: NativeNarrationQueue,
    private val canPlay: () -> Boolean,
    private val beforePlay: () -> Unit,
) : ForwardingPlayer(player) {
    private var detached = false

    override fun play() {
        if (!allowed()) return
        beforePlay()
        super.play()
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        if (playWhenReady) {
            if (!allowed()) return
            beforePlay()
        }
        super.setPlayWhenReady(playWhenReady)
    }

    /** Never interpret another queue's positions as this edition's checkpoints. */
    fun checkpoint(): ReadAlongPosition? {
        if (!matchesQueue()) return null
        return queue.plan.timeline.checkpoint(currentMediaItemIndex, currentPosition,
            playbackParameters.speed.toDouble(), playbackState == Player.STATE_ENDED)
    }

    fun seekToSection(sectionIndex: Int): Boolean {
        if (!allowed()) return false
        val target = queue.plan.timeline.startAtSection(sectionIndex) ?: return false
        super.seekTo(target.index, target.clipOffsetMs)
        return true
    }

    // System next/previous advances actual SMIL sections, skipping un-narrated text sections.
    override fun seekToNext() = nextChapter()
    override fun seekToNextMediaItem() = nextChapter()
    override fun seekToPrevious() = previousChapter()
    override fun seekToPreviousMediaItem() = previousChapter()

    private fun nextChapter() {
        if (!allowed()) return
        val section = queue.plan.timeline.clips.getOrNull(currentMediaItemIndex)?.sectionIndex ?: return
        val target = queue.plan.timeline.nextChapter(section) ?: return
        super.seekTo(target.index, target.clipOffsetMs)
    }

    private fun previousChapter() {
        if (!allowed()) return
        val section = queue.plan.timeline.clips.getOrNull(currentMediaItemIndex)?.sectionIndex ?: return
        val targetSection = queue.plan.timeline.previousChapter(section)?.let { queue.plan.timeline.clips[it.index].sectionIndex }
            ?: section
        seekToSection(targetSection)
    }

    /** The service owns the delegate; switching wrappers must never release the shared ExoPlayer. */
    fun detach() { detached = true }

    private fun allowed() = !detached && canPlay() && matchesQueue()

    private fun matchesQueue(): Boolean {
        if (detached || mediaItemCount != queue.items.size) return false
        val index = currentMediaItemIndex
        return currentMediaItem?.mediaId == queue.items.getOrNull(index)?.mediaId
    }
}
