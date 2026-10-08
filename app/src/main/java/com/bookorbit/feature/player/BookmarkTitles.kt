package com.bookorbit.feature.player

/** Default names for a new bookmark, and the time format shown beside it. */
object BookmarkTitles {
    /** "1:02:03" or "2:03" from a position in seconds. */
    fun time(positionSec: Double): String {
        val total = positionSec.toLong().coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    /**
     * The time first (so it survives truncation in a narrow list), then the chapter name, or just the
     * time when the book has no chapters.
     */
    fun default(chapterTitle: String?, positionSec: Double): String {
        val chapter = chapterTitle?.trim()?.takeIf { it.isNotEmpty() } ?: return time(positionSec)
        return "${time(positionSec)} · $chapter"
    }
}
