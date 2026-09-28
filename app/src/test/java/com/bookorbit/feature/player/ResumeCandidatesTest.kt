package com.bookorbit.feature.player

import org.junit.Assert.assertEquals
import org.junit.Test

/** Which book the app reopens on launch ([ResumeCandidates.rank]). */
class ResumeCandidatesTest {

    private fun c(bookId: Int, percentage: Double, at: Long) = ResumeCandidate(bookId, percentage, at)

    @Test
    fun `most recently active book comes first regardless of source order`() {
        val local = c(1, 40.0, at = 1_000)
        val server = c(2, 10.0, at = 5_000) // listened to later on another device
        assertEquals(listOf(2, 1), ResumeCandidates.rank(listOf(local, server)))
    }

    @Test
    fun `finished books are skipped`() {
        assertEquals(listOf(1), ResumeCandidates.rank(listOf(c(1, 50.0, 1_000), c(2, 99.5, 9_000), c(3, 100.0, 8_000))))
    }

    @Test
    fun `ties keep input order so the local candidate wins`() {
        assertEquals(listOf(1, 2), ResumeCandidates.rank(listOf(c(1, 10.0, 0), c(2, 10.0, 0))))
    }

    @Test
    fun `the same book from both sources is listed once`() {
        assertEquals(listOf(7), ResumeCandidates.rank(listOf(c(7, 30.0, 2_000), c(7, 30.0, 2_000))))
    }

    @Test
    fun `nothing to restore yields an empty list`() {
        assertEquals(emptyList<Int>(), ResumeCandidates.rank(emptyList()))
        assertEquals(emptyList<Int>(), ResumeCandidates.rank(listOf(c(1, 100.0, 1_000))))
    }
}
