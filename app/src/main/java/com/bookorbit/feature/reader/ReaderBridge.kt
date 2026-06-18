package com.bookorbit.feature.reader

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Message protocol between the host and the in-WebView foliate bridge (assets/reader/bridge.js).
 *
 * Host -> WebView: JS strings run via WebView.evaluateJavascript invoking globals the bridge
 * installs (__readerBegin/__readerChunk/__readerCommit/__readerCommand). Arguments are double-encoded
 * (a JSON string passed as a JS string literal).
 * WebView -> host: JSON strings posted through window.ReactNativeWebView.postMessage (shimmed onto the
 * AndroidReaderBridge JS interface in index.html).
 */
@Serializable
data class TocItem(
    val label: String = "",
    val href: String? = null,
    val subitems: List<TocItem> = emptyList(),
)

sealed interface ReaderEvent {
    data object Ready : ReaderEvent
    data class Loaded(val toc: List<TocItem>, val title: String?) : ReaderEvent
    data class Relocate(
        val cfi: String?,
        val fraction: Double?,
        val chapterTitle: String?,
    ) : ReaderEvent
    data class Error(val message: String) : ReaderEvent
}

object ReaderBridge {
    // Send all fields (incl. nulls/defaults) so the bridge can build the full stylesheet.
    val json = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = true
    }

    private fun asJsLiteral(jsonString: String): String =
        json.encodeToString(String.serializer(), jsonString)

    fun jsBegin(metaJson: String): String =
        "window.__readerBegin && window.__readerBegin(${asJsLiteral(metaJson)});true;"

    fun jsChunk(base64: String): String =
        "window.__readerChunk && window.__readerChunk(\"$base64\");true;"

    fun jsCommit(): String = "window.__readerCommit && window.__readerCommit();true;"

    fun jsCommand(commandJson: String): String =
        "window.__readerCommand && window.__readerCommand(${asJsLiteral(commandJson)});true;"

    fun parseEvent(data: String): ReaderEvent? = runCatching {
        val obj = json.parseToJsonElement(data).jsonObject
        when (obj["type"]?.jsonPrimitive?.contentOrNull) {
            "ready" -> ReaderEvent.Ready
            "loaded" -> {
                val toc = obj["toc"]?.let {
                    runCatching { json.decodeFromJsonElement(kotlinx.serialization.builtins.ListSerializer(TocItem.serializer()), it) }
                        .getOrDefault(emptyList())
                } ?: emptyList()
                val title = obj["metadata"]?.jsonObject?.get("title")?.jsonPrimitive?.contentOrNull
                ReaderEvent.Loaded(toc, title)
            }
            "relocate" -> ReaderEvent.Relocate(
                cfi = obj["cfi"]?.jsonPrimitive?.contentOrNull,
                fraction = obj["fraction"]?.jsonPrimitive?.doubleOrNull,
                chapterTitle = obj["chapterTitle"]?.jsonPrimitive?.contentOrNull,
            )
            "error" -> ReaderEvent.Error(obj["message"]?.jsonPrimitive?.contentOrNull ?: "Reader error")
            else -> null
        }
    }.getOrNull()
}
