package com.bookorbit.feature.player

import kotlin.math.roundToLong

/**
 * Pure book/chapter time math behind [BookAggregatingPlayer]: what the media session should report
 * for a given whole-book position, and where seeks/skips/next/previous land (always in book time).
 * [chapters] must already be normalized ([PlaybackQueue.normalizeChapters]).
 */
class ChapterClock(
    private val fileDurationsSec: List<Double>,
    private val chapters: List<ResolvedChapter>,
    mode: ProgressBarMode,
) {
    private val totalSec = PlaybackQueue.sumDurationsSec(fileDurationsSec)
    private val ranges = PlaybackQueue.chapterRanges(chapters, totalSec)

    val bookDurationMs: Long = (totalSec * 1000).roundToLong()
    val isChapterMode: Boolean = mode == ProgressBarMode.CHAPTER && ranges.size >= 2

    fun rangeAt(bookMs: Long): ChapterRange? =
        if (isChapterMode) PlaybackQueue.chapterRange(chapters, totalSec, bookMs / 1000.0) else null

    fun displayPositionMs(bookMs: Long): Long {
        val r = rangeAt(bookMs) ?: return bookMs
        return (bookMs - r.startMs()).coerceIn(0, r.lengthMs())
    }

    fun displayDurationMs(bookMs: Long): Long = rangeAt(bookMs)?.lengthMs() ?: bookDurationMs

    fun displayBufferedMs(bookMs: Long, bookBufferedMs: Long): Long {
        val r = rangeAt(bookMs) ?: return bookBufferedMs
        return (bookBufferedMs - r.startMs()).coerceIn(0, r.lengthMs())
    }

    fun bookTargetForDisplaySeek(currentBookMs: Long, displayMs: Long): Long {
        val r = rangeAt(currentBookMs) ?: return displayMs.coerceIn(0, bookDurationMs)
        return (PlaybackQueue.toBookTime(r, displayMs / 1000.0) * 1000).roundToLong()
    }

    fun bookTargetForOffset(currentBookMs: Long, deltaMs: Long): Long =
        (currentBookMs + deltaMs).coerceIn(0, bookDurationMs)

    fun navPointsSec(): List<Double> {
        if (chapters.size >= 2) return chapters.map { it.startSec }
        if (fileDurationsSec.size <= 1) return emptyList()
        return fileDurationsSec.indices.map { PlaybackQueue.absoluteSecFrom(fileDurationsSec, it, 0.0) }
    }

    fun nextNavTargetMs(bookMs: Long): Long? {
        val points = navPointsSec()
        return points.getOrNull(navIndex(points, bookMs) + 1)?.toMs()
    }

    fun hasNextNav(bookMs: Long): Boolean = nextNavTargetMs(bookMs) != null

    /** Smart previous: restart the current point if more than a few seconds in, else the prior one. */
    fun previousNavTargetMs(bookMs: Long): Long? {
        val points = navPointsSec()
        if (points.isEmpty()) return null
        val idx = navIndex(points, bookMs)
        val intoCurrent = bookMs / 1000.0 - points[idx]
        val target = if (intoCurrent > PREVIOUS_RESTART_THRESHOLD_SEC) idx else (idx - 1).coerceAtLeast(0)
        return points[target].toMs()
    }

    private fun navIndex(points: List<Double>, bookMs: Long): Int {
        val sec = bookMs / 1000.0
        var found = 0
        for (i in points.indices) if (points[i] <= sec + 0.001) found = i else break
        return found
    }

    private fun ChapterRange.startMs() = (startSec * 1000).roundToLong()
    private fun ChapterRange.lengthMs() = (lengthSec * 1000).roundToLong()
    private fun Double.toMs() = (this * 1000).roundToLong()

    companion object {
        const val PREVIOUS_RESTART_THRESHOLD_SEC = 3.0
    }
}
