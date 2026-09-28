package com.bookorbit.core.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Test

class ScrollerBooksTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun decode(body: String) = ScrollerBooks.decode(json, json.decodeFromString<JsonElement>(body))

    @Test
    fun `decodes the 3_x books-and-total object`() {
        val books = decode("""{"books":[{"id":1,"addedAt":"2026-01-01"},{"id":2,"addedAt":"2026-01-02"}],"total":null}""")
        assertEquals(listOf(1, 2), books.map { it.id })
    }

    @Test
    fun `decodes the pre-3_0 bare array`() {
        val books = decode("""[{"id":3,"addedAt":"2026-01-01"}]""")
        assertEquals(listOf(3), books.map { it.id })
    }
}
