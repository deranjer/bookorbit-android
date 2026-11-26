package com.bookorbit.feature.reader

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerReaderPolicyTest {
    @Test fun `image credentials are limited to the exact selected file origin path and GET method`() {
        val policy = ServerReaderPolicy("https://books.example/prefix", 12, 34, "cbz")
        val image = "https://books.example/prefix/api/v1/cbz/files/34/pages/0".toHttpUrl()
        assertTrue(policy.isSelectedComicImage(image, "GET"))
        assertFalse(policy.isSelectedComicImage(image, "POST"))
        for (url in listOf(
            "https://books.example/api/v1/cbz/files/34/pages/0", "https://books.example/prefix/api/v1/cbz/files/35/pages/0",
            "https://evil.example/prefix/api/v1/cbz/files/34/pages/0", "https://books.example/prefix/api/v1/cbz/files/34/pages/0?token=other",
            "https://books.example/prefix/api/v1/cbz/files/34/pages/0/extra")) assertFalse(url, policy.isSelectedComicImage(url.toHttpUrl(), "GET"))
    }
    private val policy = ServerReaderPolicy("https://books.example:8443", 12, 34)

    @Test
    fun `opens selected book and file without credentials in URL`() {
        assertEquals("https://books.example:8443/read/12/34?format=epub", policy.readerUrl.toString())
    }

    @Test
    fun `refresh credentials require the exact origin endpoint and method`() {
        assertTrue(policy.isRefresh("https://books.example:8443/api/v1/auth/refresh".toHttpUrl(), "POST"))
        listOf(
            "https://evil.example:8443/api/v1/auth/refresh",
            "https://books.example.evil.example:8443/api/v1/auth/refresh",
            "http://books.example:8443/api/v1/auth/refresh",
            "https://books.example/api/v1/auth/refresh",
            "https://books.example:8443/api/v1/auth/refresh/extra",
            "https://books.example:8443/api/v1/auth/refresh?redirect=evil",
        ).forEach { assertFalse(it, policy.isRefresh(it.toHttpUrl(), "POST")) }
        assertFalse(policy.isRefresh("https://books.example:8443/api/v1/auth/refresh".toHttpUrl(), "GET"))
    }

    @Test
    fun `only selected reader remains in the embedded navigation`() {
        assertTrue(policy.isReader("https://books.example:8443/read/12/34?mode=read#chapter".toHttpUrl()))
        assertFalse(policy.isReader("https://books.example:8443/read/12/35".toHttpUrl()))
        assertFalse(policy.isReader("https://evil.example:8443/read/12/34".toHttpUrl()))
        assertFalse(policy.isReader("https://books.example:8443/".toHttpUrl()))
    }

    @Test
    fun `preserves configured base path`() {
        val scoped = ServerReaderPolicy("https://books.example/bookorbit/", 12, 34)
        assertEquals("https://books.example/bookorbit/read/12/34?format=epub", scoped.readerUrl.toString())
        assertTrue(scoped.isRefresh("https://books.example/bookorbit/api/v1/auth/refresh".toHttpUrl(), "POST"))
        assertFalse(scoped.isRefresh("https://books.example/api/v1/auth/refresh".toHttpUrl(), "POST"))
    }

    @Test fun `web reader receives the selected comic or PDF format instead of defaulting to EPUB`() {
        for (format in listOf("cbz", "cbr", "cb7", "pdf", "mobi", "fb2")) {
            val reader = ServerReaderPolicy("https://books.example/prefix", 12, 34, format.uppercase())
            assertEquals("https://books.example/prefix/read/12/34?format=$format", reader.readerUrl.toString())
        }
    }
}
