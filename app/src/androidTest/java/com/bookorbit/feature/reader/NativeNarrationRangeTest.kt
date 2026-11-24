package com.bookorbit.feature.reader

import android.content.Context
import androidx.media3.datasource.DataSpec
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bookorbit.core.auth.SecureStorage
import com.bookorbit.core.auth.SessionManager
import com.bookorbit.core.model.AuthUser
import com.bookorbit.core.network.AuthInterceptor
import com.bookorbit.core.network.BaseUrlInterceptor
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore
import java.util.UUID

/** Real Android/Media3 HTTP reads, synthetic bytes only. Does not touch the app's login store. */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(UnstableApi::class)
class NativeNarrationRangeTest {
    private val json = Json { ignoreUnknownKeys = true }
    private fun withPlan(block: (MockWebServer, SessionManager, NativeNarrationPlan) -> Unit) = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "native-range-test-${UUID.randomUUID()}"
        val alias = "$name-key"
        try {
            val session = SessionManager(SecureStorage(context, name, alias, "$name-owner"), json)
            MockWebServer().use { server ->
                session.setServerUrl(server.url("/prefix").toString())
                session.signIn("synthetic-native-token", AuthUser(7, "synthetic-range"))
                val href = "OPS/audio 100%.mp3"
                val playlist = NativeNarrationPlaylist(5, 84, 20.0,
                    listOf(NativeNarrationClip(0, 0, "OPS/overlay.smil", "OPS/chapter.xhtml", "one", href, "audio/mpeg", 0.0, 20.0, 20.0)),
                    listOf(NativeNarrationSection(0, "OPS/chapter.xhtml", "Synthetic", "OPS/overlay.smil", 0.0, 20.0)),
                    listOf(NativeNarrationResource(href, "audio/mpeg", 20)))
                server.enqueue(MockResponse().setBody(json.encodeToString(NativeNarrationPlaylist.serializer(), playlist)))
                val client = OkHttpClient.Builder().addInterceptor(BaseUrlInterceptor(session)).addInterceptor(AuthInterceptor(session)).build()
                val plan = NativeNarrationStreaming(client, session, json).open(5, 84)
                assertEquals("/prefix/api/v1/epub/5/media-overlay?fileId=84", server.takeRequest().path)
                block(server, session, plan)
            }
        } finally {
            // These UUID-named stores/keys belong only to this test, never the app's real credentials.
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
            context.deleteSharedPreferences(name)
            context.deleteSharedPreferences("$name-owner")
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
        }
    }

    @Test fun nativeAudioRangePreservesExactEditionAndLiteralPercentAssetIdentity() = withPlan { server, _, plan ->
        server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 5-9/20").setBody("56789"))
        val source = plan.dataSourceFactory().createDataSource()
        try {
            val uri = plan.resourceUrl("OPS/audio 100%.mp3").toString()
            assertEquals(5L, source.open(DataSpec.Builder().setUri(uri).setPosition(5).setLength(5).build()))
            val bytes = ByteArray(5)
            var read = 0
            while (read < bytes.size) {
                val count = source.read(bytes, read, bytes.size - read)
                assertTrue("Expected the requested range, not an early EOF", count > 0)
                read += count
            }
            assertEquals("56789", bytes.toString(Charsets.UTF_8))
            assertEquals(-1, source.read(ByteArray(1), 0, 1))
            val wire = server.takeRequest()
            assertEquals("bytes=5-9", wire.getHeader("Range"))
            assertEquals("Bearer synthetic-native-token", wire.getHeader("Authorization"))
            assertEquals("/prefix/api/v1/epub/5/media-overlay/file/OPS/audio%20100%25.mp3?fileId=84", wire.path)
            assertEquals(2, server.requestCount) // One metadata request and one selected asset range, no ZIP.
        } finally { source.close() }
    }

    @Test fun accountChangeStopsEvenAlreadyBufferedResourceBytes() = withPlan { server, session, plan ->
        server.enqueue(MockResponse().setBody("01234567890123456789"))
        val source = plan.dataSourceFactory().createDataSource()
        try {
            source.open(DataSpec.Builder().setUri(plan.resourceUrl("OPS/audio 100%.mp3").toString()).build())
            session.signIn("other-synthetic-token", AuthUser(8, "other-synthetic"))
            assertTrue(runCatching { source.read(ByteArray(5), 0, 5) }.exceptionOrNull() is java.io.IOException)
            assertEquals(2, server.requestCount)
        } finally { source.close() }
    }

    @Test fun unlistedResourceCannotTurnTheFactoryIntoAnAuthenticatedProxy() = withPlan { server, _, plan ->
        val source = plan.dataSourceFactory().createDataSource()
        try {
            assertTrue(runCatching {
                source.open(DataSpec.Builder().setUri(server.url("/prefix/api/v1/private-other-file").toString()).build())
            }.exceptionOrNull() is java.io.IOException)
            assertEquals(1, server.requestCount)
        } finally { source.close() }
    }

    @Test fun queueKeepsClipBoundsResumeClockAndDistinctIdentityWithoutStartingOrFetchingAudio() = withPlan { server, _, plan ->
        val queue = NativeNarrationQueue(plan, "Synthetic narration", "Synthetic author")
        val item = queue.items.single()
        assertTrue(item.mediaId.startsWith("epub-narration:"))
        assertNotEquals("84", item.mediaId)
        assertNotEquals(item.mediaId, NativeNarrationQueue(plan, "Synthetic narration").items.single().mediaId)
        assertEquals(0L, item.clippingConfiguration.startPositionMs)
        assertEquals(20_000L, item.clippingConfiguration.endPositionMs)
        assertEquals(84, item.mediaMetadata.extras!!.getInt(NativeNarrationQueue.EXTRA_FILE_ID))
        assertEquals(5, item.mediaMetadata.extras!!.getInt(NativeNarrationQueue.EXTRA_BOOK_ID))
        val resume = queue.initialPosition(ReadAlongPosition(0, "OPS/chapter.xhtml#one", "OPS/audio 100%.mp3", 11.724068), 0)!!
        assertEquals(11_724L, resume.clipOffsetMs)
        assertEquals(11.724068, resume.assetSeconds, 0.0)
        assertEquals(1, server.requestCount)
    }
}
