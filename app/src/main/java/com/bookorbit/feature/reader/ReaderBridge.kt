package com.bookorbit.feature.reader

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
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

/** A box in the WebView's own coordinate space; CSS px equal dp, so it can be used as dp directly. */
data class ViewportRect(val left: Double, val top: Double, val right: Double, val bottom: Double)

/** One in-book search match, with the text around it. */
data class SearchHit(val cfi: String, val pre: String, val match: String, val post: String)

sealed interface ReaderEvent {
    data object Ready : ReaderEvent
    data class Loaded(val toc: List<TocItem>, val title: String?) : ReaderEvent
    data class Relocate(
        val cfi: String?,
        val fraction: Double?,
        val chapterTitle: String?,
    ) : ReaderEvent
    data class Error(val message: String) : ReaderEvent

    /** A tap on the page body (not on a link or highlight). [x] is 0..1 across the viewport. */
    data class Tap(val x: Double) : ReaderEvent
    data class Selection(val text: String, val cfi: String?, val rect: ViewportRect?) : ReaderEvent
    data object SelectionCleared : ReaderEvent
    data class AnnotationTap(val cfi: String, val rect: ViewportRect?) : ReaderEvent

    /** A batch of matches from one section ([label] is its chapter). */
    data class SearchResults(val label: String, val hits: List<SearchHit>) : ReaderEvent
    data class SearchProgress(val progress: Double) : ReaderEvent
    data class SearchDone(val total: Int, val capped: Boolean) : ReaderEvent
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

    private fun parseRect(el: kotlinx.serialization.json.JsonElement?): ViewportRect? = runCatching {
        val o = el?.jsonObject ?: return null
        ViewportRect(
            o["left"]!!.jsonPrimitive.doubleOrNull!!,
            o["top"]!!.jsonPrimitive.doubleOrNull!!,
            o["right"]!!.jsonPrimitive.doubleOrNull!!,
            o["bottom"]!!.jsonPrimitive.doubleOrNull!!,
        )
    }.getOrNull()

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
            "tap" -> obj["x"]?.jsonPrimitive?.doubleOrNull?.let { ReaderEvent.Tap(it) }
            "selection" -> obj["text"]?.jsonPrimitive?.contentOrNull?.let {
                ReaderEvent.Selection(it, obj["cfi"]?.jsonPrimitive?.contentOrNull, parseRect(obj["rect"]))
            }
            "selectionCleared" -> ReaderEvent.SelectionCleared
            "searchResults" -> {
                val hits = obj["items"]?.jsonArray?.mapNotNull { el ->
                    val o = el.jsonObject
                    val cfi = o["cfi"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    SearchHit(
                        cfi = cfi,
                        pre = o["pre"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        match = o["match"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        post = o["post"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    )
                }.orEmpty()
                ReaderEvent.SearchResults(obj["label"]?.jsonPrimitive?.contentOrNull.orEmpty(), hits)
            }
            "searchProgress" -> obj["progress"]?.jsonPrimitive?.doubleOrNull?.let { ReaderEvent.SearchProgress(it) }
            "searchDone" -> ReaderEvent.SearchDone(
                total = obj["total"]?.jsonPrimitive?.intOrNull ?: 0,
                capped = obj["capped"]?.jsonPrimitive?.booleanOrNull ?: false,
            )
            "annotationTap" -> obj["cfi"]?.jsonPrimitive?.contentOrNull?.let {
                ReaderEvent.AnnotationTap(it, parseRect(obj["rect"]))
            }
            else -> null
        }
    }.getOrNull()
}
