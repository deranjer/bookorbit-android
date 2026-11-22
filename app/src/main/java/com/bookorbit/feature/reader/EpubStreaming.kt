package com.bookorbit.feature.reader

import com.bookorbit.core.auth.DataScope
import com.bookorbit.core.auth.SessionManager
import com.bookorbit.core.network.AuthInterceptor
import com.bookorbit.core.network.BaseUrlInterceptor
import com.bookorbit.core.network.MainClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.*
import kotlinx.serialization.Serializable
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Serializable
data class EpubStreamingMeta(val bookId: Int, val fileId: Int, val baseUrl: String, val info: JsonObject)

/** Only metadata and resources of one exact edition are exposed to the credential-free WebView. */
internal class EpubStreamPaths(val bookId: Int, val fileId: Int, info: JsonObject, nonce: String = UUID.randomUUID().toString()) {
    val baseUrl = "https://appassets.androidplatform.net/reader-stream/$nonce"
    private val prefix = "$baseUrl/$bookId/file/".toHttpUrl()
    private val allowed = buildSet {
        add("META-INF/container.xml")
        add(checkNotNull(info["containerPath"]?.jsonPrimitive?.contentOrNull))
        val manifest = checkNotNull(info["manifest"]?.jsonArray)
        require(manifest.size in 1..100_000)
        manifest.forEach { add(checkNotNull(it.jsonObject["href"]?.jsonPrimitive?.contentOrNull)) }
        info["optionalFiles"]?.jsonArray?.forEach { add(it.jsonPrimitive.content) }
    }.also { paths ->
        require(bookId > 0 && fileId > 0)
        require(paths.all { it.length in 1..8192 && !it.startsWith('/') && '\\' !in it && '\u0000' !in it &&
            it.split('/').none { part -> part == "." || part == ".." || part.isEmpty() } })
    }

    fun resource(url: HttpUrl): String? {
        if (url.scheme != "https" || url.host != prefix.host || url.port != 443 || url.username.isNotEmpty() || url.password.isNotEmpty()) return null
        if (!url.encodedPath.startsWith(prefix.encodedPath) || url.queryParameterNames != setOf("fileId") ||
            url.queryParameterValues("fileId") != listOf(fileId.toString())) return null
        val path = url.pathSegments.drop(prefix.pathSegments.size - 1).joinToString("/")
        return path.takeIf { it in allowed }
    }

    fun serverResource(server: String, path: String): HttpUrl {
        require(path in allowed)
        val builder = "$server/api/v1/epub/$bookId/file/".toHttpUrl().newBuilder()
        // v3.9.0's generic EPUB file route decodes after router decoding. Preserve literal % names.
        path.split('/').forEach { builder.addPathSegment(it.replace("%", "%25")) }
        return builder.addQueryParameter("fileId", fileId.toString()).build()
    }
}

/** Reusable open plan; each WebView gets a separately cancellable resource lease. */
class EpubStreamPlan internal constructor(
    val bookId: Int, val fileId: Int, val info: JsonObject,
    private val server: String, private val client: OkHttpClient,
    private val token: () -> String,
) {
    fun newSession() = EpubStreamSession(EpubStreamPaths(bookId, fileId, info), server, client, token)
}

class EpubStreamSession internal constructor(
    private val paths: EpubStreamPaths, private val server: String,
    private val client: OkHttpClient, private val token: () -> String,
) : AutoCloseable {
    val baseUrl get() = paths.baseUrl
    private val closed = AtomicBoolean()
    private val calls = ConcurrentHashMap.newKeySet<Call>()
    data class Asset(val status: Int, val mime: String, val input: InputStream)

    /** Runs on WebView's interception thread, never the UI thread. Nothing is written to disk. */
    fun asset(url: HttpUrl): Asset? {
        val path = paths.resource(url) ?: return null
        if (closed.get()) throw IOException("Reader stream closed")
        val call = client.newCall(Request.Builder().url(paths.serverResource(server, path))
            .header("Authorization", "Bearer ${token()}").build())
        calls.add(call)
        var response: Response? = null
        try {
            if (closed.get()) throw IOException("Reader stream closed")
            response = call.execute()
            token() // Account/server must still match after the request as well.
            val body = response.body ?: throw IOException("Empty EPUB resource")
            val held = response
            val input = object : FilterInputStream(body.byteStream()) {
                override fun close() { try { super.close() } finally { held.close(); calls.remove(call) } }
            }
            return Asset(if (response.code in 300..399) 502 else response.code,
                body.contentType()?.let { "${it.type}/${it.subtype}" } ?: "application/octet-stream", input)
        } catch (e: Exception) { response?.close(); calls.remove(call); call.cancel(); throw e }
    }

    override fun close() { closed.set(true); calls.forEach { it.cancel() }; calls.clear() }
}

@Singleton
class EpubStreaming @Inject constructor(@MainClient main: OkHttpClient, private val session: SessionManager, private val json: Json) {
    private val client = main.newBuilder().apply {
        interceptors().removeAll { it is BaseUrlInterceptor || it is AuthInterceptor }
    }.authenticator(Authenticator.NONE).followRedirects(false).followSslRedirects(false).cache(null).build()

    suspend fun open(bookId: Int, fileId: Int): EpubStreamPlan {
        val server = session.serverUrl ?: throw IOException("Not signed in")
        val userId = session.currentUser?.id ?: throw IOException("Not signed in")
        fun token(): String {
            if (session.currentUser?.id != userId || session.serverUrl?.let(DataScope::key) != DataScope.key(server)) throw IOException("Reader account changed")
            return session.accessToken ?: throw IOException("Not signed in")
        }
        val url = "$server/api/v1/epub/$bookId/info".toHttpUrl().newBuilder().addQueryParameter("fileId", fileId.toString()).build()
        val call = client.newCall(Request.Builder().url(url).header("Authorization", "Bearer ${token()}").build())
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val info = response.use {
                            if (!it.isSuccessful) throw IOException("EPUB streaming unavailable: HTTP ${it.code}")
                            token()
                            val source = it.body?.source() ?: throw IOException("Missing EPUB metadata")
                            source.request(8 * 1024 * 1024L + 1)
                            if (source.buffer.size > 8 * 1024 * 1024) throw IOException("EPUB metadata too large")
                            json.parseToJsonElement(source.readUtf8()).jsonObject
                        }
                        EpubStreamPaths(bookId, fileId, info) // Validate before opening the renderer.
                        token()
                        if (continuation.isActive) continuation.resume(EpubStreamPlan(bookId, fileId, info, server, client, ::token))
                    } catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
                }
            })
        }
    }
}
