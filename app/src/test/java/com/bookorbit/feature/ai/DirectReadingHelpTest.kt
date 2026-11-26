package com.bookorbit.feature.ai

import com.bookorbit.core.model.ReadingHelpRequest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class DirectReadingHelpTest {
    private val request = ReadingHelpRequest("recap-note", 42, "ru", listOf("Only selected words, no future chapter."))

    @Test fun `both provider schemas contain only selected passage and correct provider authentication`() {
        val account = AiTestAccount()
        for (provider in AiProvider.entries) {
            val credentials = DirectAiCredentials(account.session.owner()!!, 0, provider, "fixture-model", "provider-key",
                baseUrl = if (provider == AiProvider.OPENAI_COMPATIBLE) "https://fixture.example/v1/" else defaultAiBaseUrl(provider),
                allowInsecure = provider == AiProvider.OLLAMA)
            val call = directHelpRequest(credentials, request)
            assertTrue(call.body!!.isOneShot())
            assertNull(call.header("Cookie"))
            assertFalse(call.url.toString().contains("provider-key"))
            if (provider != AiProvider.GOOGLE) assertEquals("Bearer provider-key", call.header("Authorization"))
            else { assertNull(call.header("Authorization")); assertEquals("provider-key", call.header("x-goog-api-key")) }
            val buffer = okio.Buffer(); call.body!!.writeTo(buffer)
            val text = buffer.readUtf8()
            assertTrue(text.contains("Only selected words"))
            assertFalse(text.contains("fileId")); assertFalse(text.contains("bookorbit-bearer")); assertFalse(text.contains("books.example"))
        }
    }

    @Test fun `truncated refused and blocked output is never reported complete`() {
        assertEquals("Answer", directHelpText(AiProvider.OPENAI, """{"choices":[{"finish_reason":"stop","message":{"content":"Answer"}}]}"""))
        assertEquals("Answer", directHelpText(AiProvider.GOOGLE, """{"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"Answer"}]}}]}"""))
        assertEquals("Answer", directHelpText(AiProvider.GOOGLE, """{"candidates":[{"finishReason":"STOP","content":{"parts":[{"thought":true,"text":"private reasoning"},{"text":"Answer"}]}}]}"""))
        for ((provider, body) in listOf(AiProvider.OPENAI to """{"choices":[{"finish_reason":"length","message":{"content":"partial"}}]}""",
            AiProvider.OPENAI to """{"choices":[{"finish_reason":"stop","message":{"content":"","refusal":"refused"}}]}""",
            AiProvider.GOOGLE to """{"candidates":[{"finishReason":"SAFETY"}]}""",
            AiProvider.OPENAI to """{"choices":[{"finish_reason":"stop","message":{"content":null}}]}""")) assertTrue(runCatching { directHelpText(provider, body) }.isFailure)
    }

    @Test fun `one confirmed call never redirects retries or falls back`() = runBlocking {
        val account = AiTestAccount(); val owner = account.session.owner()!!
        account.store.save(owner, AiRoute.DIRECT, AiProvider.OPENAI, "fixture-model", "", "provider-key")
        MockWebServer().use { server ->
            val client = OkHttpClient.Builder().followRedirects(false).retryOnConnectionFailure(false).addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().url(server.url("/chat")).build())
            }.build()
            val direct = DirectReadingHelp(account.store, account.session, client)
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/foreign")))
            assertTrue(runCatching { direct.answer(request).toList() }.isFailure)
            assertEquals(1, server.requestCount)
            val sent = server.takeRequest()
            assertEquals("Bearer provider-key", sent.getHeader("Authorization"))
            assertNull(sent.getHeader("Cookie"))
        }
    }

    @Test fun `503 retry-after zero cannot replay a billable body`() = runBlocking {
        val a = AiTestAccount(); a.store.save(a.session.owner()!!, AiRoute.DIRECT, AiProvider.OPENAI, "fixture-model", "", "provider-key")
        MockWebServer().use { server ->
            val client = OkHttpClient.Builder().followRedirects(false).retryOnConnectionFailure(false).addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().url(server.url("/chat")).build())
            }.build()
            server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "0"))
            server.enqueue(MockResponse().setBody("""{"choices":[{"finish_reason":"stop","message":{"content":"unexpected retry"}}]}"""))
            assertTrue(runCatching { DirectReadingHelp(a.store, a.session, client).answer(request).toList() }.isFailure)
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun `changing a connection cancels the in-flight HTTP call without publishing late output`() = runBlocking {
        val a = AiTestAccount(); val owner = a.session.owner()!!
        a.store.save(owner, AiRoute.DIRECT, AiProvider.OPENAI, "fixture-model", "", "provider-key")
        MockWebServer().use { server ->
            val active = java.util.concurrent.atomic.AtomicReference<okhttp3.Call>()
            server.start()
            val client = OkHttpClient.Builder().followRedirects(false).retryOnConnectionFailure(false).addInterceptor { chain ->
                active.set(chain.call())
                chain.proceed(chain.request().newBuilder().url(server.url("/chat")).build())
            }.build()
            server.enqueue(MockResponse().setBody("""{"choices":[{"finish_reason":"stop","message":{"content":"late"}}]}""")
                .setBodyDelay(1, TimeUnit.SECONDS))
            val answer = async(Dispatchers.Default) { runCatching { DirectReadingHelp(a.store, a.session, client).answer(request).toList() } }
            val sent = withContext(Dispatchers.IO) { server.takeRequest(5, TimeUnit.SECONDS) }
            assertNotNull(sent)
            a.store.save(owner, AiRoute.SERVER, AiProvider.OPENAI, "fixture-model", "")
            assertTrue(withTimeout(3000) { answer.await() }.isFailure)
            assertTrue(active.get().isCanceled())
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun `both provider response formats complete through the actual HTTP adapter`() = runBlocking {
        val a = AiTestAccount(); val owner = a.session.owner()!!
        MockWebServer().use { server ->
            val client = OkHttpClient.Builder().followRedirects(false).retryOnConnectionFailure(false).addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().url(server.url("/fixture")).build())
            }.build()
            for (provider in AiProvider.entries) {
                a.store.save(owner, AiRoute.DIRECT, provider, "fixture-model", "", "provider-key",
                    baseUrl = if (provider == AiProvider.OPENAI_COMPATIBLE) "https://fixture.example/v1/" else defaultAiBaseUrl(provider),
                    allowInsecure = provider == AiProvider.OLLAMA)
                val body = when (provider) {
                    AiProvider.GOOGLE -> """{"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"Answer"}]}}]}"""
                    AiProvider.OLLAMA -> """{"done":true,"done_reason":"stop","message":{"content":"Answer"}}"""
                    else -> """{"choices":[{"finish_reason":"stop","message":{"content":"Answer"}}]}"""
                }
                server.enqueue(MockResponse().setBody(body))
                val event = withTimeout(3000) { DirectReadingHelp(a.store, a.session, client).answer(request.copy(careful = true)).toList().single() }
                assertEquals("done", event.type); assertEquals("Answer", event.text); assertTrue(event.careful)
            }
            assertEquals(AiProvider.entries.size, server.requestCount)
        }
    }

    @Test fun `logout cancels the provider call even when connection settings are unchanged`() = runBlocking {
        val a = AiTestAccount(); val owner = a.session.owner()!!
        a.store.save(owner, AiRoute.DIRECT, AiProvider.OPENAI, "fixture-model", "", "provider-key")
        MockWebServer().use { server ->
            server.start()
            val active = java.util.concurrent.atomic.AtomicReference<okhttp3.Call>()
            val client = OkHttpClient.Builder().followRedirects(false).retryOnConnectionFailure(false).addInterceptor { chain ->
                active.set(chain.call()); chain.proceed(chain.request().newBuilder().url(server.url("/chat")).build())
            }.build()
            server.enqueue(MockResponse().setBody("""{"choices":[{"finish_reason":"stop","message":{"content":"late"}}]}""").setBodyDelay(1, TimeUnit.SECONDS))
            val answer = async(Dispatchers.Default) { runCatching { DirectReadingHelp(a.store, a.session, client).answer(request, owner).toList() } }
            assertNotNull(withContext(Dispatchers.IO) { server.takeRequest(5, TimeUnit.SECONDS) })
            a.session.signOut()
            assertTrue(withTimeout(3000) { answer.await() }.isFailure)
            assertTrue(active.get().isCanceled())
            assertEquals(1, server.requestCount)
        }
    }
}
