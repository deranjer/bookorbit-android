package com.bookorbit.feature.ai

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.bookorbit.R
import com.bookorbit.core.auth.SecureStorage
import com.bookorbit.core.auth.SessionManager
import com.bookorbit.core.model.AuthUser
import com.bookorbit.core.network.ApiService
import com.bookorbit.feature.readinghelp.ReadingHelpRepository
import com.bookorbit.feature.readinghelp.ReadingHelpSheet
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.security.KeyStore
import java.util.UUID

class DirectReadingHelpUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun selectedPassageIsSentOnlyAfterProviderSpecificConfirmation() {
        val context = compose.activity
        val name = "ai-help-ui-test-${UUID.randomUUID()}"
        val alias = "$name-key"
        val ownerPrefs = "$name-owner"
        try {
            val storage = SecureStorage(context, name, alias, ownerPrefs)
            val session = SessionManager(storage, Json).apply { setServerUrl("https://fixture.example"); signIn("fake-bookorbit-bearer", AuthUser(1, "fixture")) }
            val store = AiConnectionStore(storage, session)
            store.save(session.owner()!!, AiRoute.DIRECT, AiProvider.OPENAI, "fixture-model", "", "fake-provider-key")
            MockWebServer().use { provider -> MockWebServer().use { bookorbit ->
                val api = Retrofit.Builder().baseUrl(bookorbit.url("/"))
                    .addConverterFactory(Json.asConverterFactory("application/json".toMediaType())).build().create(ApiService::class.java)
                val client = OkHttpClient.Builder().followRedirects(false).retryOnConnectionFailure(false).addInterceptor { chain ->
                    chain.proceed(chain.request().newBuilder().url(provider.url("/chat")).build())
                }.build()
                val direct = DirectReadingHelp(store, session, client)
                val repository = ReadingHelpRepository(api, Json, store, direct, session)
                compose.setContent { MaterialTheme { ReadingHelpSheet(42, "Only this selected fixture passage", repository, {}) } }
                val ask = context.getString(R.string.help_ask)
                compose.waitUntil(10_000) { compose.onAllNodesWithText(ask).fetchSemanticsNodes().isNotEmpty() }
                assertEquals(0, provider.requestCount); assertEquals(0, bookorbit.requestCount)
                compose.onNodeWithText(ask).performScrollTo().performClick()
                compose.onNodeWithText(context.getString(R.string.ai_direct_confirm, "OpenAI", "fixture-model")).assertIsDisplayed()
                assertEquals(0, provider.requestCount)
                provider.enqueue(MockResponse().setBody("""{"choices":[{"finish_reason":"stop","message":{"content":"Fixture answer"}}]}"""))
                compose.onNodeWithText(context.getString(R.string.help_send)).performClick()
                compose.waitUntil(10_000) { compose.onAllNodesWithText("Fixture answer").fetchSemanticsNodes().isNotEmpty() }
                assertEquals(1, provider.requestCount); assertEquals(0, bookorbit.requestCount)
                val sent = provider.takeRequest()
                assertEquals("Bearer fake-provider-key", sent.getHeader("Authorization"))
                assertNull(sent.getHeader("Cookie"))
                val body = sent.body.readUtf8()
                assertTrue(body.contains("Only this selected fixture passage"))
                assertFalse(body.contains("fileId")); assertFalse(body.contains("fake-bookorbit-bearer"))
            } }
        } finally {
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
            context.deleteSharedPreferences(name); context.deleteSharedPreferences(ownerPrefs)
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
        }
    }
}
