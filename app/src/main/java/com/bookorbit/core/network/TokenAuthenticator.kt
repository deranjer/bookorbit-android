package com.bookorbit.core.network

import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp [Authenticator] invoked when a request comes back 401. Triggers a single-flight token
 * refresh ([TokenRefresher]) and, on success, retries the original request with the new bearer
 * token.
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val refresher: TokenRefresher,
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        // Give up after one retry to avoid loops if refresh keeps yielding a token the server rejects.
        if (responseCount(response) >= 2) return null

        val staleToken = response.request.header("Authorization")?.removePrefix("Bearer ")
        val newToken = refresher.refreshIfNeeded(staleToken) ?: return null

        return response.request.newBuilder()
            .header("Authorization", "Bearer $newToken")
            .build()
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
