package com.bookorbit.feature.player

import com.bookorbit.core.auth.SecureStorage
import com.bookorbit.core.auth.SessionManager
import com.bookorbit.core.model.AuthUser
import com.bookorbit.core.network.AuthInterceptor
import com.bookorbit.core.network.BaseUrlInterceptor
import com.bookorbit.core.network.TokenAuthenticator
import com.bookorbit.core.network.TokenRefresher
import dagger.Lazy
import io.mockk.mockk
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class AudioStreamCallsTest {
    private fun session(server: MockWebServer) = SessionManager(mockk<SecureStorage>(relaxed = true), Json).apply {
        setServerUrl(server.url("/prefix").toString())
        signIn("synthetic-old", AuthUser(7, "audio-test"))
    }
    private fun client(session: SessionManager, refresh: OkHttpClient = OkHttpClient()) = audioStreamClient(
        OkHttpClient.Builder().addInterceptor(BaseUrlInterceptor(session)).addInterceptor(AuthInterceptor(session))
            .authenticator(TokenAuthenticator(TokenRefresher(session, Lazy { refresh }, Json))).build())
    private fun request(server: MockWebServer) = Request.Builder()
        .url(server.url("/prefix/api/v1/audiobooks/42/assets/asset-7/content"))
        .header("Range", "bytes=32044-64043").build()

    @Test fun `a seek call uses the current token and does not rewrite the absolute source URL`() {
        MockWebServer().use { server ->
            val session = session(server)
            val calls = AudioStreamCalls(client(session), session, session.owner())
            session.updateAccessToken("synthetic-fresh")
            for (path in listOf("/prefix/api/v1/audiobooks/42/assets/asset-7/content", "/prefix/api/v1/books/files/7/serve")) {
                server.enqueue(MockResponse().setBody("audio"))
                calls.newCall(request(server).newBuilder().url(server.url(path)).build()).execute().use { assertEquals(200, it.code) }
                val actual = server.takeRequest()
                assertEquals(path, actual.path)
                assertEquals("Bearer synthetic-fresh", actual.getHeader("Authorization"))
                assertEquals("bytes=32044-64043", actual.getHeader("Range"))
            }
        }
    }

    @Test fun `an expired stream token refreshes once without changing Range or edition resource`() {
        MockWebServer().use { server ->
            val session = session(server)
            val owner = session.owner()!!
            val calls = AudioStreamCalls(client(session), session, owner)
            server.enqueue(MockResponse().setResponseCode(401))
            server.enqueue(MockResponse().setBody("""{"accessToken":"synthetic-rotated"}"""))
            server.enqueue(MockResponse().setResponseCode(206).setBody("audio"))
            calls.newCall(request(server)).execute().use { assertEquals(206, it.code) }
            val first = server.takeRequest()
            val refresh = server.takeRequest()
            val retry = server.takeRequest()
            assertEquals("/prefix/api/v1/auth/refresh", refresh.path)
            assertEquals(first.path, retry.path)
            assertEquals(first.getHeader("Range"), retry.getHeader("Range"))
            assertEquals("Bearer synthetic-rotated", retry.getHeader("Authorization"))
            assertTrue(session.owns(owner))
            assertEquals(3, server.requestCount)
        }
    }

    @Test fun `old sources and foreign destinations never receive the current accounts credential`() {
        MockWebServer().use { server ->
            val session = session(server)
            val calls = AudioStreamCalls(client(session), session, session.owner())
            for (url in listOf(server.url("/outside/api/v1/books/files/7/serve").toString(),
                server.url("/prefix/api/v1/auth/refresh").toString(), "https://other.example/api/v1/books/files/7/serve")) {
                assertThrows(IOException::class.java) { calls.newCall(Request.Builder().url(url).build()) }
            }
            session.signIn("synthetic-next-account", AuthUser(8, "next-test"))
            assertThrows(IOException::class.java) { calls.newCall(request(server)) }
            assertEquals(0, server.requestCount)
        }
    }

    @Test fun `redirects and transient failures do not become hidden credential or playback retries`() {
        MockWebServer().use { server ->
            MockWebServer().use { destination ->
                val session = session(server)
                val calls = AudioStreamCalls(client(session), session, session.owner())
                server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", destination.url("/audio")))
                calls.newCall(request(server)).execute().use { assertEquals(302, it.code) }
                assertEquals(0, destination.requestCount)
                server.enqueue(MockResponse().setResponseCode(503))
                calls.newCall(request(server)).execute().use { assertEquals(503, it.code) }
                assertEquals(2, server.requestCount)
                assertNotNull(session.owner())
            }
        }
    }
}
