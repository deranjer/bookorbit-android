package com.bookorbit.core.network

import com.bookorbit.core.auth.SecureStorage
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CookieJar that persists cookies (notably the HTTP-only refresh-token cookie the server sets on
 * login/refresh) into encrypted storage, so authenticated sessions survive across app launches.
 */
@Singleton
class PersistentCookieJar @Inject constructor(
    private val storage: SecureStorage,
    private val json: Json,
) : CookieJar {

    @Serializable
    private data class StoredCookie(
        val name: String,
        val value: String,
        val domain: String,
        val path: String,
        val expiresAt: Long,
        val secure: Boolean,
        val httpOnly: Boolean,
        val hostOnly: Boolean,
    )

    // Keyed by domain. Loaded lazily from storage and kept in memory thereafter.
    private val byDomain: MutableMap<String, MutableList<StoredCookie>> = loadFromStorage()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val list = byDomain.getOrPut(url.host) { mutableListOf() }
        for (cookie in cookies) {
            list.removeAll { it.name == cookie.name && it.path == cookie.path }
            list.add(cookie.toStored())
        }
        persist()
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val result = mutableListOf<Cookie>()
        for ((_, list) in byDomain) {
            val expired = list.filter { it.expiresAt != 0L && it.expiresAt < now }
            if (expired.isNotEmpty()) list.removeAll(expired)
            list.mapNotNull { it.toCookie() }
                .filter { it.matches(url) }
                .let(result::addAll)
        }
        return result
    }

    private fun persist() {
        val flat = byDomain.values.flatten()
        storage.putString(
            SecureStorage.KEY_COOKIES,
            json.encodeToString(ListSerializer(StoredCookie.serializer()), flat),
        )
    }

    private fun loadFromStorage(): MutableMap<String, MutableList<StoredCookie>> {
        val raw = storage.getString(SecureStorage.KEY_COOKIES) ?: return mutableMapOf()
        val stored = runCatching {
            json.decodeFromString(ListSerializer(StoredCookie.serializer()), raw)
        }.getOrDefault(emptyList())
        return stored.groupByTo(mutableMapOf(), { it.domain }, { it })
    }

    private fun Cookie.toStored() = StoredCookie(
        name = name,
        value = value,
        domain = domain,
        path = path,
        expiresAt = expiresAt,
        secure = secure,
        httpOnly = httpOnly,
        hostOnly = hostOnly,
    )

    private fun StoredCookie.toCookie(): Cookie? = runCatching {
        Cookie.Builder()
            .name(name)
            .value(value)
            .path(path)
            // OkHttp's sentinel for "no expiry" (session cookie); see okhttp3.Cookie.MAX_DATE.
            .expiresAt(if (expiresAt == 0L) 253402300799999L else expiresAt)
            .apply {
                if (hostOnly) hostOnlyDomain(domain) else domain(domain)
                if (secure) secure()
                if (httpOnly) httpOnly()
            }
            .build()
    }.getOrNull()
}
