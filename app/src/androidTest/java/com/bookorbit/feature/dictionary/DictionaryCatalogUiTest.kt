package com.bookorbit.feature.dictionary

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.bookorbit.R
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class DictionaryCatalogUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun catalogChoosesFrenchAndPersistsWithoutDownloadingOrRefreshing() {
        val context = compose.activity
        val prefs = context.getSharedPreferences("reading_tools", android.content.Context.MODE_PRIVATE)
        val previous = prefs.getString("dictionary_id", null)
        val root = File(context.cacheDir, "dictionary-catalog-ui-${UUID.randomUUID()}")
        val requests = AtomicInteger()
        try {
            prefs.edit().putString("dictionary_id", "eng-rus").apply()
            val catalog = context.assets.open("dictionaries/catalog.json").bufferedReader().use { dictionaryCatalog(it.readText(), Json) }
            assertTrue(catalog.size >= 300)
            val repository = DictionaryRepository(root, OkHttpClient.Builder().addInterceptor { requests.incrementAndGet(); throw IOException("Unexpected request") }.build(), { catalog })
            var visible by mutableStateOf(true)
            compose.setContent { MaterialTheme { if (visible) Column(Modifier.verticalScroll(rememberScrollState())) {
                OfflineDictionaryPanel("livre", repository)
            } } }
            val locale = context.resources.configuration.locales[0]
            fun pair(source: String, target: String) = dictionaryLanguage(source, locale) + " → " + dictionaryLanguage(target, locale)
            compose.onNodeWithText(pair("eng", "rus")).performScrollTo().performClick()
            compose.onNodeWithText(context.getString(R.string.dictionary_search_language)).performTextInput("fra eng")
            compose.onNodeWithText(pair("fra", "eng")).performClick()
            compose.runOnIdle { assertEquals("fra-eng", prefs.getString("dictionary_id", null)); visible = false }
            compose.waitForIdle()
            compose.runOnIdle { visible = true }
            compose.onNodeWithText(pair("fra", "eng")).assertExists()
            compose.onNodeWithText(context.getString(R.string.dictionary_find_offline)).performScrollTo().assertIsNotEnabled()
            assertEquals(0, requests.get())
            assertTrue(root.listFiles().orEmpty().isEmpty())
        } finally {
            prefs.edit().apply { if (previous == null) remove("dictionary_id") else putString("dictionary_id", previous) }.commit()
            root.deleteRecursively()
        }
    }
}
