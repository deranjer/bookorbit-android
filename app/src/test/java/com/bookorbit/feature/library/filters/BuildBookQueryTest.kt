package com.bookorbit.feature.library.filters

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers the curated filter state -> server BookQuery rule-tree mapping. */
class BuildBookQueryTest {

    @Test
    fun `no filters produces null filter and default sort`() {
        val query = buildBookQuery(LibraryFilters(), LibrarySort(), page = 0, size = 50)
        assertNull(query.filter)
        assertEquals(1, query.sort.size)
        assertEquals("title", query.sort[0].field)
        assertEquals("asc", query.sort[0].dir)
        assertEquals(0, query.pagination.page)
        assertEquals(50, query.pagination.size)
    }

    @Test
    fun `read status becomes an includesAny rule`() {
        val filters = LibraryFilters(readStatus = listOf("reading", "read"))
        val query = buildBookQuery(filters, LibrarySort(), 0, 50)
        val rules = query.filter!!.jsonObject["rules"]!!.jsonArray
        assertEquals(1, rules.size)
        val rule = rules[0].jsonObject
        assertEquals("rule", rule["type"]!!.jsonPrimitive.content)
        assertEquals("readStatus", rule["field"]!!.jsonPrimitive.content)
        assertEquals("includesAny", rule["operator"]!!.jsonPrimitive.content)
        assertEquals(listOf("reading", "read"), rule["value"]!!.jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun `read progress maps to the matching operator`() {
        val rule = buildBookQuery(LibraryFilters(readProgress = "finished"), LibrarySort(), 0, 50)
            .filter!!.jsonObject["rules"]!!.jsonArray[0].jsonObject
        assertEquals("readProgress", rule["field"]!!.jsonPrimitive.content)
        assertEquals("isFinished", rule["operator"]!!.jsonPrimitive.content)
    }

    @Test
    fun `year range emits a between rule with valueTo`() {
        val rule = buildBookQuery(LibraryFilters(yearFrom = 1990, yearTo = 2000), LibrarySort(), 0, 50)
            .filter!!.jsonObject["rules"]!!.jsonArray[0].jsonObject
        assertEquals("between", rule["operator"]!!.jsonPrimitive.content)
        assertEquals(1990, rule["value"]!!.jsonPrimitive.content.toInt())
        assertEquals(2000, rule["valueTo"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun `countActiveFilters counts each populated control once`() {
        val filters = LibraryFilters(
            readStatus = listOf("reading"),
            formats = listOf("epub"),
            minRating = 4,
        )
        assertEquals(3, countActiveFilters(filters))
        assertTrue(countActiveFilters(LibraryFilters()) == 0)
    }
}
