package com.bookorbit.feature.library.filters

import com.bookorbit.core.model.BookQuery
import com.bookorbit.core.model.Pagination
import com.bookorbit.core.model.SortSpec
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Translates the curated filter state into the server's rule tree. Every populated control
 * contributes exactly one rule under a single top-level AND group. Rules are built as raw JSON
 * because rule values are polymorphic (string list | number).
 */
private fun buildRules(filters: LibraryFilters): List<JsonObject> = buildList {
    if (filters.readStatus.isNotEmpty()) {
        add(includesAny("readStatus", filters.readStatus))
    }
    filters.readProgress?.let { progress ->
        val operator = when (progress) {
            "unread" -> "isUnread"
            "finished" -> "isFinished"
            else -> "isInProgress"
        }
        add(buildJsonObject { put("type", "rule"); put("field", "readProgress"); put("operator", operator) })
    }
    if (filters.formats.isNotEmpty()) add(includesAny("format", filters.formats))
    filters.fileAvailability?.let { availability ->
        add(
            buildJsonObject {
                put("type", "rule")
                put("field", "fileAvailability")
                put("operator", if (availability == "present") "isPresent" else "isMissing")
            },
        )
    }
    if (filters.authors.isNotEmpty()) add(includesAny("author", filters.authors))
    if (filters.genres.isNotEmpty()) add(includesAny("genre", filters.genres))
    if (filters.tags.isNotEmpty()) add(includesAny("tag", filters.tags))
    if (filters.languages.isNotEmpty()) add(includesAny("language", filters.languages))
    filters.minRating?.let { add(numericRule("rating", "gte", it)) }

    val from = filters.yearFrom
    val to = filters.yearTo
    when {
        from != null && to != null -> add(
            buildJsonObject {
                put("type", "rule")
                put("field", "publishedYear")
                put("operator", "between")
                put("value", from)
                put("valueTo", to)
            },
        )
        from != null -> add(numericRule("publishedYear", "gte", from))
        to != null -> add(numericRule("publishedYear", "lte", to))
    }
}

private fun includesAny(field: String, values: List<String>): JsonObject = buildJsonObject {
    put("type", "rule")
    put("field", field)
    put("operator", "includesAny")
    putJsonArray("value") { values.forEach { add(it) } }
}

private fun numericRule(field: String, operator: String, value: Int): JsonObject = buildJsonObject {
    put("type", "rule")
    put("field", field)
    put("operator", operator)
    put("value", value)
}

fun buildBookQuery(
    filters: LibraryFilters,
    sort: LibrarySort,
    page: Int,
    size: Int,
    q: String? = null,
): BookQuery {
    val rules = buildRules(filters)
    val filter = if (rules.isEmpty()) {
        null
    } else {
        buildJsonObject {
            put("type", "group")
            put("join", "AND")
            putJsonArray("rules") { rules.forEach { add(it) } }
        }
    }
    return BookQuery(
        sort = listOf(SortSpec(sort.field, sort.dir)),
        pagination = Pagination(page, size),
        filter = filter,
        q = q?.takeIf { it.isNotBlank() },
    )
}

/** Number of active filter controls — drives the header badge. */
fun countActiveFilters(filters: LibraryFilters): Int = buildRules(filters).size
