package com.bookorbit.feature.reader.comic

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ComicArchiveTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun archive(vararg names: String): Pair<File, File> {
        val file = temporary.newFile("synthetic.cbz")
        ZipOutputStream(file.outputStream()).use { zip -> names.forEach { name ->
            zip.putNextEntry(ZipEntry(name)); zip.write("synthetic-$name".toByteArray()); zip.closeEntry()
        } }
        return file to temporary.newFolder("pages")
    }

    @Test fun `numbered pages match comic order and legacy CFI retains the exact image`() = runBlocking {
        val (file, cache) = archive("01.png", "10.png", "2.png", "ComicInfo.xml", ".hidden.png")
        val book = ComicArchive(file, cache)
        assertEquals(listOf("01.png", "2.png", "10.png"), book.pages)
        // Legacy Foliate sorted .hidden, 01, 10, 2 lexicographically.
        assertEquals(2, book.legacyPage(2))
        assertEquals(1, book.legacyPage(3))
        assertTrue(runCatching { book.legacyPage(0) }.isFailure)
        assertEquals("synthetic-2.png", book.page(1).readText())
    }

    @Test fun `archive paths never become output paths and modified archives cannot reuse extracted images`() = runBlocking {
        val (file, cache) = archive("../outside.png", "nested/3.png")
        val book = ComicArchive(file, cache)
        assertEquals(listOf("nested/3.png"), book.pages)
        val output = book.page(0)
        assertEquals(cache.canonicalFile, output.parentFile.canonicalFile)
        assertEquals("0.png", output.name)
        file.appendBytes(byteArrayOf(0))
        assertTrue(runCatching { book.page(0) }.exceptionOrNull() is java.io.IOException)
    }
}
