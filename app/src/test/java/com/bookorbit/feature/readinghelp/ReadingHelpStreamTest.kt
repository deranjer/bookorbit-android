package com.bookorbit.feature.readinghelp

import com.bookorbit.core.model.ReadingHelpEvent
import com.bookorbit.core.model.ReadingHelpRequest
import kotlinx.serialization.json.Json
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class ReadingHelpStreamTest {
    private val json = Json { ignoreUnknownKeys = true }
    private fun decode(text: String): List<ReadingHelpEvent> = mutableListOf<ReadingHelpEvent>().also { out ->
        ReadingHelpStream.read(Buffer().writeUtf8(text), json, out::add)
    }

    @Test fun `handles CRLF keepalives unicode unknown events and authoritative done text`() {
        val events = decode(": keep-alive\r\n\r\ndata: {\"type\":\"future\"}\r\n\r\ndata: {\"type\":\"delta\",\"text\":\"Привет\"}\r\n\r\ndata: {\"type\":\"done\",\"text\":\"Привет!\",\"cached\":true,\"careful\":false}\r\n\r\n")
        assertEquals(listOf("delta", "done"), events.map { it.type })
        assertEquals("Привет!", events.last().text)
        assertTrue(events.last().cached)
    }

    @Test fun `multiline data forms one JSON event`() {
        assertEquals("done", decode("data: {\"type\":\"done\",\ndata: \"text\":\"ok\"}\n\n").single().type)
    }

    @Test fun `EOF without done and malformed data are failures not successful partial answers`() {
        for (input in listOf("", "data: {\"type\":\"delta\",\"text\":\"partial\"}\n\n", "data: invalid\n\n", "data: {\"type\":\"done\"}\n\n")) {
            assertTrue(runCatching { decode(input) }.isFailure)
        }
    }

    @Test fun `error event terminates without reading subsequent output`() {
        val events = decode("data: {\"type\":\"error\",\"errorCode\":\"READING_HELP_BUDGET\"}\n\nthis must not be parsed")
        assertEquals("READING_HELP_BUDGET", events.single().errorCode)
    }

    @Test fun `oversized lines events and accumulated answers are rejected`() {
        assertTrue(runCatching { decode("data: " + "x".repeat(65_537) + "\n\n") }.isFailure)
        val delta = "data: {\"type\":\"delta\",\"text\":\"${"x".repeat(32_768)}\"}\n\n"
        assertTrue(runCatching { decode(delta.repeat(5)) }.isFailure)
    }

    @Test fun `request validates exact file and supported bounded passages`() {
        val valid = ReadingHelpRequest("recap-note", 7, "ru-RU", listOf("Selected text"))
        valid.validate()
        for (request in listOf(valid.copy(fileId = 0), valid.copy(passages = emptyList()),
            valid.copy(passages = listOf("x".repeat(12_001))), valid.copy(language = "invalid_code"),
            valid.copy(kind = "arbitrary-prompt"), valid.copy(kind = "term", term = ""),
            valid.copy(passages = listOf("one", "two")))) assertTrue(runCatching { request.validate() }.isFailure)
    }
}
