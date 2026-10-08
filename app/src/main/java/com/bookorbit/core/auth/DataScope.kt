package com.bookorbit.core.auth

import java.security.MessageDigest

/** Which on-device data set belongs to a server (see [SessionManager.dataScope]). */
object DataScope {
    /** Normalizes a server URL so trivial differences (case, trailing slash) don't make a "new" server. */
    fun key(url: String): String = url.trim().trimEnd('/').lowercase()

    /** "" for the server that owns the original data (or when nothing is configured), else a short stable id. */
    fun of(owner: String?, serverUrl: String?): String {
        val url = serverUrl?.takeIf { it.isNotBlank() } ?: return ""
        val k = key(url)
        if (owner == null || owner == k) return ""
        return MessageDigest.getInstance("SHA-256").digest(k.toByteArray()).joinToString("") { "%02x".format(it) }.take(12)
    }
}
