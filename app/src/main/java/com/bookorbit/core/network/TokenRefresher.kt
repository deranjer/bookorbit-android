package com.bookorbit.core.network

import com.bookorbit.core.auth.SessionManager
import com.bookorbit.core.auth.SessionOwner
import com.bookorbit.core.model.RefreshResponse
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Authenticator
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Performs the single-flight access-token refresh used by [TokenAuthenticator]: at most one
 * refresh runs at a time, and concurrent 401s reuse its result.
 *
 * Uses a dedicated [OkHttpClient] that shares the cookie jar (so the refresh cookie is sent) but
 * has NO authenticator, preventing infinite refresh recursion. Only an authentication refusal
 * clears the still-matching session; transient server/network failures keep offline access.
 */
@Singleton
class TokenRefresher @Inject constructor(
    private val session: SessionManager,
    @RefreshClient private val refreshClient: dagger.Lazy<OkHttpClient>,
    private val json: Json,
) {
    /**
     * Ensures the cached token differs from [staleToken]. Returns the current valid token, or null
     * if refresh failed (in which case the session has been signed out).
     *
     * @param staleToken the token that was attached to the request that received a 401.
     */
    @Synchronized
    fun refreshIfNeeded(staleToken: String?, expectedOwner: SessionOwner? = null): String? {
        val owner = expectedOwner ?: session.owner() ?: return null
        if (!session.owns(owner)) return null
        // Another thread may have already refreshed while we waited on the lock.
        val current = session.accessToken
        if (!current.isNullOrBlank() && current != staleToken) return current.takeIf { session.owns(owner) }

        val server = owner.serverUrl
        val base = refreshClient.get()
        val request = Request.Builder()
            .url("$server/api/v1/auth/refresh")
            .post(SingleAttemptJsonBody(ByteArray(0), null))
            .build()
        val cookies = base.cookieJar.loadForRequest(request.url)
        if (!session.owns(owner)) return null
        val prepared = request.newBuilder().apply {
            if (cookies.isNotEmpty()) header("Cookie", cookies.joinToString("; ") { "${it.name}=${it.value}" })
        }.build()
        // A stale response must not rotate the next account's refresh cookie before ownership
        // is checked. Snapshot request cookies and apply response cookies only with the token.
        val client = base.newBuilder().cookieJar(CookieJar.NO_COOKIES).authenticator(Authenticator.NONE)
            .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build()

        return try {
            client.newCall(prepared).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful || body.isNullOrBlank()) {
                    if (response.code == 401 || response.code == 403) session.signOutIfOwned(owner)
                    null
                } else {
                    val newToken = json.decodeFromString<RefreshResponse>(body).accessToken
                    if (newToken.isBlank()) return null
                    val updatedCookies = Cookie.parseAll(prepared.url, response.headers)
                    newToken.takeIf { session.commitRefreshedToken(owner, it) {
                        base.cookieJar.saveFromResponse(prepared.url, updatedCookies)
                    } }
                }
            }
        } catch (_: Exception) {
            // Network error: don't sign out (server may just be unreachable); let the call fail.
            null
        }
    }
}
