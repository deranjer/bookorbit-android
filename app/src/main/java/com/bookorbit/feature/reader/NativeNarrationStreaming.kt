package com.bookorbit.feature.reader

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.bookorbit.core.auth.SessionManager
import com.bookorbit.core.auth.SessionOwner
import com.bookorbit.core.network.BaseUrlInterceptor
import com.bookorbit.core.network.MainClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Online source for the native engine. Opening metadata never fetches an audio asset or ZIP. */
@Singleton
class NativeNarrationStreaming @Inject constructor(
    @MainClient main: OkHttpClient, private val session: SessionManager, private val json: Json,
) {
    // Native media URLs are already absolute. Keep scoped auth/token refresh, never rewrite twice.
    private val client = main.newBuilder().apply { interceptors().removeAll { it is BaseUrlInterceptor } }
        .followRedirects(false).followSslRedirects(false).cache(null).build()

    internal suspend fun open(bookId: Int, fileId: Int, expectedOwner: SessionOwner? = null): NativeNarrationPlan {
        require(bookId > 0 && fileId > 0)
        val owner = expectedOwner ?: session.owner() ?: throw IOException("Not signed in")
        if (!session.owns(owner)) throw IOException("Narration account changed")
        val base = owner.serverUrl.toHttpUrl()
        if (base.username.isNotEmpty() || base.password.isNotEmpty() || base.query != null || base.fragment != null)
            throw IOException("Invalid narration server URL")
        val url = "${owner.serverUrl.trimEnd('/')}/api/v1/epub/$bookId/media-overlay".toHttpUrl().newBuilder()
            .addQueryParameter("fileId", fileId.toString()).build()
        val call = client.newCall(Request.Builder().url(url).tag(SessionOwner::class.java, owner).build())
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val playlist = response.use {
                            if (!it.isSuccessful) throw IOException("Native narration unavailable: HTTP ${it.code}")
                            if (!session.owns(owner)) throw IOException("Narration account changed")
                            val source = it.body?.source() ?: throw IOException("Missing narration playlist")
                            source.request(MAX_METADATA_BYTES + 1)
                            if (source.buffer.size > MAX_METADATA_BYTES) throw IOException("Narration playlist too large")
                            json.decodeFromString<NativeNarrationPlaylist>(source.readUtf8())
                        }
                        val timeline = NativeNarrationTimeline(playlist, bookId, fileId)
                        if (!session.owns(owner)) throw IOException("Narration account changed")
                        if (continuation.isActive) continuation.resume(NativeNarrationPlan(timeline, owner, session, client))
                    } catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
                }
            })
        }
    }

    companion object { private const val MAX_METADATA_BYTES = 32 * 1024 * 1024L }
}

/** Native-only plan: no JWT or authenticated URL is passed to WebView. Media3 requests HTTP Range. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class NativeNarrationPlan(
    val timeline: NativeNarrationTimeline,
    val owner: SessionOwner,
    private val session: SessionManager,
    private val client: OkHttpClient,
) {
    private val urls = timeline.clips.map { it.audioHref }.toSet().associateWith { href ->
        val builder = "${owner.serverUrl.trimEnd('/')}/api/v1/epub/${timeline.bookId}/media-overlay/file/".toHttpUrl().newBuilder()
        // Unlike v3.9.0's generic EPUB file route, this route decodes only once.
        href.split('/').forEach { builder.addPathSegment(it) }
        builder.addQueryParameter("fileId", timeline.fileId.toString()).build()
    }
    private val allowed = urls.values.toSet()

    fun resourceUrl(href: String): HttpUrl = urls[href] ?: throw IOException("Narration resource is not in the playlist")

    fun dataSourceFactory(): DataSource.Factory {
        val calls = Call.Factory { request ->
            requireOwner()
            if (request.url !in allowed) throw IOException("Narration resource identity changed")
            client.newCall(request.newBuilder().tag(SessionOwner::class.java, owner).build())
        }
        val factory = OkHttpDataSource.Factory(calls)
        return DataSource.Factory {
            val delegate = factory.createDataSource()
            object : DataSource by delegate {
                override fun open(dataSpec: DataSpec): Long {
                    requireOwner()
                    if (dataSpec.uri.toString().toHttpUrlOrNull() !in allowed) throw IOException("Narration resource is not in the playlist")
                    val length = delegate.open(dataSpec)
                    try { requireOwner() } catch (e: IOException) { delegate.close(); throw e }
                    return length
                }

                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    try { requireOwner() } catch (e: IOException) { delegate.close(); throw e }
                    return delegate.read(buffer, offset, length)
                }
            }
        }
    }

    private fun requireOwner() {
        if (!session.owns(owner)) throw IOException("Narration account changed")
    }
}
