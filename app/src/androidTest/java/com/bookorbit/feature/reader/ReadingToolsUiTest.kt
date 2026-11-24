package com.bookorbit.feature.reader

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.bookorbit.R
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ReadingToolsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun selectionOpensInertThenTranslatesOnlyOnExplicitTap() {
        MockWebServer().use { server ->
            val repository = ReadingToolsRepository(OkHttpClient(), Json, server.url("/translate"), server.url("/dictionary/"))
            compose.setContent { MaterialTheme { ReadingToolsSheet("Hello", ReadingTool.TRANSLATE, repository, {}) } }
            compose.waitForIdle()
            assertEquals(0, server.requestCount)
            server.enqueue(MockResponse().setBody("""[[["Привет","Hello"]],null,"en"]"""))
            compose.onAllNodesWithText(compose.activity.getString(R.string.reader_translate)).filter(hasClickAction()).onFirst().performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Привет").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(1, server.requestCount)
            val request = server.takeRequest()
            assertEquals("Hello", request.requestUrl!!.queryParameter("q"))
            assertNull(request.getHeader("Authorization"))
        }
    }
}
