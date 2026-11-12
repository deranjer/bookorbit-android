package com.bookorbit.feature.player

import com.bookorbit.core.model.AudioProgress
import com.bookorbit.core.model.BookDetail
import com.bookorbit.core.model.BookFileRef
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoPlaybackResolverTest {
    private val repository = mockk<PlayerRepository>()
    private val progress = mockk<AudioProgressRepository>(relaxed = true)
    private val candidates = mockk<RestoreCandidateFinder>()
    private val resolver = AutoPlaybackResolver(repository, progress, candidates)
    private val book = BookDetail(1, 1, "Library", "", "", title = "Book")
    private val data = PlayerRepository.PlayerData(book, listOf(BookFileRef(10, "mp3", "AUDIOBOOK", durationSeconds = 60.0)), emptyList())

    @Test fun `resume tries the next candidate when a book is unavailable`() = runTest {
        val point = AudioProgress(10, 12.0, 20.0)
        coEvery { candidates.find() } returns listOf(2 to point, 1 to point)
        coEvery { repository.resolve(2) } returns null
        coEvery { repository.resolve(1) } returns data
        val resolved = resolver.resume()!!
        assertEquals(1, resolved.data.book.id)
        assertEquals(12_000, resolved.positionMs)
        coVerify(exactly = 0) { progress.resolveResume(any()) }
    }

    @Test fun `slow progress lookup uses the local point`() = runTest {
        coEvery { repository.resolve(1) } returns data
        coEvery { progress.resolveResume(1) } coAnswers { delay(60_000); null }
        coEvery { progress.localResumePoint(1) } returns ResumePoint(AudioProgress(10, 15.0, 25.0), 1)
        assertEquals(15_000, resolver.resolve(1)!!.positionMs)
    }

    @Test fun `a removed file restarts instead of seeking into the wrong file`() = runTest {
        coEvery { repository.resolve(1) } returns data
        assertEquals(0, resolver.resolve(1, resume = AudioProgress(99, 50.0, 80.0))!!.positionMs)
    }

    @Test fun `invalid chapter does not silently resume the book`() = runTest {
        coEvery { repository.resolve(1) } returns data
        assertNull(resolver.resolve(1, chapterIndex = 99))
    }

    @Test fun `slow book resolution has a deadline`() = runTest {
        coEvery { repository.resolve(1) } coAnswers { delay(60_000); data }
        assertNull(resolver.resolve(1))
    }

    @Test fun `resume offset is bounded by the file duration`() = runTest {
        coEvery { repository.resolve(1) } returns data
        assertEquals(60_000, resolver.resolve(1, resume = AudioProgress(10, 900.0, 50.0))!!.positionMs)
    }
}
