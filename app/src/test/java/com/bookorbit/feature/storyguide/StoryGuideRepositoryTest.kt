package com.bookorbit.feature.storyguide

import com.bookorbit.core.model.*
import com.bookorbit.core.network.ApiService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class StoryGuideRepositoryTest {
    private val api = mockk<ApiService>()
    private val repo = StoryGuideRepository(api, Json { ignoreUnknownKeys = true })
    private val status = StoryGuideStatus(12, true, StoryGuideBuild(true), textKey = "edition-key")
    private fun missing() = Response.error<okhttp3.ResponseBody>(404, """{"reason":"none"}""".toResponseBody())

    @Test fun `opening guide reads status and content without ever generating`() = runTest {
        coEvery { api.getStoryGuideStatus(1, 12) } returns status
        coEvery { api.getStoryGuideContent(12, "1") } returns missing()
        val loaded = repo.load(1, 12)
        assertTrue(loaded.canRead)
        assertNull(loaded.content)
        coVerify(exactly = 0) { api.buildStoryGuide(any(), any()) }
    }

    @Test fun `production attestation denial prevents a potentially billable build`() = runTest {
        coEvery { api.getStoryGuideStatus(1, 12) } returns status
        coEvery { api.getStoryGuideContent(12, "1") } returns Response.error(403, "{}".toResponseBody())
        val loaded = repo.load(1, 12)
        assertEquals(StoryGuideRepository.Problem.RESTRICTED, loaded.problem)
        assertFalse(loaded.canRead)
        assertTrue(runCatching { repo.build(1, 12, "build") }.isFailure)
        assertTrue(runCatching { repo.retry(1, 12) }.isFailure)
        coVerify(exactly = 0) { api.buildStoryGuide(any(), any()) }
        coVerify(exactly = 0) { api.retryStoryGuide(any(), any()) }
    }

    @Test fun `wrong edition status cannot load or generate another editions guide`() = runTest {
        coEvery { api.getStoryGuideStatus(1, 12) } returns status.copy(fileId = 99)
        assertFalse(repo.load(1, 12).canRead)
        coVerify(exactly = 0) { api.getStoryGuideContent(any(), any()) }
    }

    @Test fun `different text revision is rejected rather than displaying wrong guide`() = runTest {
        coEvery { api.getStoryGuideStatus(1, 12) } returns status
        coEvery { api.getStoryGuideContent(12, "1") } returns Response.success("""{"format":1,"textKey":"other-edition","sections":[]}""".toResponseBody())
        assertEquals(StoryGuideRepository.Problem.FORMAT, repo.load(1, 12).problem)
    }

    @Test fun `explicit confirmed build sends exact file identity only after checking access`() = runTest {
        coEvery { api.getStoryGuideStatus(1, 12) } returns status
        coEvery { api.getStoryGuideContent(12, "1") } returns missing()
        coEvery { api.buildStoryGuide(1, StoryGuideBuildRequest(12, "build")) } returns Response.success(status.copy(job = StoryGuideJob("build", "queued")))
        assertEquals("queued", repo.build(1, 12, "build").job?.state)
        coVerify(exactly = 1) { api.buildStoryGuide(1, StoryGuideBuildRequest(12, "build")) }
    }

    @Test fun `cancelling panel does not swallow coroutine cancellation`() = runTest {
        coEvery { api.getStoryGuideStatus(1, 12) } throws CancellationException("closed")
        assertTrue(runCatching { repo.load(1, 12) }.exceptionOrNull() is CancellationException)
    }
}
