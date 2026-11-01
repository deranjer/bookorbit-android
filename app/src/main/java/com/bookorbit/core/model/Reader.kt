package com.bookorbit.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** `GET reader/preferences/{bookFileId}`: this book's own settings, if it has any. */
@Serializable
data class ReaderPreferenceResponse(
    val settings: JsonObject? = null,
    val isCustomized: Boolean = false,
)

@Serializable
data class ReaderStorageModeRequest(val sync: Boolean)

/**
 * One uploaded font file (`GET fonts`, `GET server-fonts`). Every file of a family shares one CSS
 * family name, told apart by [weight] and [style]; a variable font spans [weightMin]..[weightMax].
 */
@Serializable
data class ReaderFont(
    val id: Int,
    val familyName: String,
    val format: String,
    val weight: Int = 400,
    val style: String = "normal",
    val weightMin: Int? = null,
    val weightMax: Int? = null,
)
