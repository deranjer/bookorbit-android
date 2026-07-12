package com.bookorbit.feature.cast

import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import androidx.media3.common.MediaItem
import com.bookorbit.core.network.CastUpstreamClient
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.Inet4Address
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device HTTP server that re-serves audiobook bytes to a Cast device over the LAN. This is the
 * only bridge between the app's two audio source types - local `file://` downloads and server URLs
 * that require an `Authorization: Bearer` header - and a stock Cast receiver, which can reach
 * neither directly. Only listens while a cast session is active: [start] is called from
 * `PlaybackService` when a cast session becomes available, [stop] when it ends, closing the
 * LAN-exposure window immediately (the token also changes every session, so a stale URL from a
 * prior session can never be replayed).
 */
@Singleton
class CastProxyServer @Inject constructor(
    @ApplicationContext private val context: Context,
    @CastUpstreamClient private val upstreamClient: OkHttpClient,
) {
    private sealed class ProxyTarget {
        data class Local(val path: String) : ProxyTarget()
        data class Remote(val url: String) : ProxyTarget()
    }

    private val executor = Executors.newCachedThreadPool()

    @Volatile private var serverSocket: ServerSocket? = null

    @Volatile private var running = false

    @Volatile private var sessionToken: String? = null

    @Volatile private var targets: Map<String, ProxyTarget> = emptyMap()

    /** Starts listening on an OS-assigned ephemeral port and returns it. */
    @Synchronized
    fun start(): Int {
        stop()
        val socket = ServerSocket(0)
        serverSocket = socket
        sessionToken = UUID.randomUUID().toString()
        running = true
        Thread {
            while (running) {
                val client = try {
                    socket.accept()
                } catch (e: IOException) {
                    break
                }
                executor.execute { handle(client) }
            }
        }.apply { isDaemon = true }.start()
        return socket.localPort
    }

    /** Stops listening and invalidates the session token; any in-flight or cached URL becomes dead. */
    @Synchronized
    fun stop() {
        running = false
        sessionToken = null
        targets = emptyMap()
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    /**
     * Registers each item's current URI (local file or authenticated server URL) and returns a copy
     * pointed at this server instead. `buildUpon()` (not a fresh `MediaItem.Builder`) is required so
     * `mediaMetadata.extras` - the per-file duration and chapter-start data
     * [com.bookorbit.feature.player.BookAggregatingPlayer] depends on - survives the rewrite. Local
     * cover art is rewritten the same way, since it is exactly as unreachable from the Cast device as
     * a local audio file.
     */
    fun rewriteForCast(items: List<MediaItem>): List<MediaItem> {
        val token = sessionToken ?: error("CastProxyServer.start() must be called before rewriteForCast()")
        val port = serverSocket?.localPort ?: error("CastProxyServer is not running")
        val ip = lanIpAddress() ?: error("No LAN address available for the cast proxy")
        val newTargets = mutableMapOf<String, ProxyTarget>()

        val rewritten = items.map { item ->
            val uri = item.localConfiguration?.uri ?: return@map item
            val key = item.mediaId
            newTargets[key] = targetFor(uri)
            val builder = item.buildUpon().setUri(Uri.parse("http://$ip:$port/$token/$key"))

            val artwork = item.mediaMetadata.artworkUri
            if (artwork != null && artwork.scheme == "file") {
                val artKey = "art-$key"
                newTargets[artKey] = targetFor(artwork)
                val newMetadata = item.mediaMetadata.buildUpon()
                    .setArtworkUri(Uri.parse("http://$ip:$port/$token/$artKey"))
                    .build()
                builder.setMediaMetadata(newMetadata)
            }
            builder.build()
        }
        targets = newTargets
        return rewritten
    }

    private fun targetFor(uri: Uri): ProxyTarget =
        if (uri.scheme == "file") ProxyTarget.Local(uri.path ?: uri.toString()) else ProxyTarget.Remote(uri.toString())

    private fun handle(socket: Socket) {
        socket.use {
            try {
                socket.soTimeout = SOCKET_TIMEOUT_MS
                val input = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.ISO_8859_1))
                val requestLine = input.readLine() ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return
                val method = parts[0]
                val path = parts[1]

                var rangeHeader: String? = null
                while (true) {
                    val line = input.readLine() ?: break
                    if (line.isEmpty()) break
                    val idx = line.indexOf(':')
                    if (idx <= 0) continue
                    if (line.substring(0, idx).trim().equals("Range", ignoreCase = true)) {
                        rangeHeader = line.substring(idx + 1).trim()
                    }
                }

                val out = socket.getOutputStream()
                if (method != "GET" && method != "HEAD") {
                    writeStatus(out, 405, "Method Not Allowed")
                    return
                }

                val segments = path.trimStart('/').split('/')
                val activeToken = sessionToken
                if (segments.size < 2 || activeToken == null || segments[0] != activeToken) {
                    writeStatus(out, 403, "Forbidden")
                    return
                }
                when (val target = targets[segments[1]]) {
                    is ProxyTarget.Local -> serveLocal(out, target.path, rangeHeader, method == "HEAD")
                    is ProxyTarget.Remote -> serveRemote(out, target.url, rangeHeader, method == "HEAD")
                    null -> writeStatus(out, 404, "Not Found")
                }
            } catch (e: IOException) {
                // Client disconnected mid-transfer (e.g. the receiver abandoned a seek) - not an error.
            }
        }
    }

    private fun serveLocal(out: OutputStream, path: String, rangeHeader: String?, headOnly: Boolean) {
        val file = File(path)
        if (!file.exists()) {
            writeStatus(out, 404, "Not Found")
            return
        }
        val length = file.length()
        val contentType = mimeTypeFor(path)
        val range = parseRangeHeader(rangeHeader, length)
        if (range != null) {
            val contentLength = range.last - range.first + 1
            writeHeaders(out, 206, "Partial Content", contentType, contentLength, "bytes ${range.first}-${range.last}/$length")
            if (!headOnly) {
                RandomAccessFile(file, "r").use { raf ->
                    raf.seek(range.first)
                    copyRange(raf, out, contentLength)
                }
            }
        } else {
            writeHeaders(out, 200, "OK", contentType, length, null)
            if (!headOnly) file.inputStream().use { it.copyTo(out) }
        }
    }

    private fun serveRemote(out: OutputStream, url: String, rangeHeader: String?, headOnly: Boolean) {
        val request = Request.Builder().url(url).apply {
            if (rangeHeader != null) header("Range", rangeHeader)
        }.build()
        val response = try {
            upstreamClient.newCall(request).execute()
        } catch (e: IOException) {
            writeStatus(out, 502, "Bad Gateway")
            return
        }
        response.use { resp ->
            val body = resp.body
            val contentType = resp.header("Content-Type") ?: "application/octet-stream"
            val contentLength = body?.contentLength()?.takeIf { it >= 0 }
            val contentRange = resp.header("Content-Range")
            val message = resp.message.ifBlank { "OK" }
            writeHeaders(out, resp.code, message, contentType, contentLength, contentRange)
            if (!headOnly && body != null) body.byteStream().use { it.copyTo(out) }
        }
    }

    private fun copyRange(raf: RandomAccessFile, out: OutputStream, length: Long) {
        val buffer = ByteArray(COPY_BUFFER_SIZE)
        var remaining = length
        while (remaining > 0) {
            val read = raf.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read <= 0) break
            out.write(buffer, 0, read)
            remaining -= read
        }
    }

    private fun writeStatus(out: OutputStream, code: Int, message: String) {
        out.write("HTTP/1.1 $code $message\r\nConnection: close\r\nContent-Length: 0\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
        out.flush()
    }

    private fun writeHeaders(
        out: OutputStream,
        code: Int,
        message: String,
        contentType: String,
        contentLength: Long?,
        contentRange: String?,
    ) {
        val sb = StringBuilder()
        sb.append("HTTP/1.1 $code $message\r\n")
        sb.append("Connection: close\r\n")
        sb.append("Accept-Ranges: bytes\r\n")
        sb.append("Content-Type: $contentType\r\n")
        if (contentLength != null) sb.append("Content-Length: $contentLength\r\n")
        if (contentRange != null) sb.append("Content-Range: $contentRange\r\n")
        sb.append("\r\n")
        out.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
        out.flush()
    }

    private fun mimeTypeFor(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
        "mp3" -> "audio/mpeg"
        "m4b", "m4a", "mp4" -> "audio/mp4"
        "aac" -> "audio/aac"
        "flac" -> "audio/flac"
        "ogg", "oga" -> "audio/ogg"
        "wav" -> "audio/wav"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        else -> "application/octet-stream"
    }

    private fun lanIpAddress(): String? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val network = cm.activeNetwork ?: return null
        val linkProperties = cm.getLinkProperties(network) ?: return null
        return linkProperties.linkAddresses
            .map { it.address }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress }
            ?.hostAddress
    }

    private companion object {
        const val SOCKET_TIMEOUT_MS = 30_000
        const val COPY_BUFFER_SIZE = 8 * 1024
    }
}

/**
 * Parses an HTTP `Range: bytes=start-end` header into an inclusive byte range, or null if absent,
 * malformed, or unsatisfiable against [totalLength]. Standalone so it's testable without a running
 * server (only single-range `bytes=` requests are supported - multi-range is not needed here since
 * ExoPlayer/Cast receivers only ever request one contiguous span at a time).
 */
internal fun parseRangeHeader(header: String?, totalLength: Long): LongRange? {
    if (header == null || totalLength <= 0) return null
    val match = Regex("""bytes=(\d*)-(\d*)""").find(header) ?: return null
    val startStr = match.groupValues[1]
    val endStr = match.groupValues[2]
    if (startStr.isEmpty() && endStr.isEmpty()) return null

    val start: Long
    val end: Long
    if (startStr.isEmpty()) {
        val suffixLength = endStr.toLong()
        start = (totalLength - suffixLength).coerceAtLeast(0)
        end = totalLength - 1
    } else {
        start = startStr.toLong()
        end = if (endStr.isEmpty()) totalLength - 1 else endStr.toLong().coerceAtMost(totalLength - 1)
    }
    if (start > end || start >= totalLength) return null
    return start..end
}
