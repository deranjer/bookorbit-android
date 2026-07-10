package com.bookorbit.feature.player

import com.bookorbit.core.model.BookDetail
import com.bookorbit.core.model.BookFileRef
import com.bookorbit.core.model.BookFiles

/** Chapter resolved to absolute seconds across the whole book. */
data class ResolvedChapter(val title: String, val startSec: Double)

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

    fun resolveChapters(book: BookDetail): List<ResolvedChapter> {
        val chapters = book.audioMetadata?.chapters ?: return emptyList()
        return chapters.map { ResolvedChapter(it.title, it.startMs / 1000.0) }.sortedBy { it.startSec }
    }

    fun currentChapterIndex(chapters: List<ResolvedChapter>, absoluteSec: Double): Int {
        var found = -1
        for (i in chapters.indices) {
            if (chapters[i].startSec <= absoluteSec + 0.001) found = i else break
        }
        return found
    }
}
