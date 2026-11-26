package com.bookorbit.core.network

import com.bookorbit.core.auth.SecureStorage
import com.bookorbit.core.auth.SessionManager
import com.bookorbit.core.model.AuthUser
import dagger.Lazy
import io.mockk.mockk
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

class TokenRefreshOwnershipTest {
    private val first = AuthUser(id = 1, username = "synthetic-first")
    private val second = AuthUser(id = 2, username = "synthetic-second")

    private fun scenario(code: Int, change: (SessionManager) -> Unit) {
        MockWebServer().use { server ->
            val session = SessionManager(mockk<SecureStorage>(relaxed = true), Json)
            session.setServerUrl(server.url("/prefix").toString())
            session.signIn("old-token", first)
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    change(session)
                    return MockResponse().setResponseCode(code).setBody("""{"accessToken":"old-account-refreshed-token"}""")
                }
            }
            val refresher = TokenRefresher(session, Lazy { OkHttpClient() }, Json)
            val result = refresher.refreshIfNeeded("old-token")
            assertNull("An obsolete refresh must not produce a retry credential", result)
            assertEquals(second, session.currentUser)
            assertEquals("new-account-token", session.accessToken)
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun `successful refresh from an old account cannot overwrite a newly signed in account`() =
        scenario(200) { it.signIn("new-account-token", second) }

    @Test fun `failed refresh from an old account cannot sign out the newly signed in account`() =
        scenario(401) { it.signIn("new-account-token", second) }

    @Test fun `refresh from an old server cannot replace credentials after server change`() =
        scenario(200) { it.setServerUrl("https://other-synthetic-server.test"); it.signIn("new-account-token", second) }

    @Test fun `an old request owner cannot reuse the next accounts current token`() {
        MockWebServer().use { server ->
            val session = SessionManager(mockk<SecureStorage>(relaxed = true), Json)
            session.setServerUrl(server.url("/").toString()); session.signIn("old-token", first)
            val previous = session.owner()!!
            session.signIn("new-account-token", second)
            val refresher = TokenRefresher(session, Lazy { OkHttpClient() }, Json)
            assertNull(refresher.refreshIfNeeded("old-token", previous))
            assertEquals(0, server.requestCount)
        }
    }

    @Test fun `logout and relogin to the same account still invalidate the old lease`() {
        val session = SessionManager(mockk<SecureStorage>(relaxed = true), Json)
        session.setServerUrl("https://synthetic.test"); session.signIn("old-token", first)
        val previous = session.owner()!!
        session.signOut(); session.signIn("new-token", first)
        assertFalse(session.owns(previous))
        assertFalse(session.commitRefreshedToken(previous, "obsolete"))
        assertEquals("new-token", session.accessToken)
    }

    @Test fun `obsolete response cannot rotate a newly signed in accounts refresh cookie`() {
        MockWebServer().use { server ->
            val session = SessionManager(mockk<SecureStorage>(relaxed = true), Json)
            session.setServerUrl(server.url("/prefix").toString()); session.signIn("old-token", first)
            fun cookie(value: String) = Cookie.Builder().name("refresh").value(value).hostOnlyDomain(server.hostName).path("/").build()
            val currentCookies = AtomicReference(listOf(cookie("old-refresh")))
            val jar = object : CookieJar {
                override fun loadForRequest(url: HttpUrl) = currentCookies.get()
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) { if (cookies.isNotEmpty()) currentCookies.set(cookies) }
            }
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    assertEquals("refresh=old-refresh", request.getHeader("Cookie"))
                    session.signIn("new-account-token", second)
                    currentCookies.set(listOf(cookie("new-account-refresh")))
                    return MockResponse().setHeader("Set-Cookie", "refresh=obsolete-rotation; Path=/")
                        .setBody("""{"accessToken":"obsolete"}""")
                }
            }
            val client = OkHttpClient.Builder().cookieJar(jar).build()
            assertNull(TokenRefresher(session, Lazy { client }, Json).refreshIfNeeded("old-token"))
            assertEquals("new-account-refresh", currentCookies.get().single().value)
            assertEquals("new-account-token", session.accessToken)
        }
    }

    @Test fun `valid refresh updates the same owners token and cookie without changing its lease`() {
        MockWebServer().use { server ->
            val session = SessionManager(mockk<SecureStorage>(relaxed = true), Json)
            session.setServerUrl(server.url("/").toString()); session.signIn("old-token", first)
            val owner = session.owner()!!
            val saved = AtomicReference<List<Cookie>>(emptyList())
            val client = OkHttpClient.Builder().cookieJar(object : CookieJar {
                override fun loadForRequest(url: HttpUrl) = emptyList<Cookie>()
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) { saved.set(cookies) }
            }).build()
            server.enqueue(MockResponse().setHeader("Set-Cookie", "refresh=valid-rotation; Path=/").setBody("""{"accessToken":"fresh-token"}"""))
            val refresher = TokenRefresher(session, Lazy { client }, Json)
            assertEquals("fresh-token", refresher.refreshIfNeeded("old-token", owner))
            assertEquals("valid-rotation", saved.get().single().value)
            assertTrue(session.owns(owner))
            assertEquals("fresh-token", refresher.refreshIfNeeded("old-token", owner))
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun `an old 401 cannot replay a book request with another accounts token`() {
        MockWebServer().use { server ->
            val session = SessionManager(mockk<SecureStorage>(relaxed = true), Json)
            session.setServerUrl(server.url("/prefix").toString()); session.signIn("old-token", first)
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    session.signIn("new-account-token", second)
                    return MockResponse().setResponseCode(401)
                }
            }
            val refresher = TokenRefresher(session, Lazy { OkHttpClient() }, Json)
            val client = OkHttpClient.Builder().addInterceptor(BaseUrlInterceptor(session)).addInterceptor(AuthInterceptor(session))
                .authenticator(TokenAuthenticator(refresher)).build()
            assertTrue(runCatching { client.newCall(Request.Builder().url("http://localhost/books/7").build()).execute().close() }.isFailure)
            assertEquals(1, server.requestCount)
            assertEquals("new-account-token", session.accessToken)
        }
    }

    @Test fun `owned request queued before logout is refused before sending even without a bearer token`() {
        MockWebServer().use { server ->
            val session = SessionManager(mockk<SecureStorage>(relaxed = true), Json)
            session.setServerUrl(server.url("/").toString()); session.signIn("old-token", first)
            val owner = session.owner()!!
            val request = Request.Builder().url("http://localhost/books/files/7/sessions")
                .tag(com.bookorbit.core.auth.SessionOwner::class.java, owner).post("{}".toRequestBody("application/json".toMediaType())).build()
            session.signOut()
            server.enqueue(MockResponse().setResponseCode(401))
            val client = OkHttpClient.Builder().addInterceptor(BaseUrlInterceptor(session)).addInterceptor(AuthInterceptor(session)).build()
            assertTrue(runCatching { client.newCall(request).execute().close() }.isFailure)
            assertEquals(0, server.requestCount)
        }
    }

    @Test fun `transient refresh failure preserves offline login and Retry After cannot replay rotation`() {
        MockWebServer().use { server ->
            val session = SessionManager(mockk<SecureStorage>(relaxed = true), Json)
            session.setServerUrl(server.url("/").toString()); session.signIn("old-token", first)
            val owner = session.owner()!!
            server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "0"))
            server.enqueue(MockResponse().setBody("""{"accessToken":"must-not-be-requested"}"""))
            assertNull(TokenRefresher(session, Lazy { OkHttpClient() }, Json).refreshIfNeeded("old-token", owner))
            assertTrue(session.owns(owner))
            assertEquals("old-token", session.accessToken)
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun `authentication refusal clears only the still current session`() {
        MockWebServer().use { server ->
            val session = SessionManager(mockk<SecureStorage>(relaxed = true), Json)
            session.setServerUrl(server.url("/").toString()); session.signIn("old-token", first)
            val owner = session.owner()!!
            server.enqueue(MockResponse().setResponseCode(401))
            assertNull(TokenRefresher(session, Lazy { OkHttpClient() }, Json).refreshIfNeeded("old-token", owner))
            assertNull(session.currentUser)
            assertNull(session.accessToken)
            assertFalse(session.owns(owner))
        }
    }
}
