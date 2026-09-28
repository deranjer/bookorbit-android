package com.bookorbit.feature.player

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.StateFlow

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
 * The book-time accessors ([bookPositionMs], [bookDurationMs], [bookBufferedPositionMs],
 * [seekToBookMs]) always report whole-book time; in-process code such as [PlayerManager] reads them
 * via [ActiveBookPlayer] so it stays correct even if the session-facing position becomes chapter-relative.
 *
 * Per-file durations and book-wide chapter start times are read from [androidx.media3.common.MediaItem]
 * metadata extras ([PlayerRepository] embeds them there) rather than [Timeline.Window.durationUs],
 * which is unset for queue items ExoPlayer hasn't buffered yet.
 */
@UnstableApi
class BookAggregatingPlayer(
    player: Player,
    private val prefs: StateFlow<AudioSettings>,
) : ForwardingPlayer(player) {

    private val window = Timeline.Window()

    override fun getDuration(): Long = bookDurationMs()

    override fun getCurrentPosition(): Long = bookPositionMs()

    override fun getBufferedPosition(): Long = bookBufferedPositionMs()

    override fun getContentDuration(): Long = duration

    override fun getContentPosition(): Long = currentPosition

    override fun getContentBufferedPosition(): Long = bufferedPosition

    override fun seekTo(positionMs: Long) = seekToBookMs(positionMs)

    private fun clock(mode: ProgressBarMode = ProgressBarMode.BOOK): ChapterClock =
        ChapterClock(fileDurationsSec(), chaptersFromExtras(), mode)

    private fun chaptersFromExtras(): List<ResolvedChapter> {
        val extras = currentMediaItem?.mediaMetadata?.extras ?: return emptyList()
        val starts = extras.getDoubleArray(EXTRA_CHAPTER_STARTS_SEC) ?: return emptyList()
        val titles = extras.getStringArray(EXTRA_CHAPTER_TITLES)
        return starts.mapIndexed { i, s -> ResolvedChapter(titles?.getOrNull(i) ?: "Chapter ${i + 1}", s) }
            .takeIf { it.size >= 2 } ?: emptyList()
    }

    fun bookPositionMs(): Long = absoluteMs(super.getCurrentPosition())
    fun bookBufferedPositionMs(): Long = absoluteMs(super.getBufferedPosition())
    fun bookDurationMs(): Long = clock().bookDurationMs

    fun seekToBookMs(positionMs: Long) {
        val loc = PlaybackQueue.locateWithinDurations(fileDurationsSec(), positionMs / 1000.0)
        super.seekTo(loc.index, (loc.offsetSec * 1000).toLong())
    }

    /** Stops wrapper-owned work (a later task adds a poller); call when the session drops this wrapper.
     * Does not release the wrapped player. */
    fun detach() {}

    override fun seekToNext() = jumpToNavPoint(forward = true)
    override fun seekToNextMediaItem() = jumpToNavPoint(forward = true)
    override fun seekToPrevious() = jumpToNavPoint(forward = false)
    override fun seekToPreviousMediaItem() = jumpToNavPoint(forward = false)

    override fun hasNextMediaItem(): Boolean {
        val c = clock()
        if (c.navPointsSec().size <= 1) return super.hasNextMediaItem()
        return c.hasNextNav(bookPositionMs())
    }

    override fun hasPreviousMediaItem(): Boolean {
        if (clock().navPointsSec().size <= 1) return super.hasPreviousMediaItem()
        return true
    }

    override fun getAvailableCommands(): Player.Commands {
        val base = super.getAvailableCommands()
        if (clock().navPointsSec().size <= 1) return base
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
        val c = clock()
        if (c.navPointsSec().size <= 1) {
            if (forward) super.seekToNextMediaItem() else super.seekToPreviousMediaItem()
            return
        }
        val now = bookPositionMs()
        val target = (if (forward) c.nextNavTargetMs(now) else c.previousNavTargetMs(now)) ?: return
        seekToBookMs(target)
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
        const val EXTRA_CHAPTER_TITLES = "com.bookorbit.chapter_titles"
    }
}
