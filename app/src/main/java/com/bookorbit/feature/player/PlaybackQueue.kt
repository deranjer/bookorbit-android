package com.bookorbit.feature.player

import com.bookorbit.core.model.BookDetail
import com.bookorbit.core.model.BookFileRef
import com.bookorbit.core.model.BookFiles

/** Title used for the synthetic range covering audio before the first real chapter. */
const val INTRO_TITLE = "Intro"

/** Chapter resolved to absolute seconds across the whole book. */
data class ResolvedChapter(val title: String, val startSec: Double)

/** A chapter's `[startSec, endSec)` span in book time. */
data class ChapterRange(val index: Int, val title: String, val startSec: Double, val endSec: Double) {
    val lengthSec: Double get() = endSec - startSec
}

/** Whole-book offset located within a specific file: (file index, in-file offset). */
data class FileLocation(val index: Int, val offsetSec: Double)

/**
 * Playback math for the audiobook queue. ExoPlayer owns the queue natively (one MediaItem per
 * audio file); these helpers convert between whole-book offsets and (file, offset).
 */
object PlaybackQueue {
    fun audioFiles(book: BookDetail): List<BookFileRef> = BookFiles.audioFiles(book)

    fun performerLabel(book: BookDetail): String {
        val narrators = book.audioMetadata?.narrators ?: emptyList()
        return if (narrators.isNotEmpty()) narrators.joinToString(", ") { it.name }
        else book.authors.joinToString(", ") { it.name }
    }

    fun totalDurationSec(files: List<BookFileRef>): Double = sumDurationsSec(files.map { it.durationSeconds ?: 0.0 })

    fun toAbsoluteSec(files: List<BookFileRef>, index: Int, offsetSec: Double): Double =
        absoluteSecFrom(files.map { it.durationSeconds ?: 0.0 }, index, offsetSec)

    fun locateAbsolute(files: List<BookFileRef>, absoluteSec: Double): FileLocation =
        locateWithinDurations(files.map { it.durationSeconds ?: 0.0 }, absoluteSec)

    /** Duration-only variants so callers that only have raw durations (e.g. [BookAggregatingPlayer],
     * which reads them from [androidx.media3.common.MediaItem] metadata rather than [BookFileRef]s)
     * can share this exact math instead of duplicating it. Named distinctly from the [BookFileRef]
     * overloads above since `List<BookFileRef>` and `List<Double>` erase to the same JVM signature. */
    fun sumDurationsSec(durationsSec: List<Double>): Double = durationsSec.sum()

    fun absoluteSecFrom(durationsSec: List<Double>, index: Int, offsetSec: Double): Double {
        var abs = 0.0
        for (i in 0 until index) {
            if (i >= durationsSec.size) break
            abs += durationsSec[i]
        }
        return abs + offsetSec
    }

    fun locateWithinDurations(durationsSec: List<Double>, absoluteSec: Double): FileLocation {
        var remaining = absoluteSec.coerceAtLeast(0.0)
        for (i in durationsSec.indices) {
            val dur = durationsSec[i]
            if (i == durationsSec.lastIndex || remaining < dur) return FileLocation(i, remaining)
            remaining -= dur
        }
        return FileLocation(0, 0.0)
    }

    fun percentageFor(files: List<BookFileRef>, index: Int, offsetSec: Double): Double {
        val total = totalDurationSec(files)
        if (total <= 0) return 0.0
        return (toAbsoluteSec(files, index, offsetSec) / total * 100).coerceIn(0.0, 100.0)
    }

    /** The book's chapters, normalized (see [normalizeChapters]) against [totalSec]. */
    fun resolveChapters(book: BookDetail, totalSec: Double): List<ResolvedChapter> {
        val chapters = book.audioMetadata?.chapters ?: return emptyList()
        return normalizeChapters(chapters.map { ResolvedChapter(it.title, it.startMs / 1000.0) }, totalSec)
    }

    /**
     * Sorts, drops duplicate starts and starts at/after the end, and guarantees the first chapter
     * starts at 0 (snapping a near-zero start, else prepending an [INTRO_TITLE] chapter) so every
     * book position maps to exactly one range. Fewer than two chapters means "chapterless": empty.
     */
    fun normalizeChapters(raw: List<ResolvedChapter>, totalSec: Double): List<ResolvedChapter> {
        val sorted = raw.sortedBy { it.startSec }
            .distinctBy { it.startSec }
            .filter { totalSec <= 0 || it.startSec < totalSec }
        if (sorted.isEmpty()) return emptyList()
        val first = sorted.first()
        val anchored = if (first.startSec < INTRO_SNAP_SEC) {
            listOf(first.copy(startSec = 0.0)) + sorted.drop(1)
        } else {
            listOf(ResolvedChapter(INTRO_TITLE, 0.0)) + sorted
        }
        return if (anchored.size < 2) emptyList() else anchored
    }

    fun chapterRanges(chapters: List<ResolvedChapter>, totalSec: Double): List<ChapterRange> {
        if (chapters.isEmpty() || totalSec <= 0) return emptyList()
        return chapters.mapIndexed { i, c ->
            val end = chapters.getOrNull(i + 1)?.startSec ?: totalSec
            ChapterRange(i, c.title, c.startSec, end)
        }
    }

    fun chapterRange(chapters: List<ResolvedChapter>, totalSec: Double, posSec: Double): ChapterRange? {
        val ranges = chapterRanges(chapters, totalSec)
        if (ranges.isEmpty()) return null
        val pos = posSec.coerceIn(0.0, totalSec)
        return ranges.lastOrNull { it.startSec <= pos + 0.001 } ?: ranges.first()
    }

    fun toChapterTime(range: ChapterRange, bookSec: Double): Double =
        (bookSec - range.startSec).coerceIn(0.0, range.lengthSec)

    /** Clamped to `[start, end - 1 ms]` so seeking to a chapter's end stays in that chapter. */
    fun toBookTime(range: ChapterRange, chapterSec: Double): Double =
        (range.startSec + chapterSec).coerceIn(range.startSec, maxOf(range.startSec, range.endSec - 0.001))

    fun currentChapterIndex(chapters: List<ResolvedChapter>, absoluteSec: Double): Int {
        var found = -1
        for (i in chapters.indices) {
            if (chapters[i].startSec <= absoluteSec + 0.001) found = i else break
        }
        return found
    }

    private const val INTRO_SNAP_SEC = 1.0
}
