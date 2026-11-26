package com.bookorbit.feature.visionscan

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.bookorbit.R
import com.bookorbit.core.network.ApiService
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

class VisionScanAccessUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun restrictedAccessExplainsServerRouteAndCanBeRecheckedWithoutSendingPhoto() {
        MockWebServer().use { server ->
            fun appInfo() = MockResponse().setBody("""{"version":"3.9.0","features":["vision-scan"],"visionScan":{"enabled":true}}""")
            server.enqueue(appInfo())
            server.enqueue(MockResponse().setResponseCode(403).setBody("""{"errorCode":"APP_ATTEST_REQUIRED"}"""))
            val json = Json { ignoreUnknownKeys = true }
            val api = Retrofit.Builder().baseUrl(server.url("/")).addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build().create(ApiService::class.java)
            val repository = VisionScanRepository(api, json)
            compose.setContent { MaterialTheme { VisionScanSheet(repository, {}, {}) } }
            val context = compose.activity
            compose.waitUntil(10_000) { compose.onAllNodesWithText(context.getString(R.string.vision_restricted)).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(context.getString(R.string.vision_server_route)).assertExists()
            compose.onNodeWithText(context.getString(R.string.vision_device_help)).assertExists()
            compose.onNodeWithText(context.getString(R.string.vision_pick)).performScrollTo().assertIsEnabled()
            compose.onNodeWithText(context.getString(R.string.vision_scan)).performScrollTo().assertIsNotEnabled()
            assertEquals(2, server.requestCount)
            server.enqueue(appInfo())
            server.enqueue(MockResponse().setResponseCode(400).setBody("{}"))
            compose.onNodeWithText(context.getString(R.string.retry)).performScrollTo().performClick()
            compose.waitUntil(10_000) { server.requestCount == 4 && compose.onAllNodesWithText(context.getString(R.string.vision_restricted)).fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithText(context.getString(R.string.vision_scan)).performScrollTo().assertIsNotEnabled()
            repeat(2) {
                val get = server.takeRequest(5, TimeUnit.SECONDS)!!
                val probe = server.takeRequest(5, TimeUnit.SECONDS)!!
                assertEquals("GET", get.method)
                assertEquals("{}", probe.body.readUtf8())
            }
        }
    }
}
