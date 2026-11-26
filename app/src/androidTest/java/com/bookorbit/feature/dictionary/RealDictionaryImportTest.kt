package com.bookorbit.feature.dictionary

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import kotlinx.serialization.json.Json

/** Explicit acceptance: push the official pinned archives to this isolated emulator first. */
class RealDictionaryImportTest {
    @Test fun officialPackagesImportAndSearchWithoutNetwork() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val dir = File(app.cacheDir, "dictionary-real-test-${UUID.randomUUID()}").apply { mkdirs() }
        val repository = DictionaryRepository(object : ContextWrapper(app) { override fun getFilesDir() = dir })
        try {
            val catalog = app.assets.open("dictionaries/catalog.json").bufferedReader().use { dictionaryCatalog(it.readText(), Json) }
            for (id in listOf("eng-rus", "rus-eng", "fra-eng", "spa-eng")) {
                val pack = catalog.single { it.id == id }
                val file = File("/data/local/tmp/bookorbit-dictionary-acceptance/${pack.id}.tar.xz")
                assertTrue("Push the pinned official dictionary before running acceptance", file.isFile)
                repository.installArchive(pack, file)
                val word = when (pack.id) { "eng-rus" -> "book"; "rus-eng" -> "книга"; "fra-eng" -> "livre"; else -> "libro" }
                val expected = if (pack.id == "eng-rus") "книга" else "book"
                assertTrue(repository.lookup(pack.id, word)!!.contains(expected))
            }
            assertEquals(4, repository.installed().size)
        } finally { dir.deleteRecursively() }
    }
}
