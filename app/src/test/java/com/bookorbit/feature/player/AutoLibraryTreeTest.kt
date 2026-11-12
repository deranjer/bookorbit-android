package com.bookorbit.feature.player

import com.bookorbit.core.model.BookCard
import com.bookorbit.core.model.BookFileRef
import org.junit.Assert.*
import org.junit.Test

class AutoLibraryTreeTest {
    private val labels = AutoBrowseTree.Labels("App", "Continue", "Downloads", "Audiobook", { it },
        byTitle = "Titles*", byAuthor = "Authors*", bySeries = "Series*", unknownAuthor = "Unknown*")
    private fun book(id: Int, title: String, authors: List<String> = listOf("Author"), series: String? = null,
        index: Double? = null, format: String = "mp3") = BookCard(id, title = title, authors = authors,
        seriesName = series, seriesIndex = index, files = listOf(BookFileRef(id, format, "primary")), addedAt = "")

    @Test fun `audiobooks offers localized browse modes and small lists open directly`() {
        assertEquals(listOf("Titles*", "Authors*", "Series*"), AutoLibraryTree.children("audiobooks", emptyList(), labels).map { it.title })
        val books = listOf(book(1, "Zulu"), book(2, "Éclair"), book(3, "alpha"), book(4, "Ebook", format = "epub"))
        assertEquals(listOf("alpha", "Éclair", "Zulu"), AutoLibraryTree.children(AutoLibraryTree.TITLES, books, labels).map { it.title })
    }

    @Test fun `authors include coauthors unknown authors and safely encoded names`() {
        val books = listOf(book(1, "One", listOf("A/B + C", "Coauthor")), book(2, "Two", emptyList()),
            book(3, "Ebook", listOf("Ebook only"), format = "epub"))
        val authors = AutoLibraryTree.children(AutoLibraryTree.AUTHORS, books, labels)
        assertEquals(listOf("A/B + C", "Coauthor", "Unknown*"), authors.map { it.title })
        assertEquals(listOf("book/1"), AutoLibraryTree.children(authors.first().mediaId, books, labels).map { it.mediaId })
        assertEquals(authors.first(), AutoLibraryTree.item(authors.first().mediaId, books, labels))
        assertEquals(listOf("book/2"), AutoLibraryTree.children(authors.last().mediaId, books, labels).map { it.mediaId })
    }

    @Test fun `series exclude standalone ebooks and order by numeric series position`() {
        val books = listOf(book(1, "Ten", series = "Saga / +", index = 10.0), book(2, "Two", series = "Saga / +", index = 2.0),
            book(3, "Novella", series = "Saga / +", index = 2.5), book(4, "Unknown", series = "Saga / +"),
            book(5, "Standalone"), book(6, "Ebook", series = "Ebook series", format = "epub"))
        val series = AutoLibraryTree.children(AutoLibraryTree.SERIES, books, labels).single()
        assertEquals("4 books", series.subtitle)
        assertEquals(listOf("book/2", "book/3", "book/1", "book/4"), AutoLibraryTree.children(series.mediaId, books, labels).map { it.mediaId })
    }

    private fun flatten(id: String, books: List<BookCard>, depth: Int = 0): List<String> {
        assertTrue("Navigation must terminate", depth < 8)
        val children = AutoLibraryTree.children(id, books, labels)
        assertTrue(children.size <= 40)
        return children.flatMap {
            if (it.isPlayable) listOf(it.mediaId) else {
                assertEquals(it, AutoLibraryTree.item(it.mediaId, books, labels))
                flatten(it.mediaId, books, depth + 1)
            }
        }
    }

    @Test fun `large mixed alphabet library reaches every book including punctuation accents and duplicate titles`() {
        val books = (1..2000).map { id ->
            val prefix = listOf("Apple", "Éclair", "Zulu", "8", "!", "😀")[id % 6]
            book(id, "$prefix ${id / 3}")
        }
        assertEquals(listOf("#", "0–9", "A", "E", "Z"), AutoLibraryTree.children(AutoLibraryTree.TITLES, books, labels).map { it.title })
        val found = flatten(AutoLibraryTree.TITLES, books)
        assertEquals(books.size, found.size)
        assertEquals(books.map { "book/${it.id}" }.toSet(), found.toSet())
    }

    @Test fun `long series preserves order through named ranges`() {
        val books = (1..81).map { book(it, "Volume ${82 - it}", series = "Saga", index = it.toDouble()) }
        val series = AutoLibraryTree.children(AutoLibraryTree.SERIES, books, labels).single()
        assertEquals(books.map { "book/${it.id}" }, flatten(series.mediaId, books))
        assertEquals("Volume 81 – Volume 42", AutoLibraryTree.children(series.mediaId, books, labels).first().title)
    }

    @Test fun `content anchors survive reordered input and malformed routes fail`() {
        val books = (1..45).map { book(it, "Apple $it") } + book(46, "Zulu")
        val group = AutoLibraryTree.children(AutoLibraryTree.TITLES, books, labels).first()
        assertEquals(AutoLibraryTree.children(group.mediaId, books, labels), AutoLibraryTree.children(group.mediaId, books.reversed(), labels))
        assertNull(AutoLibraryTree.parseGroup("alphabet/%invalid/x/y"))
        assertThrows(AutoRequestException::class.java) { AutoLibraryTree.children("audiobooks/999", books, labels) }
    }
}
