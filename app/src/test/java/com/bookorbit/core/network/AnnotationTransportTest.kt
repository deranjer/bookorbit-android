package com.bookorbit.core.network

import com.bookorbit.core.model.CreateAnnotation
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class AnnotationTransportTest {
    @Test fun `annotation POST is one shot even for retry-after zero and retains authenticator`() = runBlocking {
        MockWebServer().use { server ->
            val auth = Authenticator { _, _ -> null }
            val client = annotationClient(OkHttpClient.Builder().authenticator(auth).build())
            assertSame(auth, client.authenticator)
            assertFalse(client.followRedirects)
            assertFalse(client.retryOnConnectionFailure)
            val api = Retrofit.Builder().baseUrl(server.url("/" )).client(client)
                .addConverterFactory(Json.asConverterFactory("application/json".toMediaType())).build().create(ApiService::class.java)
            server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "0"))
            server.enqueue(MockResponse().setBody("{}"))
            assertTrue(runCatching { api.createAnnotation(1, CreateAnnotation("cfi", 9, "text", "yellow", "highlight", null, null)) }.isFailure)
            assertEquals(1, server.requestCount)
        }
    }
}
