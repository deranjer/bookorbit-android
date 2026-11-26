package com.bookorbit.feature.ai

import com.bookorbit.core.model.ReadingHelpRequest
import org.junit.Assert.*
import org.junit.Test
import okio.Buffer
import kotlinx.serialization.json.*

class AiEndpointTest {
    @Test fun `existing OpenAI settings without endpoint fields keep their token and official address`() {
        val a = AiTestAccount(); val owner = a.session.owner()!!
        a.store.save(owner, AiRoute.DIRECT, AiProvider.OPENAI, "existing-model", "", "existing-key")
        val key = a.values.keys.single { it.startsWith("ai_connection_") }
        val old = Json.parseToJsonElement(a.values.getValue(key)!!).jsonObject
        a.values[key] = JsonObject(old.filterKeys { it != "endpoints" && it != "insecure" }).toString()
        assertTrue(a.store.connection(owner).hasToken)
        assertEquals("existing-key", a.store.credentials(owner).token)
        assertEquals(defaultAiBaseUrl(AiProvider.OPENAI), a.store.credentials(owner).baseUrl)
    }
    private val request = ReadingHelpRequest("recap-note", 5, "ru", listOf("Selected passage"))

    @Test fun `custom endpoint preserves prefix and optional token is not fabricated`() {
        val a = AiTestAccount()
        for ((provider, base, path) in listOf(
            Triple(AiProvider.OPENAI_COMPATIBLE, "https://llm.example/prefix/v1", "/prefix/v1/chat/completions"),
            Triple(AiProvider.OLLAMA, "http://192.168.1.40:11434/proxy", "/proxy/api/chat"))) {
            a.store.save(a.session.owner()!!, AiRoute.DIRECT, provider, "namespace/model:tag", "", baseUrl = base, allowInsecure = provider == AiProvider.OLLAMA)
            val call = directHelpRequest(a.store.credentials(a.session.owner()!!), request)
            assertEquals(path, call.url.encodedPath)
            assertNull(call.header("Authorization")); assertNull(call.header("Cookie"))
            val buffer = Buffer(); call.body!!.writeTo(buffer)
            val body = Json.parseToJsonElement(buffer.readUtf8()).jsonObject
            assertEquals("namespace/model:tag", body.getValue("model").jsonPrimitive.content)
            assertFalse(body.getValue("stream").jsonPrimitive.boolean)
            if (provider == AiProvider.OLLAMA) assertEquals(2048, body.getValue("options").jsonObject.getValue("num_predict").jsonPrimitive.int)
            else assertEquals(2048, body.getValue("max_tokens").jsonPrimitive.int)
        }
    }

    @Test fun `HTTP requires explicit opt in and credentials in URL are forbidden`() {
        for (url in listOf("http://lan:11434", "https://user:secret@host/v1", "https://host/v1?key=secret", "https://host/v1#secret", " https://host/v1", "file:///private", "https://host/\nsecret"))
            assertTrue(runCatching { validatedAiBaseUrl(AiProvider.OPENAI_COMPATIBLE, url, false) }.isFailure)
        assertEquals("http://lan:11434/", validatedAiBaseUrl(AiProvider.OLLAMA, "http://lan:11434", true).toString())
        assertTrue(runCatching { validatedAiBaseUrl(AiProvider.OPENROUTER, "https://foreign.example/v1/", true) }.isFailure)
    }

    @Test fun `changing endpoints drops the old token and invalidates previous consent`() {
        val a = AiTestAccount(); val owner = a.session.owner()!!
        a.store.save(owner, AiRoute.DIRECT, AiProvider.OPENAI_COMPATIBLE, "model", "", "secret", "https://first.example/v1/")
        val old = a.store.credentials(owner)
        a.store.save(owner, AiRoute.DIRECT, AiProvider.OPENAI_COMPATIBLE, "model", "", baseUrl = "https://second.example/v1/")
        assertFalse(a.store.current(old))
        assertFalse(a.store.connection(owner).hasToken)
        assertEquals("", a.store.credentials(owner).token)
        a.store.useServer(owner)
        assertEquals(AiRoute.SERVER, a.store.connection(owner).route)
        assertEquals("https://second.example/v1/", a.store.connection(owner).baseUrl)
    }

    @Test fun `OpenRouter model namespaces and routing restrictions are preserved`() {
        val a = AiTestAccount(); val owner = a.session.owner()!!
        a.store.save(owner, AiRoute.DIRECT, AiProvider.OPENROUTER, "org/model:free", "", "router-token")
        val call = directHelpRequest(a.store.credentials(owner), request)
        assertEquals("https://openrouter.ai/api/v1/chat/completions", call.url.toString())
        assertEquals("Bearer router-token", call.header("Authorization"))
        val buffer = Buffer(); call.body!!.writeTo(buffer)
        val body = Json.parseToJsonElement(buffer.readUtf8()).jsonObject
        assertEquals("org/model:free", body.getValue("model").jsonPrimitive.content)
        assertFalse(body.getValue("provider").jsonObject.getValue("allow_fallbacks").jsonPrimitive.boolean)
    }

    @Test fun `Ollama completion requires stopped done response and never exposes thinking`() {
        assertEquals("Answer", directHelpText(AiProvider.OLLAMA, """{"done":true,"done_reason":"stop","message":{"content":"Answer","thinking":"hidden"}}"""))
        for (json in listOf("""{"done":false,"message":{"content":"partial"}}""", """{"done":true,"done_reason":"length","message":{"content":"partial"}}""",
            """{"error":"failed"}""", """{"done":true,"done_reason":"stop","message":{"content":"text","tool_calls":[{}]}}"""))
            assertTrue(runCatching { directHelpText(AiProvider.OLLAMA, json) }.isFailure)
    }
}
