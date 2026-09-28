package com.bookorbit.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class AppInfo(
    val version: String,
    val updateAvailable: Boolean? = null,
    val latestVersion: String? = null,
    val bookDockPath: String? = null,
)

/** Catalog typeahead rows (`GET /metadata/{authors,genres,tags,languages}`) return `{ name }`. */
@Serializable
data class NamedResult(val name: String? = null)

/** Scroller types accepted by `GET /dashboard/scrollers/:type`. */
object ScrollerType {
    const val RECENTLY_ADDED = "recently-added"
    const val CONTINUE_READING = "continue-reading"
    const val CONTINUE_LISTENING = "continue-listening"
    const val RANDOM = "random"
    const val SMART_SCOPE = "smart-scope"
}

/**
 * `GET /dashboard/scrollers/{type}` body: a bare `BookCard[]` before server 3.0, `{ books, total }`
 * since. Accepts both.
 */
object ScrollerBooks {
    fun decode(json: Json, body: JsonElement): List<BookCard> {
        val books = (body as? JsonObject)?.get("books") ?: body
        return json.decodeFromJsonElement(ListSerializer(BookCard.serializer()), books)
    }
}
