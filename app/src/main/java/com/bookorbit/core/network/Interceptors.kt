package com.bookorbit.core.network

import com.bookorbit.core.auth.SessionManager
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rewrites every request onto the user-configured server, prepending `${base}/api/v1` to the
 * relative [ApiService] path.
 *
 * Retrofit is built with a throwaway placeholder base; [ApiService] paths are relative and do NOT
 * include the `/api/v1` prefix. Here we prepend the live server URL (which may itself contain a
 * reverse-proxy subpath) plus `api/v1`, preserving the request path and query.
 */
@Singleton
class BaseUrlInterceptor @Inject constructor(
    private val session: SessionManager,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val server = session.serverUrl
            ?: throw IOException("No server URL configured")
        val base = "$server/api/v1/".toHttpUrlOrNull()
            ?: throw IOException("Invalid server URL: $server")

        val newUrl = base.newBuilder()
            .addEncodedPathSegments(original.url.encodedPath.trimStart('/'))
            .encodedQuery(original.url.encodedQuery)
            .build()

        return chain.proceed(original.newBuilder().url(newUrl).build())
    }
}

/** Adds the bearer access token when present. */
@Singleton
class AuthInterceptor @Inject constructor(
    private val session: SessionManager,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = session.accessToken
        val request = if (token.isNullOrBlank()) {
            chain.request()
        } else {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        }
        return chain.proceed(request)
    }
}
