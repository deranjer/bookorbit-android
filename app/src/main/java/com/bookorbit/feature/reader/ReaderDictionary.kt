package com.bookorbit.feature.reader

import com.bookorbit.BuildConfig
import com.bookorbit.core.network.PublicClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

data class DictionaryDefinition(val definition: String, val example: String?)

/** One part of speech's senses; [sourceWord] differs from the looked-up word when it came from a lemma. */
data class DictionaryEntry(val partOfSpeech: String, val definitions: List<DictionaryDefinition>, val sourceWord: String)

enum class DictionaryProvider(val label: String) { FREE_DICTIONARY("Free Dictionary"), WIKTIONARY("Wiktionary") }

data class DictionaryResult(
    val word: String,
    val phonetic: String?,
    val audioUrl: String?,
    val entries: List<DictionaryEntry>,
    val provider: DictionaryProvider,
)

internal data class SenseBlock(val partOfSpeech: String, val definitions: List<DictionaryDefinition>)

internal data class DictionaryPayload(val word: String, val phonetic: String?, val audioUrl: String?, val blocks: List<SenseBlock>)

/**
 * Looks a word up the way the web reader does (client/src/features/reader/epub/composables/useDictionary.ts):
 * English goes to the Free Dictionary API and falls back to Wiktionary; other languages go to
 * Wiktionary's section for the book's language. A sense that only points at another word ("plural
 * of candelabra") is followed to that word so the reader gets a real definition.
 */
@Singleton
class ReaderDictionary @Inject constructor(
    @PublicClient private val client: OkHttpClient,
    private val json: Json,
) {
    /** Null when no provider knows the word; throws when they could not be reached. */
    suspend fun lookup(word: String, bookLanguage: String?): DictionaryResult? = withContext(Dispatchers.IO) {
        val lang = DictionaryParsing.normalizeLang(bookLanguage)
        val trimmed = DictionaryParsing.cleanWord(word)
        if (trimmed.isEmpty()) return@withContext null

        if (lang == "en") {
            val payload = runCatching { freeDictionary(trimmed) }.getOrNull()
            if (payload != null) {
                val entries = DictionaryParsing.expandPointers(payload) { freeDictionary(it)?.blocks.orEmpty() }
                return@withContext DictionaryResult(payload.word, payload.phonetic, payload.audioUrl, entries, DictionaryProvider.FREE_DICTIONARY)
            }
        }
        val payload = wiktionary(trimmed, lang) ?: return@withContext null
        val entries = DictionaryParsing.expandPointers(payload) { wiktionary(it, lang)?.blocks.orEmpty() }
        DictionaryResult(payload.word, payload.phonetic, payload.audioUrl, entries, DictionaryProvider.WIKTIONARY)
    }

    private fun freeDictionary(word: String): DictionaryPayload? =
        get("https://api.dictionaryapi.dev/api/v2/entries/en/${pathSegment(word)}")
            ?.let { DictionaryParsing.parseFreeDictionary(json.parseToJsonElement(it), word) }

    // Only English Wiktionary serves this endpoint; its response carries every language's section.
    private fun wiktionary(word: String, lang: String): DictionaryPayload? =
        get("https://en.wiktionary.org/api/rest_v1/page/definition/${pathSegment(word)}")
            ?.let { DictionaryParsing.parseWiktionary(json.parseToJsonElement(it), word, lang) }

    /** The body, or null on a 404 (the word isn't there); any other failure throws. */
    private fun get(url: String): String? {
        val request = Request.Builder()
            .url(url.toHttpUrl())
            // Wikimedia asks API clients to say who they are.
            .header("User-Agent", "BookOrbit-Android/${BuildConfig.VERSION_NAME} (https://github.com/deranjer/bookorbit-android)")
            .build()
        client.newCall(request).execute().use { res ->
            if (res.code == 404) return null
            if (!res.isSuccessful) throw IOException("HTTP ${res.code}")
            return res.body.string()
        }
    }

    private fun pathSegment(word: String) = URLEncoder.encode(word, "UTF-8").replace("+", "%20")
}

internal object DictionaryParsing {
    private const val MAX_DEFINITIONS_PER_ENTRY = 5
    private const val MAX_LEMMA_DEPTH = 2
    private const val MAX_LEMMA_LOOKUPS = 3

    private val LANGUAGE_ALIASES: Map<String, String> = buildMap {
        fun alias(code: String, vararg names: String) = names.forEach { put(it, code) }
        alias("ar", "ar", "ara", "arabic")
        alias("cs", "cs", "ces", "cze", "czech")
        alias("da", "da", "dan", "danish")
        alias("de", "de", "deu", "ger", "german")
        alias("el", "el", "ell", "gre", "greek")
        alias("en", "en", "eng", "english")
        alias("es", "es", "spa", "spanish")
        alias("fi", "fi", "fin", "finnish")
        alias("fr", "fr", "fra", "fre", "french")
        alias("he", "he", "heb", "hebrew")
        alias("hi", "hi", "hin", "hindi")
        alias("hu", "hu", "hun", "hungarian")
        alias("id", "id", "ind", "indonesian")
        alias("it", "it", "ita", "italian")
        alias("ja", "ja", "jpn", "japanese")
        alias("ko", "ko", "kor", "korean")
        alias("nl", "nl", "nld", "dut", "dutch")
        alias("no", "no", "nor", "norwegian")
        alias("pl", "pl", "pol", "polish")
        alias("pt", "pt", "por", "portuguese")
        alias("ro", "ro", "ron", "rum", "romanian")
        alias("ru", "ru", "rus", "russian")
        alias("sv", "sv", "swe", "swedish")
        alias("th", "th", "tha", "thai")
        alias("tr", "tr", "tur", "turkish")
        alias("uk", "uk", "ukr", "ukrainian")
        alias("vi", "vi", "vie", "vietnamese")
        alias("zh", "zh", "zho", "chi", "chinese")
    }

    /**
     * Words allowed before "of" in an inflection gloss such as "simple past and past participle of
     * walk". Requiring every leading word to be grammatical keeps ordinary definitions ("Informal form
     * of address used by...") from being mistaken for pointers. Beyond the web's list, the regional
     * spelling words are here too, so "UK standard spelling of endeavored" (common in older English
     * books) leads to the word's definition.
     */
    private val GRAMMAR_TERMS = setOf(
        "a", "an", "the", "and", "or", "ablative", "abbreviation", "accusative", "acronym", "active",
        "adjective", "adverb", "alternate", "alternative", "aorist", "archaic", "attributive", "augmentative",
        "capitalized", "clipping", "common", "comparative", "conditional", "conjugation", "construct",
        "contraction", "dated", "dative", "declension", "definite", "diminutive", "dual", "elative", "essive",
        "feminine", "first-person", "form", "forms", "future", "genitive", "gerund", "illative", "imperative",
        "imperfect", "imperfective", "indefinite", "indicative", "infinitive", "inflected", "inflection",
        "inflections", "initialism", "instrumental", "letter-case", "locative", "lowercase", "masculine",
        "misspelling", "neuter", "nominative", "nonstandard", "noun", "obsolete", "participle", "particle",
        "partitive", "passive", "past", "perfect", "perfective", "pluperfect", "plural", "positive",
        "prepositional", "present", "preterite", "second-person", "simple", "singular", "spelling",
        "substantive", "superlative", "supine", "tense", "third-person", "uppercase", "verb", "verbal", "vocative",
        "american", "australian", "british", "canadian", "commonwealth", "standard", "uk", "us",
    )

    private val FILLER_TERMS = setOf("a", "an", "the", "and", "or")

    private val POINTER_PATTERN =
        Regex("""^(?:\([^)]*\)\s*)?([\p{L}\p{M}/ -]+?)\s+of\s+([\p{L}\p{M}][\p{L}\p{M}'’-]*)\s*[.:;,]?$""")

    /** A selection's word without the punctuation a long-press often takes with it. */
    fun cleanWord(word: String): String = word.trim().trim { !it.isLetterOrDigit() && it != '\'' && it != '’' && it != '-' }.trim('\'', '’', '-')

    /** The book's language as a dictionary section code; English when unknown. */
    fun normalizeLang(lang: String?): String {
        val normalized = lang?.trim()?.lowercase().orEmpty()
        if (normalized.isEmpty()) return "en"
        val primary = normalized.split(Regex("[;,/|]")).first().trim()
        if (primary.isEmpty()) return "en"
        val base = primary.split('-').first().trim()
        LANGUAGE_ALIASES[base]?.let { return it }
        return if (Regex("^[a-z]{2,3}$").matches(base)) base else "en"
    }

    /**
     * The lemma a definition merely points at, or null when it stands on its own. "plural of
     * candelabra" tells the reader nothing, so the lookup follows it; "A candle holder." does not.
     */
    fun pointerLemma(definition: String): String? {
        val match = POINTER_PATTERN.find(definition.trim()) ?: return null
        val modifiers = match.groupValues[1].lowercase().split(Regex("[\\s/]+")).filter { it.isNotEmpty() }
        if (modifiers.isEmpty()) return null
        if (!modifiers.all { it in GRAMMAR_TERMS }) return null
        if (modifiers.all { it in FILLER_TERMS }) return null
        return match.groupValues[2].lowercase()
    }

    /** Text of an HTML fragment. Script and style contents go too: Wiktionary ships inline <style> with some senses. */
    fun stripHtml(html: String): String = html
        .replace(Regex("<(script|style)\\b[^>]*>.*?</\\1\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
        .replace(Regex("<[^>]*>"), "")
        .let(::decodeEntities)
        .replace(Regex("\\s+"), " ")
        .trim()

    private val NAMED_ENTITIES = mapOf("amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ")

    private fun decodeEntities(text: String): String = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);").replace(text) { m ->
        val body = m.groupValues[1]
        when {
            body.startsWith("#x") -> body.drop(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
            body.startsWith("#") -> body.drop(1).toIntOrNull()?.let { String(Character.toChars(it)) }
            else -> NAMED_ENTITIES[body]
        } ?: m.value
    }

    /**
     * Collapses blocks that share a part of speech. Both providers split a word across several
     * same-part-of-speech blocks (one per etymology), which would otherwise repeat "NOUN" headings.
     */
    fun mergeBlocks(blocks: List<SenseBlock>): List<SenseBlock> {
        val merged = LinkedHashMap<String, MutableList<DictionaryDefinition>>()
        for (block in blocks) {
            if (block.definitions.isEmpty()) continue
            merged.getOrPut(block.partOfSpeech) { mutableListOf() } += block.definitions
        }
        return merged.map { (pos, defs) -> SenseBlock(pos, defs) }
    }

    fun parseFreeDictionary(data: JsonElement, fallbackWord: String): DictionaryPayload? {
        val entry = (data as? JsonArray)?.firstOrNull() as? JsonObject ?: return null
        var phonetic = entry.str("phonetic")
        var audioUrl: String? = null
        for (p in entry.arr("phonetics")) {
            val o = p as? JsonObject ?: continue
            val audio = o.str("audio")?.takeIf { it.isNotEmpty() }
            val text = o.str("text")?.takeIf { it.isNotEmpty() }
            if (audio != null) {
                audioUrl = audio
                if (text != null) phonetic = text
                break
            }
            if (phonetic == null && text != null) phonetic = text
        }
        val blocks = mergeBlocks(
            entry.arr("meanings").mapNotNull { m ->
                val o = m as? JsonObject ?: return@mapNotNull null
                SenseBlock(
                    partOfSpeech = o.str("partOfSpeech").orEmpty(),
                    definitions = o.arr("definitions").mapNotNull { d ->
                        val def = d as? JsonObject ?: return@mapNotNull null
                        DictionaryDefinition(def.str("definition").orEmpty(), def.str("example"))
                    },
                )
            },
        )
        if (blocks.isEmpty()) return null
        return DictionaryPayload(entry.str("word") ?: fallbackWord, phonetic, audioUrl, blocks)
    }

    /**
     * Wiktionary keys its response by language and one page carries every language that spells the
     * word the same way, so only the book's language section is read.
     */
    fun parseWiktionary(data: JsonElement, word: String, lang: String): DictionaryPayload? {
        val section = (data as? JsonObject)?.get(lang) as? JsonArray ?: return null
        val blocks = section.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val definitions = o.arr("definitions").mapNotNull { d ->
                val def = d as? JsonObject ?: return@mapNotNull null
                val text = def.str("definition")?.let(::stripHtml)?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val example = def.arr("examples").firstNotNullOfOrNull { ex ->
                    when (ex) {
                        is JsonPrimitive -> ex.contentOrNull
                        is JsonObject -> ex.str("text")
                        else -> null
                    }
                }?.let(::stripHtml)
                DictionaryDefinition(text, example)
            }
            if (definitions.isEmpty()) null else SenseBlock(o.str("partOfSpeech").orEmpty(), definitions)
        }
        val merged = mergeBlocks(blocks)
        if (merged.isEmpty()) return null
        return DictionaryPayload(word, null, null, merged)
    }

    /**
     * Walks inflection pointers breadth-first so a lookup that lands on a pure inflection
     * ("candelabras" -> "candelabra" -> "candelabrum") still reaches a real definition. Only a pointer
     * in the leading sense of its block is followed.
     */
    suspend fun expandPointers(root: DictionaryPayload, fetchBlocks: suspend (String) -> List<SenseBlock>): List<DictionaryEntry> {
        val entries = mutableListOf<DictionaryEntry>()
        val visited = mutableSetOf(root.word.lowercase())
        val queue = ArrayDeque(listOf(Triple(root.word, root.blocks, 0)))
        var lookups = 0
        while (queue.isNotEmpty()) {
            val (word, blocks, depth) = queue.removeFirst()
            blocks.forEach { entries += DictionaryEntry(it.partOfSpeech, it.definitions.take(MAX_DEFINITIONS_PER_ENTRY), word) }
            if (depth >= MAX_LEMMA_DEPTH) continue
            for (block in blocks) {
                if (lookups >= MAX_LEMMA_LOOKUPS) break
                val lemma = block.definitions.firstOrNull()?.let { pointerLemma(it.definition) } ?: continue
                if (!visited.add(lemma)) continue
                lookups++
                // A lemma that fails to resolve must never take the primary result down.
                val found = runCatching { mergeBlocks(fetchBlocks(lemma)) }.getOrDefault(emptyList())
                if (found.isNotEmpty()) queue.addLast(Triple(lemma, found, depth + 1))
            }
        }
        return entries
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
    private fun JsonObject.arr(key: String): List<JsonElement> = (this[key] as? JsonArray).orEmpty()
}
