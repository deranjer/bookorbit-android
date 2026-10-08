package com.bookorbit.feature.reader.comic

import com.bookorbit.core.model.BookDetail
import com.bookorbit.core.model.BookFileRef
import com.bookorbit.core.model.BookFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComicPagingTest {
    @Test
    fun `percentage spans 0 to 100 and the last page is 100`() {
        assertEquals(0.0, ComicPaging.percentage(0, 8), 1e-9)
        assertEquals(100.0, ComicPaging.percentage(7, 8), 1e-9)
        assertEquals(100.0 * 3 / 7, ComicPaging.percentage(3, 8), 1e-9)
    }

    @Test
    fun `percentage handles one-page and empty comics`() {
        assertEquals(100.0, ComicPaging.percentage(0, 1), 1e-9)
        assertEquals(0.0, ComicPaging.percentage(0, 0), 1e-9)
    }

    @Test
    fun `percentage clamps an out-of-range page`() {
        assertEquals(100.0, ComicPaging.percentage(99, 8), 1e-9)
        assertEquals(0.0, ComicPaging.percentage(-3, 8), 1e-9)
    }

    @Test
    fun `resume page round-trips through the saved fraction`() {
        for (page in 0 until 8) {
            val fraction = ComicPaging.percentage(page, 8) / 100.0
            assertEquals(page, ComicPaging.pageForFraction(fraction, 8))
        }
    }

    @Test
    fun `resume page defaults to the start without a saved position`() {
        assertEquals(0, ComicPaging.pageForFraction(null, 8))
        assertEquals(0, ComicPaging.pageForFraction(0.9, 1))
        assertEquals(7, ComicPaging.pageForFraction(5.0, 8))
    }

    @Test
    fun `server page numbers are one-based`() {
        assertEquals(1, ComicPaging.pageNumber(0))
        assertEquals(8, ComicPaging.pageNumber(7))
    }

    private fun book(vararg files: Pair<String, String>) = BookDetail(
        id = 1, libraryId = 1, libraryName = "L", status = "active", addedAt = "2024-01-01",
        files = files.mapIndexed { i, (format, role) -> BookFileRef(id = i + 1, format = format, role = role) },
    )

    @Test
    fun `a CBR-only book opens in the comic reader`() {
        val target = BookFiles.readingTarget(book("cbr" to "primary"))
        assertTrue(target is BookFiles.ReadingTarget.Comic)
    }

    @Test
    fun `CB7 also opens in the comic reader`() {
        assertTrue(BookFiles.readingTarget(book("cb7" to "primary")) is BookFiles.ReadingTarget.Comic)
    }

    @Test
    fun `CBZ stays in the foliate reader and is no longer treated as server paged`() {
        assertTrue(BookFiles.readingTarget(book("cbz" to "primary")) is BookFiles.ReadingTarget.Foliate)
        assertNull(BookFiles.comicFile(book("cbz" to "primary")))
    }

    @Test
    fun `an EPUB beats a CBR when a book has both`() {
        assertTrue(BookFiles.readingTarget(book("cbr" to "primary", "epub" to "alternate")) is BookFiles.ReadingTarget.Foliate)
    }
}
