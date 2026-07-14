package com.bookorbit.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookFilesTest {

    private fun book(vararg files: BookFileRef) = BookDetail(
        id = 1,
        libraryId = 1,
        libraryName = "Lib",
        status = "active",
        addedAt = "2026-01-01",
        files = files.toList(),
    )

    private fun file(id: Int, format: String?, role: String = "supplementary") =
        BookFileRef(id = id, format = format, role = role)

    @Test
    fun `pdf-only book routes to the PDF reader`() {
        val b = book(file(1, "pdf", role = "primary"))
        assertTrue(BookFiles.isPdf(b))
        assertFalse(BookFiles.isReadable(b))
        assertEquals(1, BookFiles.pdfFile(b)?.id)
        assertTrue(BookFiles.readingTarget(b) is BookFiles.ReadingTarget.Pdf)
    }

    @Test
    fun `epub-only book routes to the foliate reader`() {
        val b = book(file(1, "epub", role = "primary"))
        assertTrue(BookFiles.isReadable(b))
        assertFalse(BookFiles.isPdf(b))
        assertTrue(BookFiles.readingTarget(b) is BookFiles.ReadingTarget.Foliate)
    }

    @Test
    fun `foliate wins when both an epub and a pdf are present`() {
        val b = book(file(1, "pdf", role = "primary"), file(2, "epub"))
        // PDF is primary, but the foliate-readable epub still takes precedence.
        val target = BookFiles.readingTarget(b)
        assertTrue(target is BookFiles.ReadingTarget.Foliate)
        assertEquals(2, (target as BookFiles.ReadingTarget.Foliate).file.id)
    }

    @Test
    fun `pdfFile prefers the primary file when it is a pdf`() {
        val b = book(file(1, "pdf"), file(2, "pdf", role = "primary"))
        assertEquals(2, BookFiles.pdfFile(b)?.id)
    }

    @Test
    fun `audio-only book is neither readable nor pdf`() {
        val b = book(file(1, "mp3", role = "primary"))
        assertFalse(BookFiles.isReadable(b))
        assertFalse(BookFiles.isPdf(b))
        assertNull(BookFiles.pdfFile(b))
        assertEquals(BookFiles.ReadingTarget.None, BookFiles.readingTarget(b))
    }

    @Test
    fun `badgeFormat ignores a stray non-content file ahead of the real one`() {
        // Regression for a book card badge showing "JPG" instead of the audiobook's real format.
        val files = listOf(file(1, "jpg"), file(2, "m4b", role = "primary"))
        assertEquals("m4b", BookFiles.badgeFormat(files))
    }

    @Test
    fun `badgeFormat falls back to the first content file when primary is not content`() {
        val files = listOf(file(1, "jpg", role = "primary"), file(2, "epub"))
        assertEquals("epub", BookFiles.badgeFormat(files))
    }

    @Test
    fun `badgeFormat returns null when no file is a known content format`() {
        val files = listOf(file(1, "jpg", role = "primary"))
        assertNull(BookFiles.badgeFormat(files))
    }
}
