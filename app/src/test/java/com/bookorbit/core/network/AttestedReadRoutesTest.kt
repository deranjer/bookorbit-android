package com.bookorbit.core.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class AttestedReadRoutesTest {
    @Test fun `audited guarded GET routes match after arbitrary reverse proxy prefix`() {
        for (prefix in listOf("", "/books")) for (route in listOf("dashboard/widgets/reading-streak", "dashboard/widgets/reading-goal", "smart-scopes/7/books")) {
            assertTrue(AttestedReadRoutes.matches("GET", "https://books.test$prefix/api/v1/$route?fileId=7".toHttpUrl()))
        }
    }

    @Test fun `unguarded downloads progress auth batch and similar paths are not signed`() {
        for (route in listOf("auth/login", "books/1/serve", "books/files/7/progress", "dashboard/widgets/batch",
            "smart-scopes", "smart-scopes/7/books/query", "smart-scopes/7/books/more", "dashboard/widgets/reading-streaks",
            "smart-scopes/7%2Fbooks", "smart-scopes/0/books", "mobile/reading-help")) {
            assertFalse(route, AttestedReadRoutes.matches("GET", "https://books.test/api/v1/$route".toHttpUrl()))
        }
        assertFalse(AttestedReadRoutes.matches("POST", "https://books.test/api/v1/dashboard/widgets/reading-streak".toHttpUrl()))
        assertFalse(AttestedReadRoutes.matches("GET", "https://books.test/dashboard/widgets/reading-streak".toHttpUrl()))
    }
}
