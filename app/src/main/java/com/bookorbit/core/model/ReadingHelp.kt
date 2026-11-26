package com.bookorbit.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ReadingHelpCapability(val enabled: Boolean = false, val careful: Boolean = false, val passageTokens: Int = 8_000)

/** Server-owned prompts; no arbitrary prompt, full-book upload or inferred reading boundary. */
@Serializable
data class ReadingHelpRequest(
    val kind: String,
    val fileId: Int,
    val language: String,
    val passages: List<String>,
    val term: String? = null,
    val scope: String? = null,
    val careful: Boolean = false,
) {
    fun validate() {
        require(fileId > 0)
        require(kind in setOf("term", "description", "recap-note", "recap-stop"))
        require(language.length <= 35 && language.matches(Regex("[A-Za-z]{2,3}(-[A-Za-z0-9]{1,8})*")))
        require(passages.size in 1..24 && passages.all { it.isNotBlank() && it.length <= 12_000 })
        require(passages.sumOf { it.length } <= 64_000)
        if (kind in setOf("term", "description")) require(!term.isNullOrBlank() && term.length <= 120)
        if (kind in setOf("recap-note", "recap-stop")) require(passages.size == 1)
        require(scope == null || scope in setOf("book", "chapter"))
    }
}

@Serializable
data class ReadingHelpEvent(
    val type: String,
    val text: String = "",
    val cached: Boolean = false,
    val careful: Boolean = false,
    val errorCode: String? = null,
)
