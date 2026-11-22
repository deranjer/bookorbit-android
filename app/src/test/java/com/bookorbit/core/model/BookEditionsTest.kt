package com.bookorbit.core.model

import org.junit.Assert.*
import org.junit.Test

class BookEditionsTest {
    private fun book(files: List<BookFileRef>) = BookDetail(1, 1, "Test", "present", "2026-11-22", files = files)
    private val tracks = (1..75).map { BookFileRef(it, "mp3", "supplement", sizeBytes = 100, durationSeconds = 10.0) }

    @Test fun `75 tracks form one audio edition not 75 choices`() {
        val editions = BookEditions.of(book(tracks))
        assertEquals(1, editions.size)
        assertEquals(BookEditions.Kind.AUDIO, editions.single().kind)
        assertEquals((1..75).toSet(), editions.single().fileIds)
        assertEquals(7500L, editions.single().sizeBytes)
        assertEquals(750.0, editions.single().durationSeconds!!, 0.0)
    }

    @Test fun `text plus 75 tracks gives two edition choices`() {
        val editions = BookEditions.of(book(listOf(BookFileRef(100, "epub", "primary")) + tracks))
        assertEquals(2, editions.size)
        assertEquals((1..75).toSet(), BookEditions.additionalFiles(editions, setOf("audio"), emptySet()))
        assertEquals(setOf(100), BookEditions.additionalFiles(editions, setOf("text:100"), emptySet()))
        assertEquals((1..75).toSet() + 100, BookEditions.additionalFiles(editions, editions.map { it.key }.toSet(), emptySet()))
    }

    @Test fun `partial audio choice includes every missing track and preserves saved tracks`() {
        val editions = BookEditions.of(book(tracks))
        assertFalse(editions.single().isOffline((1..74).toSet()))
        assertEquals(setOf(75), BookEditions.additionalFiles(editions, setOf("audio"), (1..74).toSet()))
        assertTrue(editions.single().isOffline((1..75).toSet()))
    }

    @Test fun `text editions keep exact IDs and do not group by filename`() {
        val editions = BookEditions.of(book(listOf(BookFileRef(100, "epub", "primary", filename = "book.epub"), BookFileRef(101, "epub", "supplement", filename = "book.epub"))))
        assertEquals(2, editions.size)
        assertEquals(setOf(101), BookEditions.additionalFiles(editions, setOf("text:101"), emptySet()))
    }

    @Test fun `unknown duration and size are not shown as complete totals`() {
        val edition = BookEditions.of(book(listOf(tracks.first(), tracks[1].copy(sizeBytes = null, durationSeconds = null)))).single()
        assertNull(edition.sizeBytes)
        assertNull(edition.durationSeconds)
    }
}
