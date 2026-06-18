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

    fun totalDurationSec(files: List<BookFileRef>): Double =
        files.sumOf { it.durationSeconds ?: 0.0 }

    fun toAbsoluteSec(files: List<BookFileRef>, index: Int, offsetSec: Double): Double {
        var abs = 0.0
        for (i in 0 until index) {
            if (i >= files.size) break
            abs += files[i].durationSeconds ?: 0.0
        }
        return abs + offsetSec
    }

    fun locateAbsolute(files: List<BookFileRef>, absoluteSec: Double): FileLocation {
        var remaining = absoluteSec.coerceAtLeast(0.0)
        for (i in files.indices) {
            val dur = files[i].durationSeconds ?: 0.0
            if (i == files.lastIndex || remaining < dur) return FileLocation(i, remaining)
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
