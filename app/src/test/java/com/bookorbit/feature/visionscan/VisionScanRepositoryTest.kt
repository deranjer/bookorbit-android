package com.bookorbit.feature.visionscan

import com.bookorbit.core.model.*
import com.bookorbit.core.network.ApiService
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class VisionScanRepositoryTest {
    private val api = mockk<ApiService>()
    private val json = Json { ignoreUnknownKeys = true }
    private val repo = VisionScanRepository(api, json)
    private val encoded = "dGVzdA=="
    private fun access(status: Int = 400, enabled: Boolean = true) {
        coEvery { api.getAppInfo() } returns AppInfo("3.9.0", features = listOf("vision-scan"), visionScan = VisionScanCapability(enabled))
        coEvery { api.probeVisionScan(any()) } returns Response.error(status, "{}".toResponseBody())
    }

    @Test fun `access checks are nonbillable empty DTOs and production restriction blocks photo upload`() = runBlocking {
        access(403)
        coEvery { api.probeVisionScan(any()) } returns Response.error(403, """{"errorCode":"APP_ATTEST_REQUIRED"}""".toResponseBody())
        assertEquals(VisionScanRepository.Problem.RESTRICTED, repo.access().problem)
        assertTrue(runCatching { repo.scan("cover", encoded) }.isFailure)
        coVerify { api.probeVisionScan(match { it.isEmpty() }) }
        coVerify(exactly = 0) { api.visionScan(any()) }
    }

    @Test fun `unsupported disabled and invalid inputs never upload a photo`() = runBlocking {
        access(enabled = false)
        assertEquals(VisionScanRepository.Problem.DISABLED, repo.access().problem)
        assertTrue(runCatching { repo.scan("cover", encoded) }.isFailure)
        assertTrue(runCatching { repo.scan("unsupported", encoded) }.isFailure)
        assertTrue(runCatching { repo.scan("cover", "data:image/jpeg;base64,$encoded") }.isFailure)
        assertTrue(runCatching { repo.scan("cover", "a") }.isFailure)
        coEvery { api.getAppInfo() } returns AppInfo("old")
        assertEquals(VisionScanRepository.Problem.UNSUPPORTED, repo.access().problem)
        coVerify(exactly = 0) { api.visionScan(any()) }
    }

    @Test fun `confirmed scan submits only exact kind and prepared photo and keeps authoritative book ids`() = runBlocking {
        access()
        val response = VisionScanResponse("shelf", listOf(VisionScanBook("one", "Recognized title", "Author", listOf(VisionMatch(VisionMatchedBook(37, "Library title"), .95)))))
        coEvery { api.visionScan(VisionScanRequest("shelf", encoded)) } returns Response.success(response)
        assertEquals(37, repo.scan("shelf", encoded).books.single().matches.single().book.id)
        coVerify(exactly = 1) { api.visionScan(VisionScanRequest("shelf", encoded)) }
    }

    @Test fun `wrong result kind and invalid library identity are rejected`() = runBlocking {
        access()
        coEvery { api.visionScan(any()) } returns Response.success(VisionScanResponse("shelf"))
        assertTrue(runCatching { repo.scan("cover", encoded) }.isFailure)
        coEvery { api.visionScan(any()) } returns Response.success(VisionScanResponse("cover", listOf(VisionScanBook("one", "Title", matches = listOf(VisionMatch(VisionMatchedBook(-1)))))))
        assertTrue(runCatching { repo.scan("cover", encoded) }.isFailure)
    }

    @Test fun `server provider budget is distinct from attestation and failure never retries`() = runBlocking {
        access()
        coEvery { api.visionScan(any()) } returns Response.error(403, """{"errorCode":"VISION_SCAN_BUDGET"}""".toResponseBody())
        val failure = runCatching { repo.scan("cover", encoded) }.exceptionOrNull() as VisionScanRepository.Failure
        assertEquals(VisionScanRepository.Problem.BUDGET, failure.problem)
        coVerify(exactly = 1) { api.visionScan(any()) }
    }

    @Test fun `cancelling active photo scan cancels actual HTTP call`() = runBlocking {
        MockWebServer().use { server ->
            val cancellations = AtomicInteger()
            val client = okhttp3.OkHttpClient.Builder().eventListener(object : okhttp3.EventListener() {
                override fun canceled(call: okhttp3.Call) { cancellations.incrementAndGet() }
            }).build()
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"version":"3.9.0","features":["vision-scan"],"visionScan":{"enabled":true}}"""))
            server.enqueue(MockResponse().setResponseCode(400).setBody("{}"))
            server.enqueue(MockResponse().setHeadersDelay(2, TimeUnit.SECONDS).setBody("{}"))
            val network = Retrofit.Builder().baseUrl(server.url("/")).client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build().create(ApiService::class.java)
            val repository = VisionScanRepository(network, json)
            val job = launch(Dispatchers.Default) { repository.scan("cover", encoded) }
            repeat(3) { assertNotNull(server.takeRequest(5, TimeUnit.SECONDS)) }
            withTimeout(5_000) { job.cancelAndJoin() }
            assertTrue(cancellations.get() > 0)
        }
    }
}
