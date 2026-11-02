package com.bookorbit.feature.reader

import com.bookorbit.core.network.PublicClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class TranslationResult(val translatedText: String, val detectedSourceLang: String, val provider: String)

/**
 * Translates a selection the way the web reader does (client/src/features/reader/epub/services):
 * Google's free endpoint first, then Microsoft's Edge endpoint when Google fails. Neither needs a key.
 */
@Singleton
class ReaderTranslator @Inject constructor(
    @PublicClient private val client: OkHttpClient,
    private val json: Json,
) {
    private var azureToken: String? = null
    private var azureTokenExpiresAt = 0L

    /** Throws when neither provider could translate [text]. */
    suspend fun translate(text: String, targetLang: String): TranslationResult = withContext(Dispatchers.IO) {
        runCatching { google(text, targetLang) }.getOrElse { azure(text, targetLang) }
    }

    private fun google(text: String, targetLang: String): TranslationResult {
        val url = "https://translate.googleapis.com/translate_a/single".toHttpUrl().newBuilder()
            .addQueryParameter("client", "gtx")
            .addQueryParameter("dt", "t")
            .addQueryParameter("sl", "auto")
            .addQueryParameter("tl", targetLang)
            .addQueryParameter("q", text)
            .build()
        return TranslationParsing.parseGoogle(json.parseToJsonElement(call(Request.Builder().url(url).build())))
    }

    private fun azure(text: String, targetLang: String): TranslationResult {
        val url = "https://api-edge.cognitive.microsofttranslator.com/translate".toHttpUrl().newBuilder()
            .addQueryParameter("to", targetLang)
            .addQueryParameter("api-version", "3.0")
            .build()
        val body = buildJsonArray { add(buildJsonObject { put("Text", text) }) }.toString()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${azureToken()}")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        val response = try {
            call(request)
        } catch (e: IOException) {
            if (e.message == "HTTP 401") synchronized(this) { azureToken = null }
            throw e
        }
        return TranslationParsing.parseAzure(json.parseToJsonElement(response))
    }

    private fun azureToken(): String = synchronized(this) {
        azureToken?.takeIf { System.currentTimeMillis() < azureTokenExpiresAt }?.let { return it }
        val token = call(Request.Builder().url("https://edge.microsoft.com/translate/auth").build()).trim()
        if (token.isEmpty()) throw IOException("Empty auth token")
        azureToken = token
        azureTokenExpiresAt = System.currentTimeMillis() + AZURE_TOKEN_TTL_MS
        token
    }

    private fun call(request: Request): String = client.newCall(request).execute().use { res ->
        if (!res.isSuccessful) throw IOException("HTTP ${res.code}")
        res.body.string()
    }

    companion object {
        private const val AZURE_TOKEN_TTL_MS = 7 * 60 * 1000L

        /** Longer selections are refused, as on the web. */
        const val CHAR_LIMIT = 500

        /** The web reader's target languages. */
        val LANGUAGES = listOf("en", "es", "fr", "de", "zh", "ja", "ko", "ar", "hi", "pt")

        /** The phone's language when it is one of [LANGUAGES], else English. */
        fun defaultTarget(): String = Locale.getDefault().language.takeIf { it in LANGUAGES } ?: "en"

        /** A language's name in the phone's own language, e.g. "Spanish" or "Español". */
        fun displayName(code: String): String =
            Locale.forLanguageTag(code).let { it.getDisplayLanguage(Locale.getDefault()).replaceFirstChar { c -> c.titlecase(Locale.getDefault()) } }
    }
}

internal object TranslationParsing {
    /** Google answers [[["translated","original",...],...],null,"detected-lang",...]. */
    fun parseGoogle(data: JsonElement): TranslationResult {
        val root = data as? JsonArray ?: throw IOException("Unexpected response shape")
        val sentences = root.firstOrNull() as? JsonArray ?: throw IOException("Unexpected sentences shape")
        val text = sentences.joinToString("") { s -> ((s as? JsonArray)?.firstOrNull() as? JsonPrimitive)?.contentOrNull.orEmpty() }
        if (text.isEmpty()) throw IOException("Empty translation")
        val detected = (root.getOrNull(2) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull ?: "auto"
        return TranslationResult(text, detected, "Google")
    }

    /** Microsoft answers [{"detectedLanguage":{"language":"fr"},"translations":[{"text":"..."}]}]. */
    fun parseAzure(data: JsonElement): TranslationResult {
        val first = (data as? JsonArray)?.firstOrNull() as? JsonObject ?: throw IOException("Unexpected response shape")
        val translation = (first["translations"] as? JsonArray)?.firstOrNull() as? JsonObject
        val text = (translation?.get("text") as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() }
            ?: throw IOException("Empty translation")
        val detected = ((first["detectedLanguage"] as? JsonObject)?.get("language") as? JsonPrimitive)?.contentOrNull ?: "auto"
        return TranslationResult(text, detected, "Microsoft")
    }
}
