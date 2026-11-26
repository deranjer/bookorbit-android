package com.bookorbit.feature.dictionary

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.bookorbit.R
import com.bookorbit.feature.reader.ReadingTool
import com.bookorbit.feature.reader.ReadingToolsRepository
import com.bookorbit.feature.reader.ReadingToolsSheet
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class OfflineDictionaryUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun openingOfflineSourceDoesNotDownloadOrSendSelectedWord() {
        val prefs = compose.activity.getSharedPreferences("reading_tools", android.content.Context.MODE_PRIVATE)
        val oldMode = prefs.getBoolean("dictionary_offline", false)
        val oldId = prefs.getString("dictionary_id", "eng-rus")
        val dir = File(compose.activity.cacheDir, "dictionary-ui-test-${UUID.randomUUID()}")
        val requests = AtomicInteger()
        try {
            prefs.edit().putBoolean("dictionary_offline", false).apply()
            MockWebServer().use { server ->
                val online = ReadingToolsRepository(OkHttpClient(), Json, server.url("/translate"), server.url("/dictionary/"))
                val offline = DictionaryRepository(dir, OkHttpClient.Builder().addInterceptor { requests.incrementAndGet(); throw IOException("Unexpected download") }.build())
                compose.setContent { MaterialTheme { ReadingToolsSheet("book", ReadingTool.DICTIONARY, online, {}, offline) } }
                compose.onNodeWithText(compose.activity.getString(R.string.dictionary_offline)).performScrollTo().performClick()
                compose.onNodeWithText(compose.activity.getString(R.string.dictionary_find_offline)).performScrollTo().assertIsNotEnabled()
                compose.waitForIdle()
                assertEquals(0, server.requestCount)
                assertEquals(0, requests.get())
                assertTrue(dir.listFiles().orEmpty().isEmpty())
            }
        } finally {
            prefs.edit().putBoolean("dictionary_offline", oldMode).putString("dictionary_id", oldId).apply()
            dir.deleteRecursively()
        }
    }
}
