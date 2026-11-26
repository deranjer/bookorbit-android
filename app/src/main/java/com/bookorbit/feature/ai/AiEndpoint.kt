package com.bookorbit.feature.ai

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

internal fun defaultAiBaseUrl(provider: AiProvider): String = when (provider) {
    AiProvider.OPENAI -> "https://api.openai.com/v1/"
    AiProvider.GOOGLE -> "https://generativelanguage.googleapis.com/v1beta/"
    AiProvider.OPENROUTER -> "https://openrouter.ai/api/v1/"
    AiProvider.OLLAMA -> "http://127.0.0.1:11434/"
    AiProvider.OPENAI_COMPATIBLE -> ""
}

/** Endpoint is a base directory, never a credential-bearing URL or a redirect destination. */
internal fun validatedAiBaseUrl(provider: AiProvider, value: String, allowInsecure: Boolean): HttpUrl {
    require(value.length in 1..2048 && value == value.trim() && value.none { it.isWhitespace() || it.code < 32 })
    val url = value.toHttpUrl()
    require(url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null)
    require(url.isHttps || provider.customEndpoint && allowInsecure)
    require(provider.customEndpoint || url == defaultAiBaseUrl(provider).toHttpUrl())
    require(url.host != "0.0.0.0" && url.host != "::")
    return url.newBuilder().apply { if (!url.encodedPath.endsWith('/')) addPathSegment("") }.build()
}

internal fun aiEndpointLabel(connection: AiConnection): String = runCatching {
    validatedAiBaseUrl(connection.provider, connection.baseUrl, connection.allowInsecure).toString()
}.getOrDefault("")
