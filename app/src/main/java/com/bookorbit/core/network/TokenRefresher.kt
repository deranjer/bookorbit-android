package com.bookorbit.core.network

import com.bookorbit.core.auth.SessionManager
import com.bookorbit.core.model.RefreshResponse
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Performs the single-flight access-token refresh used by [TokenAuthenticator]: at most one
 * refresh runs at a time, and concurrent 401s reuse its result.
 *
 * Uses a dedicated [OkHttpClient] that shares the cookie jar (so the refresh cookie is sent) but
 * has NO authenticator, preventing infinite refresh recursion. On failure the session is cleared.
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
    fun refreshIfNeeded(staleToken: String?): String? {
        // Another thread may have already refreshed while we waited on the lock.
        val current = session.accessToken
        if (!current.isNullOrBlank() && current != staleToken) return current

        val server = session.serverUrl ?: return null
        val request = Request.Builder()
            .url("$server/api/v1/auth/refresh")
            .post(ByteArray(0).toRequestBody())
            .build()

        return try {
            refreshClient.get().newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful || body.isNullOrBlank()) {
                    session.signOut()
                    null
                } else {
                    val newToken = json.decodeFromString<RefreshResponse>(body).accessToken
                    session.updateAccessToken(newToken)
                    newToken
                }
            }
        } catch (_: Exception) {
            // Network error: don't sign out (server may just be unreachable); let the call fail.
            null
        }
    }
}
