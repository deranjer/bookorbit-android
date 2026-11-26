package com.bookorbit.feature.reader

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ReadingToolResult(val text: String, val source: String, val sourceUrl: String)
private class LookupHttpException(val status: Int) : IOException("Lookup HTTP $status")

/** Public lookup transport deliberately has no BookOrbit authentication, cookies or redirects. */
@Singleton
class ReadingToolsRepository internal constructor(private val client: OkHttpClient, private val json: Json,
    private val translateBase: HttpUrl, private val dictionaryBase: HttpUrl) {
    @Inject constructor() : this(OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build(), Json { ignoreUnknownKeys = true },
        "https://translate.googleapis.com/translate_a/single".toHttpUrl(),
        "https://en.wiktionary.org/api/rest_v1/page/definition/".toHttpUrl())

    suspend fun translate(text: String, language: String): ReadingToolResult {
        require(text.isNotBlank() && text.length <= 5000 && language in LANGUAGES)
        val url = translateBase.newBuilder().addQueryParameter("client", "gtx").addQueryParameter("dt", "t")
            .addQueryParameter("sl", "auto").addQueryParameter("tl", language).addQueryParameter("q", text).build()
        val root = json.parseToJsonElement(get(url)) as? JsonArray ?: throw IOException("Invalid translation")
        val sentences = root.firstOrNull() as? JsonArray ?: throw IOException("Missing translation")
        val result = sentences.joinToString("") { (it as? JsonArray)?.firstOrNull()?.jsonPrimitive?.contentOrNull.orEmpty() }
        if (result.isBlank()) throw IOException("Empty translation")
        return ReadingToolResult(result, "Google Translate", "https://translate.google.com/")
    }

    suspend fun dictionary(word: String, language: String): ReadingToolResult {
        require(word.isNotBlank() && word.length <= 120 && language in LANGUAGES)
        var lookup = word.trim().trim { it in ",.!?;:()[]{}«»\"'“”" }
        require(lookup.isNotBlank())
        fun url() = dictionaryBase.newBuilder().addPathSegment(lookup).build()
        val response = try { get(url()) } catch (e: LookupHttpException) {
            val lower = lookup.lowercase()
            if (e.status != 404 || lower == lookup) throw e
            lookup = lower
            get(url())
        }
        val root = json.parseToJsonElement(response).jsonObject
        val entries = root[language] as? JsonArray ?: throw IOException("Word not found")
        val definitions = entries.take(10).flatMap { entry ->
            (entry.jsonObject["definitions"] as? JsonArray).orEmpty().take(5).mapNotNull { definition ->
                definition.jsonObject["definition"]?.jsonPrimitive?.contentOrNull
            }
        }
        if (definitions.isEmpty()) throw IOException("Word not found")
        return ReadingToolResult(definitions.mapIndexed { i, value -> "${i + 1}. $value" }.joinToString("\n\n"),
            "Wiktionary · CC BY-SA", "https://en.wiktionary.org/wiki/".toHttpUrl().newBuilder().addPathSegment(lookup).build().toString())
    }

    private suspend fun get(url: HttpUrl): String = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(Request.Builder().url(url).header("User-Agent", "BookOrbitClient/0.9 (reading tools)").build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(IOException("Lookup failed")) }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val result = response.use {
                        if (!it.isSuccessful) throw LookupHttpException(it.code)
                        val source = it.body?.source() ?: throw IOException("Empty lookup")
                        source.request(524_289)
                        if (source.buffer.size > 524_288) throw IOException("Lookup too large")
                        source.readUtf8()
                    }
                    if (continuation.isActive) continuation.resume(result)
                } catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(if (e is LookupHttpException) e else IOException("Lookup failed")) }
            }
        })
    }

    companion object {
        val LANGUAGES = linkedMapOf("ru" to "Русский", "en" to "English", "de" to "Deutsch", "fr" to "Français",
            "es" to "Español", "it" to "Italiano", "pt" to "Português", "uk" to "Українська", "ja" to "日本語", "ko" to "한국어", "zh" to "中文")
    }
}
