package com.bookorbit.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DataScopeTest {
    private val prod = DataScope.key("https://Prod.example.com/")

    @Test
    fun `the owning server keeps the original, unscoped data`() {
        assertEquals("", DataScope.of(prod, "https://prod.example.com"))
        assertEquals("", DataScope.of(prod, "https://PROD.example.com/"))
    }

    @Test
    fun `an install with no owner yet, or no server, is unscoped`() {
        assertEquals("", DataScope.of(null, "https://prod.example.com"))
        assertEquals("", DataScope.of(prod, null))
        assertEquals("", DataScope.of(prod, " "))
    }

    @Test
    fun `another server gets its own stable scope`() {
        val demo = DataScope.of(prod, "https://demo.example.com")
        assertNotEquals("", demo)
        assertEquals(12, demo.length)
        assertEquals(demo, DataScope.of(prod, "https://Demo.example.com/"))
        assertNotEquals(demo, DataScope.of(prod, "https://other.example.com"))
    }
}
