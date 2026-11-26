package com.bookorbit.feature.ai

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.bookorbit.R
import com.bookorbit.core.auth.SecureStorage
import com.bookorbit.core.auth.SessionManager
import com.bookorbit.core.model.AuthUser
import com.bookorbit.core.network.ApiService
import com.bookorbit.feature.readinghelp.ReadingHelpRepository
import com.bookorbit.feature.readinghelp.ReadingHelpSheet
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.security.KeyStore
import java.util.UUID

class CustomAiConnectionUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun ollamaWithoutTokenRequiresHttpOptInAndAddressSpecificConsent() = exercise(AiProvider.OLLAMA)
    @Test fun compatibleWithoutTokenUsesConfiguredApiPrefix() = exercise(AiProvider.OPENAI_COMPATIBLE)

    private fun exercise(provider: AiProvider) {
        val context = compose.activity
        val name = "custom-ai-ui-${UUID.randomUUID()}"
        val alias = "$name-key"
        try {
            val session = SessionManager(SecureStorage(context, name, alias, "$name-owner"), Json).apply {
                setServerUrl("https://fixture.example"); signIn("fake-bookorbit-token", AuthUser(1, "fixture"))
            }
            val store = AiConnectionStore(SecureStorage(context, name, alias, "$name-owner"), session)
            MockWebServer().use { endpoint -> MockWebServer().use { bookorbit ->
                val base = endpoint.url(if (provider == AiProvider.OLLAMA) "/proxy/" else "/proxy/v1/").toString()
                val api = Retrofit.Builder().baseUrl(bookorbit.url("/")).addConverterFactory(Json.asConverterFactory("application/json".toMediaType())).build().create(ApiService::class.java)
                val repository = ReadingHelpRepository(api, Json, store, DirectReadingHelp(store, session), session)
                var settings by mutableStateOf(true)
                compose.setContent { MaterialTheme {
                    if (settings) AiConnectionDialog(store, session.owner()) { settings = false }
                    else ReadingHelpSheet(42, "Only selected custom-server passage", repository, {})
                } }
                compose.onNodeWithText(context.getString(R.string.ai_route_direct)).performScrollTo().performClick()
                compose.onNodeWithText(AiProvider.OPENAI.displayName).performScrollTo().performClick()
                compose.onNodeWithText(provider.displayName).performScrollTo().performClick()
                compose.onNodeWithText(context.getString(R.string.ai_base_url)).performScrollTo().performTextReplacement(base)
                compose.onNodeWithText(context.getString(R.string.ai_help_model)).performScrollTo().performTextInput("org/model:fixture")
                compose.onNodeWithText(context.getString(R.string.ai_save)).assertIsNotEnabled()
                androidx.test.espresso.Espresso.closeSoftKeyboard()
                compose.onNode(isToggleable()).performScrollTo().performClick()
                assertEquals(0, endpoint.requestCount); assertEquals(0, bookorbit.requestCount)
                compose.onNodeWithText(context.getString(R.string.ai_save)).performClick()
                compose.runOnIdle { assertFalse("Saving must close settings", settings); assertEquals(AiRoute.DIRECT, store.connection(session.owner()!!).route) }
                val ask = context.getString(R.string.help_ask)
                compose.waitUntil(10_000) { compose.onAllNodesWithText(ask).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText(ask).performScrollTo().performClick()
                compose.onNodeWithText(context.getString(R.string.ai_http_warning), substring = true).assertIsDisplayed()
                assertTrue(compose.onAllNodesWithText(base, substring = true).fetchSemanticsNodes().isNotEmpty())
                assertEquals(0, endpoint.requestCount)
                val response = if (provider == AiProvider.OLLAMA) """{"done":true,"done_reason":"stop","message":{"content":"Local fixture answer","thinking":"not displayed"}}"""
                    else """{"choices":[{"finish_reason":"stop","message":{"content":"Local fixture answer"}}]}"""
                endpoint.enqueue(MockResponse().setBody(response))
                compose.onNodeWithText(context.getString(R.string.help_send)).performClick()
                compose.waitUntil(10_000) { compose.onAllNodesWithText("Local fixture answer").fetchSemanticsNodes().isNotEmpty() }
                assertEquals(1, endpoint.requestCount); assertEquals(0, bookorbit.requestCount)
                val sent = endpoint.takeRequest()
                assertEquals(if (provider == AiProvider.OLLAMA) "/proxy/api/chat" else "/proxy/v1/chat/completions", sent.path)
                assertNull(sent.getHeader("Authorization")); assertNull(sent.getHeader("Cookie"))
                val body = Json.parseToJsonElement(sent.body.readUtf8()).jsonObject
                assertEquals("org/model:fixture", body.getValue("model").jsonPrimitive.content)
                assertFalse(body.getValue("stream").jsonPrimitive.boolean)
            } }
        } finally {
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
            context.deleteSharedPreferences(name); context.deleteSharedPreferences("$name-owner")
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
        }
    }
}
