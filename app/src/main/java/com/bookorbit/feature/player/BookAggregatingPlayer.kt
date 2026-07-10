package com.bookorbit.feature.player

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.Timeline

/**
 * Presents the whole-book position/duration to every observer of the [PlaybackService]'s
 * [androidx.media3.session.MediaSession] (Bluetooth AVRCP, Android Auto, lock screen, and the app's
 * own [PlayerManager]) instead of the real per-file numbers ExoPlayer natively reports. The book is
 * still a real multi-item queue underneath (needed for per-file streaming/downloads) - this only
 * virtualizes the position/duration math and the next/previous command behavior. Queue identity
 * ([getCurrentMediaItemIndex], [getMediaItemCount], [getCurrentTimeline], etc.) is deliberately left
 * as a real pass-through: faking it while [Player.Listener] events still fire with real per-item data
 * (ForwardingPlayer re-registers listeners directly on the wrapped player) would desync the session's
 * internal PlaybackState/PositionInfo bookkeeping.
 *
 * Per-file durations and book-wide chapter start times are read from [androidx.media3.common.MediaItem]
 * metadata extras ([PlayerRepository] embeds them there) rather than [Timeline.Window.durationUs],
 * which is unset for queue items ExoPlayer hasn't buffered yet.
 */
class BookAggregatingPlayer(player: Player) : ForwardingPlayer(player) {

    private val window = Timeline.Window()

    override fun getDuration(): Long = (PlaybackQueue.sumDurationsSec(fileDurationsSec()) * 1000).toLong()

    override fun getCurrentPosition(): Long = absoluteMs(super.getCurrentPosition())

    override fun getBufferedPosition(): Long = absoluteMs(super.getBufferedPosition())

    override fun getContentDuration(): Long = duration

    override fun getContentPosition(): Long = currentPosition

    override fun getContentBufferedPosition(): Long = bufferedPosition

    override fun seekTo(positionMs: Long) {
        val loc = PlaybackQueue.locateWithinDurations(fileDurationsSec(), positionMs / 1000.0)
        super.seekTo(loc.index, (loc.offsetSec * 1000).toLong())
    }

    override fun seekToNext() = jumpToNavPoint(forward = true)
    override fun seekToNextMediaItem() = jumpToNavPoint(forward = true)
    override fun seekToPrevious() = jumpToNavPoint(forward = false)
    override fun seekToPreviousMediaItem() = jumpToNavPoint(forward = false)

    override fun hasNextMediaItem(): Boolean {
        val points = navPoints()
        if (points.size <= 1) return super.hasNextMediaItem()
        return navPointIndex(points, currentPosition / 1000.0) < points.lastIndex
    }

    override fun hasPreviousMediaItem(): Boolean {
        val points = navPoints()
        if (points.size <= 1) return super.hasPreviousMediaItem()
        return true
    }

    override fun getAvailableCommands(): Player.Commands {
        val base = super.getAvailableCommands()
        if (navPoints().size <= 1) return base
        val builder = base.buildUpon()
        if (hasNextMediaItem()) {
            builder.add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            builder.add(Player.COMMAND_SEEK_TO_NEXT)
        }
        if (hasPreviousMediaItem()) {
            builder.add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            builder.add(Player.COMMAND_SEEK_TO_PREVIOUS)
        }
        return builder.build()
    }

    private fun absoluteMs(inItemPositionMs: Long): Long =
        (PlaybackQueue.absoluteSecFrom(fileDurationsSec(), currentMediaItemIndex, inItemPositionMs / 1000.0) * 1000).toLong()

    /** Jumps to the next/previous chapter when the book has chapter metadata, else the next/previous
     * file boundary (matching the pre-aggregation behavior for chapter-less books). "Previous" follows
     * the standard smart-previous convention: restart the current point if more than a few seconds into
     * it, otherwise go to the prior point. */
    private fun jumpToNavPoint(forward: Boolean) {
        val points = navPoints()
        if (points.size <= 1) {
            if (forward) super.seekToNextMediaItem() else super.seekToPreviousMediaItem()
            return
        }
        val absNowSec = currentPosition / 1000.0
        val idx = navPointIndex(points, absNowSec)
        val targetSec = if (forward) {
            points.getOrNull(idx + 1) ?: return
        } else {
            val intoCurrent = absNowSec - points[idx]
            val targetIdx = if (intoCurrent > PREVIOUS_RESTART_THRESHOLD_SEC) idx else (idx - 1).coerceAtLeast(0)
            points[targetIdx]
        }
        seekTo((targetSec * 1000).toLong())
    }

    private fun navPointIndex(points: List<Double>, absoluteSec: Double): Int {
        var found = 0
        for (i in points.indices) {
            if (points[i] <= absoluteSec + 0.001) found = i else break
        }
        return found
    }

    /** Chapter start times (book-wide, absolute seconds) when the book has >= 2 chapters, else the
     * start-of-each-file offsets when there are >= 2 files, else empty (nothing meaningful to jump to). */
    private fun navPoints(): List<Double> {
        chapterStartsSec()?.let { return it }
        val durations = fileDurationsSec()
        if (durations.size <= 1) return emptyList()
        return durations.indices.map { i -> PlaybackQueue.absoluteSecFrom(durations, i, 0.0) }
    }

    private fun chapterStartsSec(): List<Double>? {
        val extras = currentMediaItem?.mediaMetadata?.extras ?: return null
        val starts = extras.getDoubleArray(EXTRA_CHAPTER_STARTS_SEC) ?: return null
        return starts.toList().takeIf { it.size >= 2 }
    }

    private fun fileDurationsSec(): List<Double> {
        val timeline = currentTimeline
        if (timeline.isEmpty) return emptyList()
        return (0 until timeline.windowCount).map { i ->
            timeline.getWindow(i, window).mediaItem.mediaMetadata.extras?.getDouble(EXTRA_DURATION_SEC) ?: 0.0
        }
    }

    companion object {
        const val EXTRA_DURATION_SEC = "com.bookorbit.duration_sec"
        const val EXTRA_CHAPTER_STARTS_SEC = "com.bookorbit.chapter_starts_sec"
        private const val PREVIOUS_RESTART_THRESHOLD_SEC = 3.0
    }
}
