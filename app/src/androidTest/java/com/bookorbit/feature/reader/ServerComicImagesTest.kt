package com.bookorbit.feature.reader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bookorbit.core.auth.SecureStorage
import com.bookorbit.core.auth.SessionManager
import com.bookorbit.core.model.AuthUser
import com.bookorbit.core.network.AuthInterceptor
import com.bookorbit.core.network.BaseUrlInterceptor
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ServerComicImagesTest {
    private fun withSource(block: (MockWebServer, SessionManager, ServerComicImages, ServerReaderPolicy) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "server-comic-test-${UUID.randomUUID()}"
        val alias = "$name-key"
        try {
            val session = SessionManager(SecureStorage(context, name, alias, "$name-owner"), Json)
            MockWebServer().use { server ->
                session.setServerUrl(server.url("/prefix").toString())
                session.signIn("synthetic-web-token", AuthUser(7, "synthetic-comic"))
                val client = OkHttpClient.Builder().addInterceptor(BaseUrlInterceptor(session)).addInterceptor(AuthInterceptor(session)).build()
                block(server, session, ServerComicImages(client, session), ServerReaderPolicy(session.serverUrl!!, 12, 34, "cbz"))
            }
        } finally {
            context.deleteSharedPreferences(name)
            context.deleteSharedPreferences("$name-owner")
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
        }
    }

    @Test fun webComicImagesUseNativeOwnerBoundAuthOnlyForTheSelectedEdition() = withSource { server, session, source, policy ->
        server.enqueue(MockResponse().setHeader("Content-Type", "image/png").setBody("synthetic-png-bytes"))
        val response = source.open(policy, session.owner(), server.url("/prefix/api/v1/cbz/files/34/pages/2"), "GET")!!
        response.data.use { assertEquals("synthetic-png-bytes", it.readBytes().toString(Charsets.UTF_8)) }
        assertEquals("image/png", response.mimeType)
        assertFalse(response.responseHeaders.values.any { it.contains("synthetic-web-token") })
        val request = server.takeRequest()
        assertEquals("/prefix/api/v1/cbz/files/34/pages/2", request.path)
        assertEquals("Bearer synthetic-web-token", request.getHeader("Authorization"))
    }

    @Test fun anotherFileOrOriginNeverBecomesAnAuthenticatedWebProxy() = withSource { server, session, source, policy ->
        assertNull(source.open(policy, session.owner(), server.url("/prefix/api/v1/cbz/files/35/pages/0"), "GET"))
        assertEquals(0, server.requestCount)
    }

    @Test fun switchingAccountStopsAlreadyBufferedImageBytes() = withSource { server, session, source, policy ->
        server.enqueue(MockResponse().setHeader("Content-Type", "image/png").setBody("synthetic-png-bytes"))
        val response = source.open(policy, session.owner(), server.url("/prefix/api/v1/cbz/files/34/pages/0"), "GET")!!
        try {
            session.signIn("another-synthetic-token", AuthUser(8, "other-comic"))
            assertTrue(runCatching { response.data.read() }.exceptionOrNull() is java.io.IOException)
            assertEquals(1, server.requestCount)
        } finally { response.data.close() }
    }
}
