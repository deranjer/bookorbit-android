package com.bookorbit.feature.reader

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Credential responses are served only to the configured server's exact refresh endpoint. */
class ServerReaderPolicy(serverUrl: String, bookId: Int, fileId: Int, format: String = "epub") {
    private val base = (serverUrl.trimEnd('/') + "/").toHttpUrl()
    val readerUrl: HttpUrl = base.newBuilder().addPathSegment("read")
        .addPathSegment(bookId.toString()).addPathSegment(fileId.toString())
        .addQueryParameter("format", format.lowercase()).build()
    private val refreshUrl = base.newBuilder().addPathSegments("api/v1/auth/refresh").build()
    private val imagePath = base.newBuilder().addPathSegments("api/v1/cbz/files/$fileId/pages/").build().encodedPath

    fun isSelectedComicImage(url: HttpUrl, method: String): Boolean = method == "GET" && sameOrigin(url) &&
        url.query == null && url.encodedPath.startsWith(imagePath) &&
        url.encodedPath.removePrefix(imagePath).matches(Regex("[0-9]{1,6}"))

    fun matchesServer(serverUrl: String): Boolean = (serverUrl.trimEnd('/') + "/").toHttpUrl() == base

    fun sameOrigin(url: HttpUrl): Boolean =
        url.scheme == base.scheme && url.host == base.host && url.port == base.port

    fun isRefresh(url: HttpUrl, method: String): Boolean =
        method == "POST" && sameOrigin(url) && url.encodedPath == refreshUrl.encodedPath && url.query == null

    fun isReader(url: HttpUrl): Boolean = sameOrigin(url) && url.encodedPath == readerUrl.encodedPath
}
