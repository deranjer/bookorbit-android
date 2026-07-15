package com.bookorbit.core.storage

import com.bookorbit.core.model.BookDetail
import com.bookorbit.core.model.BookFileRef
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadFileNamingTest {

    private fun book(title: String? = "Sea of Tranquility") = BookDetail(
        id = 482,
        libraryId = 1,
        libraryName = "Lib",
        status = "active",
        addedAt = "2026-01-01",
        title = title,
    )

    @Test
    fun `sanitize strips illegal filesystem characters`() {
        assertEquals("Book_ Title__2024_", DownloadFileNaming.sanitize("Book: Title/\\2024?"))
    }

    @Test
    fun `sanitize falls back to untitled for blank input`() {
        assertEquals("untitled", DownloadFileNaming.sanitize("   "))
    }

    @Test
    fun `sanitize truncates very long names`() {
        val long = "a".repeat(200)
        assertEquals(120, DownloadFileNaming.sanitize(long).length)
    }

    @Test
    fun `bookFolderName appends the book id for uniqueness`() {
        assertEquals("Sea of Tranquility (482)", DownloadFileNaming.bookFolderName("Sea of Tranquility", 482))
    }

    @Test
    fun `bookFolderName falls back to a generic name when title is missing`() {
        assertEquals("Book (482)", DownloadFileNaming.bookFolderName(null, 482))
    }

    @Test
    fun `extensionOf prefers the declared format`() {
        val ref = BookFileRef(id = 1, format = "M4B", role = "primary", filename = "chapter.mp3")
        assertEquals("m4b", DownloadFileNaming.extensionOf(ref))
    }

    @Test
    fun `extensionOf falls back to the filename extension`() {
        val ref = BookFileRef(id = 1, format = null, role = "primary", filename = "chapter.mp3")
        assertEquals("mp3", DownloadFileNaming.extensionOf(ref))
    }

    @Test
    fun `extensionOf defaults to bin when nothing is known`() {
        val ref = BookFileRef(id = 1, format = null, role = "primary")
        assertEquals("bin", DownloadFileNaming.extensionOf(ref))
    }

    @Test
    fun `fileName prefers the server filename`() {
        val ref = BookFileRef(id = 1, format = "mp3", role = "primary", filename = "01 - Intro.mp3")
        assertEquals("01 - Intro.mp3", DownloadFileNaming.fileName(ref, index = 0, book = book()))
    }

    @Test
    fun `fileName falls back to the book title with an index suffix when no filename is known`() {
        val ref = BookFileRef(id = 1, format = "mp3", role = "primary")
        assertEquals("Sea of Tranquility.mp3", DownloadFileNaming.fileName(ref, index = 0, book = book()))
        assertEquals("Sea of Tranquility 2.mp3", DownloadFileNaming.fileName(ref, index = 1, book = book()))
    }

    @Test
    fun `mimeTypeFor uses the override table for formats MimeTypeMap misses`() {
        assertEquals("audio/mp4", DownloadFileNaming.mimeTypeFor("m4b"))
        assertEquals("application/vnd.comicbook+zip", DownloadFileNaming.mimeTypeFor("cbz"))
        assertEquals("application/vnd.comicbook-rar", DownloadFileNaming.mimeTypeFor("cbr"))
        assertEquals("application/vnd.amazon.ebook", DownloadFileNaming.mimeTypeFor("azw3"))
        assertEquals("application/x-fictionbook+xml", DownloadFileNaming.mimeTypeFor("fb2"))
        assertEquals("application/x-mobipocket-ebook", DownloadFileNaming.mimeTypeFor("mobi"))
        assertEquals("application/epub+zip", DownloadFileNaming.mimeTypeFor("epub"))
    }

    @Test
    fun `mimeTypeFor override lookup is case-insensitive`() {
        assertEquals("audio/mp4", DownloadFileNaming.mimeTypeFor("M4B"))
    }
}
