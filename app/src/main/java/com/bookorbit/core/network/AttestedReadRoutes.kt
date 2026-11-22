package com.bookorbit.core.network

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response

/** Guarded non-AI GET routes currently used by ApiService, audited against server v3.9.0. */
internal object AttestedReadRoutes {
    fun matches(method: String, url: HttpUrl): Boolean {
        if (method != "GET") return false
        val path = url.encodedPath
        val at = path.indexOf("/api/v1/")
        if (at < 0) return false
        val route = path.substring(at + "/api/v1/".length)
        return route in setOf("dashboard/widgets/reading-streak", "dashboard/widgets/reading-goal") ||
            route.matches(Regex("smart-scopes/[1-9][0-9]*/books"))
    }
}

internal class AttestedReadInterceptor(private val attestation: AndroidAttestation) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        return chain.proceed(if (AttestedReadRoutes.matches(request.method, request.url)) attestation.sign(request) else request)
    }
}
