package com.bookorbit.core.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test

class SingleAttemptAiTest {
    private fun request(server: MockWebServer) = Request.Builder().url(server.url("/mobile/reading-help"))
        .post("{}".toRequestBody("application/json".toMediaType())).build()

    @Test fun `503 Retry After zero does not replay potentially paid AI request`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "0"))
            server.enqueue(MockResponse().setBody("must not be requested"))
            singleAttemptAiClient(OkHttpClient()).newCall(request(server)).execute().use { assertEquals(503, it.code) }
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun `redirect cannot resend an excerpt or photo to another origin`() {
        MockWebServer().use { source -> MockWebServer().use { target ->
            source.enqueue(MockResponse().setResponseCode(307).setHeader("Location", target.url("/private")))
            singleAttemptAiClient(OkHttpClient()).newCall(request(source)).execute().use { assertEquals(307, it.code) }
            assertEquals(1, source.requestCount)
            assertEquals(0, target.requestCount)
        } }
    }

    @Test fun `disconnect after receiving request cannot trigger a transparent second generation`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            server.enqueue(MockResponse().setBody("must not be requested"))
            assertTrue(runCatching { singleAttemptAiClient(OkHttpClient()).newCall(request(server)).execute().close() }.isFailure)
            assertEquals(1, server.requestCount)
        }
    }
}
