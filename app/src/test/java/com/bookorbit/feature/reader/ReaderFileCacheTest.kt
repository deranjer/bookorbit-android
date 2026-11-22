package com.bookorbit.feature.reader

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReaderFileCacheTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun `failed request never publishes partial unknown-sized file`() = runTest {
        val dest = File(temp.root, "book.epub")
        assertTrue(runCatching {
            ReaderFileCache.getOrFetch(dest, null) { it.writeText("partial"); error("offline") }
        }.isFailure)
        assertFalse(dest.exists())
        assertTrue(temp.root.listFiles()!!.isEmpty())
        ReaderFileCache.getOrFetch(dest, null) { it.writeText("complete") }
        assertEquals("complete", dest.readText())
    }

    @Test fun `short response never replaces previous cached bytes`() = runTest {
        val dest = temp.newFile("book.epub").apply { writeText("old") }
        assertTrue(runCatching {
            ReaderFileCache.getOrFetch(dest, 8) { it.writeText("short") }
        }.isFailure)
        assertEquals("old", dest.readText())
        ReaderFileCache.getOrFetch(dest, 8) { it.writeText("complete") }
        assertEquals("complete", dest.readText())
    }

    @Test fun `completed unknown-sized file can be reused without fetching`() = runTest {
        val dest = File(temp.root, "book.epub")
        ReaderFileCache.getOrFetch(dest, null) { it.writeText("complete") }
        ReaderFileCache.getOrFetch(dest, null) { error("must not fetch") }
        assertEquals("complete", dest.readText())
    }
}
