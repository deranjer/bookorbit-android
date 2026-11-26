package com.bookorbit.feature.player

import com.bookorbit.core.auth.SessionManager
import com.bookorbit.core.auth.SessionOwner
import com.bookorbit.core.network.BaseUrlInterceptor
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** Keep configured CA trust, live authorization and single-flight refresh, but never rewrite an
 * already absolute media URL or follow a redirect carrying account credentials. */
internal fun audioStreamClient(main: OkHttpClient): OkHttpClient = main.newBuilder().apply {
    interceptors().removeAll { it is BaseUrlInterceptor }
}.followRedirects(false).followSslRedirects(false).cache(null).build()

/** One account lease per HTTP DataSource; Range and source URL survive authorization retries. */
internal class AudioStreamCalls(
    private val client: OkHttpClient, private val session: SessionManager, private val owner: SessionOwner?,
) : Call.Factory {
    fun requireOwner() {
        if (owner == null || !session.owns(owner)) throw IOException("Audio account changed")
    }

    override fun newCall(request: Request): Call {
        requireOwner()
        val expected = checkNotNull(owner)
        val server = expected.serverUrl.toHttpUrlOrNull() ?: throw IOException("Invalid audio server")
        val url = request.url
        val prefix = server.encodedPath.trimEnd('/') + "/api/v1/"
        if (url.scheme != server.scheme || url.host != server.host || url.port != server.port ||
            !url.encodedPath.startsWith(prefix) || url.username.isNotEmpty() || url.password.isNotEmpty() || url.fragment != null)
            throw IOException("Audio destination changed")
        val route = url.encodedPath.removePrefix(prefix)
        if (request.method != "GET" || !(route.matches(Regex("books/files/[1-9][0-9]*/serve")) ||
                route.matches(Regex("audiobooks/[1-9][0-9]*/assets/[^/]+/content"))))
            throw IOException("Not an audiobook resource")
        return client.newCall(request.newBuilder().tag(SessionOwner::class.java, expected).build())
    }
}
