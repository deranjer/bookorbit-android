package com.bookorbit.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SerializationTest {

    // Same config the app uses (NetworkModule.provideJson).
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Test
    fun `BooksPage decodes and ignores unknown fields`() {
        val raw = """
            {
              "items": [
                {"id": 1, "title": "A Book", "authors": ["Jane Doe"], "addedAt": "2024-01-01",
                 "hasCover": true, "unknownField": 42}
              ],
              "total": 1, "page": 0, "size": 50, "extra": "ignored"
            }
        """.trimIndent()
        val page = json.decodeFromString(BooksPage.serializer(), raw)
        assertEquals(1, page.items.size)
        assertEquals("A Book", page.items[0].title)
        assertEquals(listOf("Jane Doe"), page.items[0].authors)
        assertEquals(1, page.total)
    }

    @Test
    fun `BookDetail tolerates missing optional fields`() {
        val raw = """
            {"id": 7, "libraryId": 2, "libraryName": "Main", "status": "reading", "addedAt": "2024-01-01"}
        """.trimIndent()
        val detail = json.decodeFromString(BookDetail.serializer(), raw)
        assertEquals(7, detail.id)
        assertEquals("Main", detail.libraryName)
        assertTrue(detail.authors.isEmpty())
        assertTrue(detail.files.isEmpty())
    }

    @Test
    fun `BookFiles detects audiobooks and readable ebooks`() {
        val audiobook = BookDetail(
            id = 1, libraryId = 1, libraryName = "L", status = "u", addedAt = "x",
            files = listOf(BookFileRef(id = 1, format = "m4b", role = "primary")),
        )
        assertTrue(BookFiles.isAudiobook(audiobook))
        assertFalse(BookFiles.isReadable(audiobook))

        val epub = BookDetail(
            id = 2, libraryId = 1, libraryName = "L", status = "u", addedAt = "x",
            files = listOf(BookFileRef(id = 9, format = "epub", role = "primary")),
        )
        assertTrue(BookFiles.isReadable(epub))
        assertEquals(9, BookFiles.readableFile(epub)?.id)
        assertFalse(BookFiles.isAudiobook(epub))
    }
}
