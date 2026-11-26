package com.bookorbit.feature.reader

import com.bookorbit.core.auth.SessionManager
import com.bookorbit.core.model.AuthUser
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class EpubStreamingTest {
    private val info = Json.parseToJsonElement("""{"containerPath":"OPS/book.opf","manifest":[{"href":"OPS/ch1.xhtml"},{"href":"OPS/ch2.xhtml"},{"href":"OPS/art 100%.svg"}],"optionalFiles":[]}""").jsonObject
    private val user = mockk<AuthUser>().also { every { it.id } returns 3 }
    private fun session(server: MockWebServer): SessionManager = mockk<SessionManager>().also {
        every { it.currentUser } returns user
        every { it.serverUrl } returns server.url("/prefix").toString()
        every { it.accessToken } returns "synthetic-reader-token"
    }
    private fun metadata() = MockResponse().setHeader("Content-Type", "application/json").setBody(info.toString())

    @Test fun `open gets only metadata then requested chapter with exact edition and native authorization`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(metadata())
            server.enqueue(MockResponse().setHeader("Content-Type", "application/xhtml+xml").setBody("<h1>Chapter one</h1>"))
            val plan = EpubStreaming(OkHttpClient(), session(server), Json).open(4, 71)
            assertEquals(1, server.requestCount)
            val first = server.takeRequest(1, TimeUnit.SECONDS)!!
            assertEquals("/prefix/api/v1/epub/4/info?fileId=71", first.path)
            assertEquals("Bearer synthetic-reader-token", first.getHeader("Authorization"))
            plan.newSession().use { lease ->
                val asset = lease.asset("${lease.baseUrl}/4/file/OPS/ch1.xhtml?fileId=71".toHttpUrl())!!
                assertEquals("<h1>Chapter one</h1>", asset.input.bufferedReader().use { it.readText() })
                val next = server.takeRequest(1, TimeUnit.SECONDS)!!
                assertEquals("/prefix/api/v1/epub/4/file/OPS/ch1.xhtml?fileId=71", next.path)
                assertEquals("Bearer synthetic-reader-token", next.getHeader("Authorization"))
                val pageMeta = ReaderBridge.json.encodeToString(EpubStreamingMeta.serializer(), EpubStreamingMeta(4, 71, lease.baseUrl, plan.info))
                assertFalse(pageMeta.contains("synthetic-reader-token"))
            }
            assertEquals(2, server.requestCount) // No whole file, second chapter, audio or images fetched.
        }
    }

    @Test fun `resource bridge cannot select another edition book origin or unlisted path`() {
        val paths = EpubStreamPaths(4, 71, info, "nonce")
        val base = "${paths.baseUrl}/4/file/OPS/ch1.xhtml"
        assertEquals("OPS/ch1.xhtml", paths.resource("$base?fileId=71".toHttpUrl()))
        for (url in listOf("$base?fileId=72", "$base?fileId=71&fileId=71", "$base?fileId=71&token=private",
            "$base", base.replace("/4/file/", "/5/file/") + "?fileId=71",
            base.replace("ch1.xhtml", "secret.json") + "?fileId=71",
            base.replace("appassets.androidplatform.net", "elsewhere.test") + "?fileId=71",
            base.replace("https://", "https://user@") + "?fileId=71",
            base.replace("OPS/ch1.xhtml", "%2e%2e/secret") + "?fileId=71")) assertNull(url, paths.resource(url.toHttpUrl()))
    }

    @Test fun `literal percent and spaces survive the pinned servers double decoding`() {
        val paths = EpubStreamPaths(4, 71, info, "nonce")
        val name = "OPS/art 100%.svg"
        assertEquals(name, paths.resource("${paths.baseUrl}/4/file/OPS/art%20100%25.svg?fileId=71".toHttpUrl()))
        assertEquals("/prefix/api/v1/epub/4/file/OPS/art%20100%2525.svg", paths.serverResource("https://books.test/prefix", name).encodedPath)
    }

    @Test fun `changing account stops resource access before any new request`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(metadata())
            val session = session(server)
            val plan = EpubStreaming(OkHttpClient(), session, Json).open(4, 71)
            every { session.currentUser } returns mockk<AuthUser>().also { every { it.id } returns 99 }
            plan.newSession().use { lease ->
                assertTrue(runCatching { lease.asset("${lease.baseUrl}/4/file/OPS/ch1.xhtml?fileId=71".toHttpUrl()) }.isFailure)
            }
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun `closing a WebView lease cancels in-flight resource data and rejects later fetches`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(metadata())
            server.enqueue(MockResponse().setBody("resource data").throttleBody(1, 1, TimeUnit.SECONDS))
            val plan = EpubStreaming(OkHttpClient(), session(server), Json).open(4, 71)
            val lease = plan.newSession()
            val url = "${lease.baseUrl}/4/file/OPS/ch1.xhtml?fileId=71".toHttpUrl()
            val asset = lease.asset(url)!!
            assertTrue(asset.input.read() >= 0)
            lease.close()
            assertTrue(runCatching { asset.input.use { it.readBytes() } }.isFailure)
            assertTrue(runCatching { lease.asset(url) }.isFailure)
        }
    }

    @Test fun `cancelled metadata open cancels its HTTP call`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(metadata().setHeadersDelay(2, TimeUnit.SECONDS))
            val cancelled = AtomicInteger()
            val client = OkHttpClient.Builder().eventListener(object : EventListener() {
                override fun canceled(call: Call) { cancelled.incrementAndGet() }
            }).build()
            val repo = EpubStreaming(client, session(server), Json)
            val job = launch(Dispatchers.Default) { repo.open(4, 71) }
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            withTimeout(5_000) { job.cancelAndJoin() }
            assertTrue(cancelled.get() > 0)
        }
    }

    @Test fun `unsupported streaming never silently requests a complete book`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(404))
            assertTrue(runCatching { EpubStreaming(OkHttpClient(), session(server), Json).open(4, 71) }.isFailure)
            assertEquals(1, server.requestCount)
        }
    }
}
