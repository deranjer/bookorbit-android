package com.bookorbit.feature.reader

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Credential responses are served only to the configured server's exact refresh endpoint. */
class ServerReaderPolicy(serverUrl: String, bookId: Int, fileId: Int) {
    private val base = (serverUrl.trimEnd('/') + "/").toHttpUrl()
    val readerUrl: HttpUrl = base.newBuilder().addPathSegment("read")
        .addPathSegment(bookId.toString()).addPathSegment(fileId.toString()).build()
    private val refreshUrl = base.newBuilder().addPathSegments("api/v1/auth/refresh").build()

    fun matchesServer(serverUrl: String): Boolean = (serverUrl.trimEnd('/') + "/").toHttpUrl() == base

    fun sameOrigin(url: HttpUrl): Boolean =
        url.scheme == base.scheme && url.host == base.host && url.port == base.port

    fun isRefresh(url: HttpUrl, method: String): Boolean =
        method == "POST" && sameOrigin(url) && url.encodedPath == refreshUrl.encodedPath && url.query == null

    fun isReader(url: HttpUrl): Boolean = sameOrigin(url) && url.encodedPath == readerUrl.encodedPath
}
