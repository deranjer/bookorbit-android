package com.bookorbit.feature.reader

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class ReadingToolsTest {
    @Test fun `dictionary strips sentence punctuation from OCR words`() = runBlocking {
        MockWebServer().use { server ->
            val repository = ReadingToolsRepository(OkHttpClient(), Json, server.url("/translate"), server.url("/dictionary/"))
            server.enqueue(MockResponse().setBody("""{"en":[{"definitions":[{"definition":"The earth."}]}]}"""))
            assertEquals("1. The earth.", repository.dictionary("world!", "en").text)
            assertEquals("/dictionary/world", server.takeRequest().path)
        }
    }
    @Test fun `a capitalized sentence word falls back to its lowercase dictionary entry only on 404`() = runBlocking {
        MockWebServer().use { server ->
            val repository = ReadingToolsRepository(OkHttpClient(), Json, server.url("/translate"), server.url("/dictionary/"))
            server.enqueue(MockResponse().setResponseCode(404))
            server.enqueue(MockResponse().setBody("""{"en":[{"definitions":[{"definition":"A greeting."}]}]}"""))
            assertEquals("1. A greeting.", repository.dictionary("Hello", "en").text)
            assertEquals("/dictionary/Hello", server.takeRequest().path)
            assertEquals("/dictionary/hello", server.takeRequest().path)
        }
    }
    @Test fun `translation joins actual sentence segments and sends only selected text and target`() = runBlocking {
        MockWebServer().use { server ->
            val repository = ReadingToolsRepository(OkHttpClient(), Json, server.url("/translate"), server.url("/dictionary/"))
            server.enqueue(MockResponse().setBody("""[[["Привет ","Hello "],["мир","world"]],null,"en"]"""))
            val result = repository.translate("Hello world", "ru")
            assertEquals("Привет мир", result.text)
            val request = server.takeRequest()
            assertEquals("Hello world", request.requestUrl!!.queryParameter("q"))
            assertEquals("ru", request.requestUrl!!.queryParameter("tl"))
            assertEquals("auto", request.requestUrl!!.queryParameter("sl"))
            assertNull(request.getHeader("Authorization"))
            assertNull(request.getHeader("Cookie"))
            assertFalse(request.path!!.contains("bookId"))
        }
    }

    @Test fun `dictionary respects selected language and bounds definitions`() = runBlocking {
        MockWebServer().use { server ->
            val repository = ReadingToolsRepository(OkHttpClient(), Json, server.url("/translate"), server.url("/dictionary/"))
            server.enqueue(MockResponse().setBody("""{"en":[{"definitions":[{"definition":"An English word."}]}],"ru":[{"definitions":[{"definition":"A Russian word."}]}]}"""))
            assertEquals("1. A Russian word.", repository.dictionary("слово", "ru").text)
            assertEquals("/dictionary/%D1%81%D0%BB%D0%BE%D0%B2%D0%BE", server.takeRequest().path)
        }
    }

    @Test fun `invalid input never triggers external lookup`() = runBlocking {
        MockWebServer().use { server ->
            val repository = ReadingToolsRepository(OkHttpClient(), Json, server.url("/translate"), server.url("/dictionary/"))
            assertTrue(runCatching { repository.translate("x".repeat(5001), "ru") }.exceptionOrNull() is IllegalArgumentException)
            assertTrue(runCatching { repository.dictionary("word", "../other") }.exceptionOrNull() is IllegalArgumentException)
            assertEquals(0, server.requestCount)
        }
    }

    @Test fun `missing dictionary language does not substitute an unrelated definition`() = runBlocking {
        MockWebServer().use { server ->
            val repository = ReadingToolsRepository(OkHttpClient(), Json, server.url("/translate"), server.url("/dictionary/"))
            server.enqueue(MockResponse().setBody("""{"en":[]}"""))
            assertTrue(runCatching { repository.dictionary("word", "ru") }.isFailure)
            assertEquals(1, server.requestCount)
        }
    }
}
