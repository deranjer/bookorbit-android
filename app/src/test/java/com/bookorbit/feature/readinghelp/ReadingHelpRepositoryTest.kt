package com.bookorbit.feature.readinghelp

import com.bookorbit.core.model.AppInfo
import com.bookorbit.core.model.ReadingHelpCapability
import com.bookorbit.core.model.ReadingHelpRequest
import com.bookorbit.core.network.ApiService
import io.mockk.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

class ReadingHelpRepositoryTest {
    private val api = mockk<ApiService>()
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val repo = ReadingHelpRepository(api, json)
    private val request = ReadingHelpRequest("recap-note", 73, "ru", listOf("Exact selected excerpt"))

    private fun access(code: Int = 400, error: String = "{}", enabled: Boolean = true) {
        coEvery { api.getAppInfo() } returns AppInfo("3.9.0", features = listOf("reading-help"), readingHelp = ReadingHelpCapability(enabled))
        coEvery { api.probeReadingHelp(any()) } returns Response.error(code, error.toResponseBody())
    }

    @Test fun `opening restricted capability sends only empty invalid DTO never excerpts`() = runBlocking {
        access(403)
        assertEquals(ReadingHelpRepository.Problem.RESTRICTED, repo.access().problem)
        assertTrue(runCatching { repo.answer(request).toList() }.exceptionOrNull() is ReadingHelpRepository.Failure)
        coVerify { api.probeReadingHelp(match { it.isEmpty() }) }
        verify(exactly = 0) { api.readingHelp(any()) }
    }

    @Test fun `disabled server and unsupported careful model do not start generation`() = runBlocking {
        access(enabled = false)
        assertEquals(ReadingHelpRepository.Problem.DISABLED, repo.access().problem)
        assertTrue(runCatching { repo.answer(request).toList() }.isFailure)
        access()
        assertTrue(runCatching { repo.answer(request.copy(careful = true)).toList() }.isFailure)
        verify(exactly = 0) { api.readingHelp(any()) }
    }

    @Test fun `provider configuration and attestation errors remain distinguishable without newline`() = runBlocking {
        access(403, """{"errorCode":"APP_ATTEST_REQUIRED"}""")
        assertEquals(ReadingHelpRepository.Problem.RESTRICTED, repo.access().problem)
        access(403, """{"errorCode":"READING_HELP_BUDGET"}""")
        assertEquals(ReadingHelpRepository.Problem.BUDGET, repo.access().problem)
        access(403, """{"errorCode":"READING_HELP_OFF"}""")
        assertEquals(ReadingHelpRepository.Problem.DISABLED, repo.access().problem)
    }

    @Test fun `streamed answer uses exact file and only explicitly supplied passage`() = runBlocking {
        access()
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(
                "data: {\"type\":\"delta\",\"text\":\"part\"}\n\ndata: {\"type\":\"done\",\"text\":\"answer\"}\n\n"))
            val network = Retrofit.Builder().baseUrl(server.url("/"))
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build().create(ApiService::class.java)
            every { api.readingHelp(request) } returns network.readingHelp(request)
            val answer = withTimeout(5_000) { repo.answer(request).toList() }
            assertEquals(listOf("part", "answer"), answer.map { it.text })
            val sent = server.takeRequest(1, TimeUnit.SECONDS)!!
            assertEquals("/mobile/reading-help", sent.path)
            val body = sent.body.readUtf8()
            assertTrue(body.contains("\"fileId\":73"))
            assertTrue(body.contains("Exact selected excerpt"))
        }
    }

    @Test fun `collector cancellation closes live HTTP call rather than leaving paid stream running`() = runBlocking {
        access()
        MockWebServer().use { server ->
            val delta = "data: {\"type\":\"delta\",\"text\":\"part\"}\n\n"
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream")
                .setBody(delta + "data: {\"type\":\"done\",\"text\":\"answer\"}\n\n")
                .throttleBody(delta.toByteArray().size.toLong(), 1, TimeUnit.SECONDS))
            val network = Retrofit.Builder().baseUrl(server.url("/"))
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build().create(ApiService::class.java)
            val call = network.readingHelp(request)
            every { api.readingHelp(request) } returns call
            assertEquals("part", withTimeout(5_000) { repo.answer(request).first() }.text)
            assertTrue(call.isCanceled)
        }
    }

    @Test fun `a dropped stream is not reported complete and is never retried`() = runBlocking {
        access()
        val call = mockk<retrofit2.Call<ResponseBody>>(relaxed = true)
        every { api.readingHelp(request) } returns call
        every { call.enqueue(any()) } answers {
            firstArg<retrofit2.Callback<ResponseBody>>().onResponse(call, Response.success(
                "data: {\"type\":\"delta\",\"text\":\"partial\"}\n\n".toResponseBody("text/event-stream".toMediaType())))
        }
        assertTrue(runCatching { repo.answer(request).toList() }.isFailure)
        verify(exactly = 1) { api.readingHelp(request) }
        verify { call.cancel() }
    }
}
