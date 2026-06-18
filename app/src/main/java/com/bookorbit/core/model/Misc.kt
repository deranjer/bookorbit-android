package com.bookorbit.core.model

import kotlinx.serialization.Serializable

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
    const val RANDOM = "random"
    const val SMART_SCOPE = "smart-scope"
}
