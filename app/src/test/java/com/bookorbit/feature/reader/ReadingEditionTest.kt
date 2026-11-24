package com.bookorbit.feature.reader

import com.bookorbit.core.model.*
import org.junit.Assert.*
import org.junit.Test

class ReadingEditionTest {
    private val book = BookDetail(1, 1, "Library", "READY", "2026-01-01", files = listOf(
        BookFileRef(2, "epub", "primary"), BookFileRef(3, "pdf", "supplementary"), BookFileRef(4, "cbz", "supplementary"), BookFileRef(5, "mp3", "supplementary")))
    @Test fun `main read action opens the remembered exact edition`() {
        assertEquals(3, preferredReadingFileId(book, 3))
        assertEquals(4, preferredReadingFileId(book, 4))
    }
    @Test fun `missing or audio edition falls back to the normal readable primary`() {
        assertEquals(2, preferredReadingFileId(book, null))
        assertEquals(2, preferredReadingFileId(book, 99))
        assertEquals(2, preferredReadingFileId(book, 5))
    }
    @Test fun `CBZ now uses the shared native comic pager`() {
        assertEquals(4, (BookFiles.readingTarget(book, 4) as BookFiles.ReadingTarget.Comic).file.id)
    }
}
