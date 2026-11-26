package com.bookorbit.feature.dictionary

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.text.Normalizer
import java.util.Locale

@Serializable
data class DictionaryPackage(val id: String, val version: String, val bytes: Long, val sha512: String, val headwords: Int) {
    val url: String get() {
        val encodedVersion = version.replace("+", "%2B")
        return "https://download.freedict.org/dictionaries/$id/$encodedVersion/freedict-$id-$encodedVersion.src.tar.xz"
    }
    fun validate() {
        require(validDictionaryId(id) && version.matches(Regex("[a-zA-Z0-9._+-]{1,40}")) && version != "." && version != "..")
        require(bytes in 1..MAX_DICTIONARY_DOWNLOAD && sha512.matches(Regex("[a-f0-9]{128}")) && headwords in 1..1_000_000)
    }
}

internal const val MAX_DICTIONARY_DOWNLOAD = 32L * 1024 * 1024
internal fun validDictionaryId(value: String): Boolean = value.matches(Regex("[a-z]{3}-[a-z]{3}"))
internal val BUNDLED_DICTIONARY_CATALOG = listOf(
    DictionaryPackage("eng-rus", "2025.11.23", 3948540,
        "fdee5ebdbe59c2a4293b875def3befdfddd990f95ecd10eb289eff22d815ed6d31267ca4695af66811ef368dd72b16342210475e044c995831e7931ce0e4bcfe", 62181),
    DictionaryPackage("rus-eng", "2025.11.23", 1183416,
        "9adf7a5eda27ccfa3e496b86b2fd575fc0e4af7ac5626a219d44e4ebfd56280deaf67cefd995ab04e9d7ef415e88f8cf0f9cae3252da6221376fd6d0c39012ba", 42600),
)

internal fun dictionaryCatalog(text: String, json: Json): List<DictionaryPackage> {
    val root = json.parseToJsonElement(text).jsonArray
    require(root.size <= 2000)
    return root.mapNotNull { element -> runCatching {
        val entry = element.jsonObject
        val id = entry["name"]?.jsonPrimitive?.content ?: return@runCatching null
        val release = entry["releases"]?.jsonArray?.firstOrNull { it.jsonObject["platform"]?.jsonPrimitive?.content == "src" }?.jsonObject ?: return@runCatching null
        val pack = DictionaryPackage(id, release.getValue("version").jsonPrimitive.content,
            release.getValue("size").jsonPrimitive.content.toLong(), release.getValue("checksum").jsonPrimitive.content,
            entry.getValue("headwords").jsonPrimitive.content.toInt())
        pack.validate()
        require(release.getValue("URL").jsonPrimitive.content.toHttpUrl().toString() == pack.url)
        pack
    }.getOrNull() }.sortedBy { it.id }.also { require(it.isNotEmpty() && it.map { pack -> pack.id }.distinct().size == it.size) }
}

internal fun dictionaryWord(value: String): String {
    val word = value.trim().trim { it in ",.!?;:()[]{}«»\"'“”" }.trim()
    require(word.isNotBlank() && word.length <= 1024)
    return Normalizer.normalize(word, Normalizer.Form.NFC).lowercase(Locale.ROOT)
}
