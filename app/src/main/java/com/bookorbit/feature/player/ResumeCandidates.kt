package com.bookorbit.feature.player

/**
 * A book the app could reopen on launch, with its newest known progress (local or server) and when
 * that progress was captured. Timestamps are writer clocks (this device, or whichever device last
 * saved to the server), so books listened to within a clock-skew window of each other on different
 * devices may rank in either order.
 */
data class ResumeCandidate(val bookId: Int, val percentage: Double, val lastActivityMillis: Long)

object ResumeCandidates {
    /** Progress at or above this is treated as finished and never reopened. */
    const val FINISHED_PERCENT = 99.5

    /**
     * Unfinished candidates, most recently active first. The sort is stable, so on equal timestamps
     * the caller's order wins (callers list the local candidate first).
     */
    fun rank(candidates: List<ResumeCandidate>): List<Int> =
        candidates
            .filter { it.percentage < FINISHED_PERCENT }
            .distinctBy { it.bookId }
            .sortedByDescending { it.lastActivityMillis }
            .map { it.bookId }
}
