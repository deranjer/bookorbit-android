package com.bookorbit.feature.reader

import com.bookorbit.core.auth.SecureStorage
import com.bookorbit.core.auth.SessionManager
import com.bookorbit.core.model.AuthUser
import com.bookorbit.core.network.AuthInterceptor
import com.bookorbit.core.network.BaseUrlInterceptor
import io.mockk.mockk
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.mockwebserver.*
import okhttp3.mockwebserver.Dispatcher
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class NativeNarrationStreamingTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val fixture = checkNotNull(javaClass.getResourceAsStream("/epub-overlay-playlist.json")).bufferedReader().use { it.readText() }
    private fun session(server: MockWebServer) = SessionManager(mockk<SecureStorage>(relaxed = true), json).apply {
        setServerUrl(server.url("/prefix").toString()); signIn("synthetic-narration-token", AuthUser(7, "synthetic"))
    }
    private fun client(session: SessionManager) = OkHttpClient.Builder()
        .addInterceptor(BaseUrlInterceptor(session)).addInterceptor(AuthInterceptor(session)).build()

    @Test fun `open requests only exact edition playlist with native credentials and no audio or archive prefetch`() = runBlocking {
        MockWebServer().use { server ->
            val session = session(server)
            server.enqueue(MockResponse().setBody(fixture))
            val plan = NativeNarrationStreaming(client(session), session, json).open(5, 84)
            assertEquals(4, plan.timeline.clips.size)
            val request = server.takeRequest(1, TimeUnit.SECONDS)!!
            assertEquals("/prefix/api/v1/epub/5/media-overlay?fileId=84", request.path)
            assertEquals("Bearer synthetic-narration-token", request.getHeader("Authorization"))
            assertEquals(1, server.requestCount)
            assertFalse(plan.resourceUrl("OEBPS/audio.mp3").toString().contains("synthetic-narration-token"))
        }
    }

    @Test fun `native media overlay route encodes percent names once rather than copying generic EPUB double decoding workaround`() = runBlocking {
        MockWebServer().use { server ->
            val session = session(server)
            server.enqueue(MockResponse().setBody(fixture.replace("OEBPS/audio.mp3", "OEBPS/audio 100%.mp3")))
            val plan = NativeNarrationStreaming(client(session), session, json).open(5, 84)
            assertEquals("/prefix/api/v1/epub/5/media-overlay/file/OEBPS/audio%20100%25.mp3",
                plan.resourceUrl("OEBPS/audio 100%.mp3").encodedPath)
            assertEquals("84", plan.resourceUrl("OEBPS/audio 100%.mp3").queryParameter("fileId"))
            assertTrue(runCatching { plan.resourceUrl("OEBPS/not-in-playlist.mp3") }.isFailure)
        }
    }

    @Test fun `null or another file identity is refused instead of selecting the servers default EPUB`() = runBlocking {
        for (response in listOf(fixture.replace("\"fileId\": 84", "\"fileId\": null"), fixture.replace("\"fileId\": 84", "\"fileId\": 85"))) {
            MockWebServer().use { server ->
                val session = session(server); server.enqueue(MockResponse().setBody(response))
                assertTrue(runCatching { NativeNarrationStreaming(client(session), session, json).open(5, 84) }.isFailure)
                assertEquals(1, server.requestCount)
            }
        }
    }

    @Test fun `an obsolete reader owner stops before any metadata request`() = runBlocking {
        MockWebServer().use { server ->
            val session = session(server); val previous = session.owner()!!
            session.signIn("other-token", AuthUser(8, "other"))
            assertTrue(runCatching { NativeNarrationStreaming(client(session), session, json).open(5, 84, previous) }.isFailure)
            assertEquals(0, server.requestCount)
        }
    }

    @Test fun `account switch while metadata is in flight cannot publish a plan for the next account`() = runBlocking {
        MockWebServer().use { server ->
            val session = session(server)
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    session.signIn("other-token", AuthUser(8, "other"))
                    return MockResponse().setBody(fixture)
                }
            }
            assertTrue(runCatching { NativeNarrationStreaming(client(session), session, json).open(5, 84) }.isFailure)
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun `unsupported narration never falls back to a full archive or follows a redirect`() = runBlocking {
        for (code in listOf(404, 307)) MockWebServer().use { server ->
            val session = session(server)
            server.enqueue(MockResponse().setResponseCode(code).setHeader("Location", server.url("/whole-book")))
            assertTrue(runCatching { NativeNarrationStreaming(client(session), session, json).open(5, 84) }.isFailure)
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun `cancelling metadata cancels its HTTP call`() = runBlocking {
        MockWebServer().use { server ->
            val session = session(server)
            server.enqueue(MockResponse().setBody(fixture).setHeadersDelay(2, TimeUnit.SECONDS))
            val cancelled = AtomicInteger()
            val client = client(session).newBuilder().eventListener(object : EventListener() {
                override fun canceled(call: Call) { cancelled.incrementAndGet() }
            }).build()
            val repo = NativeNarrationStreaming(client, session, json)
            val job = launch(Dispatchers.Default) { repo.open(5, 84) }
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            withTimeout(5_000) { job.cancelAndJoin() }
            assertTrue(cancelled.get() > 0)
        }
    }
}
