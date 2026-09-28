package com.bookorbit.feature.player

import com.bookorbit.core.db.AudioProgressEntity
import com.bookorbit.core.model.AudioProgress
import com.bookorbit.core.model.BookCard
import com.bookorbit.feature.browse.BrowseRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** How [RestoreCandidateFinder] combines local history with the server's Continue Listening. */
class RestoreCandidateFinderTest {

    private lateinit var progress: AudioProgressRepository
    private lateinit var browse: BrowseRepository
    private lateinit var finder: RestoreCandidateFinder

    private fun row(bookId: Int, pct: Double) = AudioProgressEntity(bookId, 10, 1.0, pct, updatedAt = 0L, dirty = false)
    private fun card(id: Int) = BookCard(id = id, addedAt = "x")
    private fun point(pct: Double, at: Long, fileId: Int = 10) = ResumePoint(AudioProgress(fileId, 1.0, pct), at)

    @Before
    fun setUp() {
        progress = mockk(relaxed = true)
        browse = mockk()
        coEvery { progress.flushPending() } returns true
        finder = RestoreCandidateFinder(progress, browse)
    }

    @Test
    fun `a book listened to later on another device comes first`() = runTest {
        coEvery { progress.recent() } returns listOf(row(1, 40.0))
        coEvery { browse.scroller(any(), any(), any()) } returns listOf(card(2))
        coEvery { progress.resumePoint(1, false) } returns point(40.0, at = 1_000)
        coEvery { progress.resumePoint(2, false) } returns point(10.0, at = 5_000, fileId = 22)

        val found = finder.find()

        assertEquals(listOf(2, 1), found.map { it.first })
        assertEquals(22, found.first().second.currentFileId)
    }

    @Test
    fun `server list failure falls back to the local book`() = runTest {
        coEvery { progress.recent() } returns listOf(row(1, 40.0))
        coEvery { browse.scroller(any(), any(), any()) } throws RuntimeException("offline")
        coEvery { progress.resumePoint(1, false) } returns point(40.0, at = 1_000)

        assertEquals(listOf(1), finder.find().map { it.first })
    }

    @Test
    fun `a slow server lookup falls back to the local position`() = runTest {
        coEvery { progress.recent() } returns listOf(row(1, 40.0))
        coEvery { browse.scroller(any(), any(), any()) } returns emptyList()
        coEvery { progress.resumePoint(1, false) } coAnswers { delay(60_000); point(40.0, at = 9_999) }
        coEvery { progress.localResumePoint(1) } returns point(40.0, at = 1_000)

        assertEquals(listOf(1), finder.find().map { it.first })
        coVerify { progress.localResumePoint(1) }
    }

    @Test
    fun `a server book that can't be looked up is dropped`() = runTest {
        coEvery { progress.recent() } returns emptyList()
        coEvery { browse.scroller(any(), any(), any()) } returns listOf(card(3), card(4))
        coEvery { progress.resumePoint(3, false) } returns null
        coEvery { progress.localResumePoint(3) } returns null
        coEvery { progress.resumePoint(4, false) } returns point(20.0, at = 2_000)

        assertEquals(listOf(4), finder.find().map { it.first })
    }

    @Test
    fun `the local book also on the server is looked up once`() = runTest {
        coEvery { progress.recent() } returns listOf(row(1, 40.0))
        coEvery { browse.scroller(any(), any(), any()) } returns listOf(card(1))
        coEvery { progress.resumePoint(1, false) } returns point(40.0, at = 1_000)

        assertEquals(listOf(1), finder.find().map { it.first })
        coVerify(exactly = 1) { progress.resumePoint(1, false) }
    }

    @Test
    fun `finished books are never offered, locally or from the server`() = runTest {
        coEvery { progress.recent() } returns listOf(row(1, 100.0), row(5, 30.0))
        coEvery { browse.scroller(any(), any(), any()) } returns listOf(card(2))
        coEvery { progress.resumePoint(5, false) } returns point(30.0, at = 1_000)
        coEvery { progress.resumePoint(2, false) } returns point(99.8, at = 9_000) // finished on the web

        assertEquals(listOf(5), finder.find().map { it.first })
    }
}
