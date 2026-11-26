package com.bookorbit.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookFilesTest {

    @Test
    fun `explicit secondary EPUB is not replaced by primary`() {
        val b = book(file(1, "epub", "primary"), file(2, "epub"))
        assertEquals(2, (BookFiles.readingTarget(b, 2) as BookFiles.ReadingTarget.Foliate).file.id)
    }

    @Test
    fun `explicit PDF wins even when EPUB is present`() {
        val b = book(file(1, "epub", "primary"), file(2, "pdf"))
        assertEquals(2, (BookFiles.readingTarget(b, 2) as BookFiles.ReadingTarget.Pdf).file.id)
    }

    @Test
    fun `missing explicit file and non-readable content never fall back`() {
        val b = book(file(1, "epub"), file(2, "mp3"))
        assertEquals(BookFiles.ReadingTarget.None, BookFiles.readingTarget(b, 99))
        assertEquals(BookFiles.ReadingTarget.None, BookFiles.readingTarget(b, 2))
    }

    @Test
    fun `combined book allows exact text audio or both selection`() {
        val b = book(file(1, "epub"), file(2, "MP3"), file(3, "mp3"), file(4, "jpg", "primary"))
        assertEquals(listOf(1, 2, 3), BookFiles.downloadCandidates(b).map { it.id })
        assertEquals(listOf(1), BookFiles.selectedDownloads(b, setOf(1)).map { it.id })
        assertEquals(listOf(2, 3), BookFiles.selectedDownloads(b, setOf(3, 2)).map { it.id })
        assertEquals(listOf(1, 2, 3), BookFiles.selectedDownloads(b, setOf(1, 2, 3)).map { it.id })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unknown selected ID never falls back to another edition`() {
        BookFiles.selectedDownloads(book(file(1, "epub")), setOf(99))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `cover is not a downloadable book`() {
        BookFiles.selectedDownloads(book(file(1, "jpg", "primary")), setOf(1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `empty selection cannot enqueue anything`() {
        BookFiles.selectedDownloads(book(file(1, "epub")), emptySet())
    }

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
