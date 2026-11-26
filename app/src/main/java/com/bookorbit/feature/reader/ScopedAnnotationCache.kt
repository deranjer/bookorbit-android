package com.bookorbit.feature.reader

import com.bookorbit.core.auth.SessionOwner
import com.bookorbit.core.model.BookAnnotation
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.security.MessageDigest

/** The former unowned list is retained verbatim, never assigned to the next signed-in user. */
@Serializable
internal data class ScopedAnnotationCache(
    val format: Int = 1,
    val scopes: Map<String, List<BookAnnotation>> = emptyMap(),
    val legacyJson: String? = null,
)

internal fun decodeAnnotationCache(raw: String?, json: Json): ScopedAnnotationCache {
    if (raw == null) return ScopedAnnotationCache()
    return runCatching {
        require(json.parseToJsonElement(raw).jsonObject.containsKey("scopes"))
        json.decodeFromString<ScopedAnnotationCache>(raw).also { require(it.format == 1) }
    }
        .getOrElse { ScopedAnnotationCache(legacyJson = raw) }
}

internal fun annotationOwnerPrefix(owner: SessionOwner): String =
    MessageDigest.getInstance("SHA-256").digest(owner.serverUrl.toByteArray()).joinToString("") { "%02x".format(it) } + ":${owner.userId}:"

internal fun annotationScope(owner: SessionOwner, fileId: Int): String = annotationOwnerPrefix(owner) + fileId
