package com.bookorbit.feature.reader

import android.webkit.WebResourceResponse
import com.bookorbit.core.auth.SessionManager
import com.bookorbit.core.auth.SessionOwner
import com.bookorbit.core.network.BaseUrlInterceptor
import com.bookorbit.core.network.MainClient
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.FilterInputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Server web images lack the JS bearer header; proxy only the selected edition's exact page URLs. */
@Singleton
class ServerComicImages @Inject constructor(@MainClient main: OkHttpClient, private val session: SessionManager) {
    private val client = main.newBuilder().apply { interceptors().removeAll { it is BaseUrlInterceptor } }
        .followRedirects(false).followSslRedirects(false).cache(null).build()

    fun open(policy: ServerReaderPolicy, owner: SessionOwner?, url: HttpUrl, method: String): WebResourceResponse? {
        if (!policy.isSelectedComicImage(url, method)) return null
        fun unavailable() = WebResourceResponse("text/plain", "UTF-8", 503, "Unavailable", mapOf("Cache-Control" to "no-store"), "".byteInputStream())
        if (owner == null || !session.owns(owner) || !policy.matchesServer(owner.serverUrl)) return unavailable()
        return try {
            val response = client.newCall(Request.Builder().url(url).tag(SessionOwner::class.java, owner).build()).execute()
            val body = response.body
            if (!response.isSuccessful || body == null || !session.owns(owner) || body.contentLength() > 64 * 1024 * 1024L) {
                response.close()
                unavailable()
            } else {
                val stream = object : FilterInputStream(body.byteStream()) {
                    private var bytes = 0L
                    private fun checkOwner(count: Int = 0) {
                        bytes += count.coerceAtLeast(0)
                        if (!session.owns(owner) || bytes > 64 * 1024 * 1024L) { close(); throw IOException("Comic image unavailable") }
                    }
                    override fun read(): Int {
                        checkOwner()
                        val value = super.read()
                        checkOwner(if (value >= 0) 1 else 0)
                        if (value < 0) close()
                        return value
                    }
                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                        checkOwner()
                        val count = `in`.read(buffer, offset, length)
                        checkOwner(count)
                        if (count < 0) close()
                        return count
                    }
                    override fun skip(count: Long): Long {
                        checkOwner()
                        val skipped = super.skip(count.coerceIn(0L, 64 * 1024 * 1024L))
                        checkOwner(skipped.toInt())
                        return skipped
                    }
                    override fun close() { super.close(); response.close() }
                }
                WebResourceResponse(body.contentType()?.let { "${it.type}/${it.subtype}" } ?: "application/octet-stream", null,
                    200, "OK", mapOf("Cache-Control" to "no-store"), stream)
            }
        } catch (_: IOException) { unavailable() }
    }
}
