package com.bookorbit.feature.cast

import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import android.util.Log
import com.bookorbit.BuildConfig
import androidx.media3.common.MediaItem
import com.bookorbit.core.network.CastUpstreamClient
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Call
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.Inet4Address
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
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
        abstract val filterMp4: Boolean
        var patches: List<Mp4HeaderPatch>? = null
        class Local(val path: String, override val filterMp4: Boolean) : ProxyTarget()
        class Remote(val url: String, override val filterMp4: Boolean) : ProxyTarget()
    }

    private val executor = Executors.newCachedThreadPool()
    private val activeSockets = ConcurrentHashMap.newKeySet<Socket>()
    private val activeCalls = ConcurrentHashMap.newKeySet<Call>()

    data class SourceQueue(
        val items: List<MediaItem>,
        val castItems: List<MediaItem>,
        val startIndex: Int,
        val startPositionMs: Long,
    )

    @Volatile var sourceQueue: SourceQueue? = null
        private set

    val isRunning: Boolean get() = running

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
                activeSockets.add(client)
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
        sourceQueue = null
        closeTransfers()
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
    @Synchronized
    fun rewriteForCast(items: List<MediaItem>, startIndex: Int = 0, startPositionMs: Long = 0): List<MediaItem> {
        check(running) { "CastProxyServer.start() must be called before rewriteForCast()" }
        val token = UUID.randomUUID().toString()
        val port = serverSocket?.localPort ?: error("CastProxyServer is not running")
        val ip = lanIpAddress() ?: error("No LAN address available for the cast proxy")
        val newTargets = mutableMapOf<String, ProxyTarget>()

        val rewritten = items.map { item ->
            val uri = item.localConfiguration?.uri ?: return@map item
            val key = item.mediaId
            // CastPlayer's DefaultMediaItemConverter throws IllegalArgumentException when the item has
            // no mimeType, and our items are built from bare URIs (often extensionless API URLs).
            val mime = item.localConfiguration?.mimeType
                ?: mimeTypeFor(uri.path.orEmpty()).takeIf { it.startsWith("audio/") }
                ?: "audio/mpeg"
            newTargets[key] = targetFor(uri, mime.substringBefore(';') == "audio/mp4")
            val builder = item.buildUpon()
                .setUri(Uri.parse("http://$ip:$port/$token/$key"))
                .setMimeType(mime)

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
        sessionToken = token
        sourceQueue = SourceQueue(items.toList(), rewritten, startIndex.coerceAtLeast(0), startPositionMs.coerceAtLeast(0))
        closeTransfers() // Old book requests must not keep consuming bandwidth after queue replacement.
        trace("Queue replaced items=${items.size} index=$startIndex positionMs=$startPositionMs")
        return rewritten
    }

    private fun closeTransfers() {
        activeCalls.forEach { it.cancel() }
        activeSockets.forEach { runCatching { it.close() } }
    }

    /** A bounded debug trace survives small Android log buffers and wireless-debugging disconnects. */
    private fun trace(message: String) {
        if (!BuildConfig.DEBUG) return
        Log.i("CastProxyServer", message)
        synchronized(traceLock) {
            runCatching {
                val file = File(context.cacheDir, "cast-proxy-trace.log")
                if (file.length() > 262_144) file.writeText("")
                file.appendText("${System.currentTimeMillis()} $message\n")
            }
        }
    }

    private val traceLock = Any()

    // A content:// local Uri (SAF-backed download) isn't castable — RandomAccessFile has no SAF
    // equivalent — but it must still route to Local so serveLocal() 404s it cleanly instead of
    // Remote handing a non-http(s) scheme to OkHttp, which throws.
    private fun targetFor(uri: Uri, filterMp4: Boolean = false): ProxyTarget =
        if (uri.scheme == "file" || uri.scheme == "content") {
            ProxyTarget.Local(uri.path ?: uri.toString(), filterMp4)
        } else {
            ProxyTarget.Remote(uri.toString(), filterMp4)
        }

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
                if (method != "GET" && method != "HEAD" && method != "OPTIONS") {
                    writeStatus(out, 405, "Method Not Allowed")
                    return
                }

                val segments = path.trimStart('/').split('/')
                val activeToken = sessionToken
                if (segments.size < 2 || activeToken == null || segments[0] != activeToken) {
                    writeStatus(out, 403, "Forbidden")
                    return
                }
                if (method == "OPTIONS") {
                    writeStatus(out, if (targets.containsKey(segments[1])) 204 else 404, "No Content")
                    return
                }
                when (val target = targets[segments[1]]) {
                    is ProxyTarget.Local -> serveLocal(out, target, rangeHeader, method == "HEAD")
                    is ProxyTarget.Remote -> serveRemote(out, target, rangeHeader, method == "HEAD")
                    null -> writeStatus(out, 404, "Not Found")
                }
            } catch (e: IOException) {
                // Client disconnected mid-transfer (e.g. the receiver abandoned a seek) - not an error.
            } finally {
                activeSockets.remove(socket)
            }
        }
    }

    private fun serveLocal(out: OutputStream, target: ProxyTarget.Local, rangeHeader: String?, headOnly: Boolean) {
        val file = File(target.path)
        if (!file.exists()) {
            writeStatus(out, 404, "Not Found")
            return
        }
        val length = file.length()
        val contentType = mimeTypeFor(target.path)
        val range = parseRangeHeader(rangeHeader, length)
        trace("Local request head=$headOnly range=${rangeHeader ?: "none"} length=$length mime=$contentType")
        if (rangeHeader != null && range == null) {
            writeHeaders(out, 416, "Range Not Satisfiable", contentType, 0, "bytes */$length")
            return
        }
        if (range != null) {
            val contentLength = range.last - range.first + 1
            writeHeaders(out, 206, "Partial Content", contentType, contentLength, "bytes ${range.first}-${range.last}/$length")
            if (!headOnly) {
                RandomAccessFile(file, "r").use { raf ->
                    val patches = localPatches(target, raf)
                    raf.seek(range.first)
                    copyRange(raf, out, contentLength, patches)
                }
            }
        } else {
            writeHeaders(out, 200, "OK", contentType, length, null)
            if (!headOnly) RandomAccessFile(file, "r").use {
                val patches = localPatches(target, it)
                it.seek(0)
                copyRange(it, out, length, patches)
            }
        }
    }

    private fun serveRemote(out: OutputStream, target: ProxyTarget.Remote, rangeHeader: String?, headOnly: Boolean) {
        val started = System.nanoTime()
        trace("Upstream request head=$headOnly range=${rangeHeader ?: "none"}")
        val request = Request.Builder().url(target.url).header("Accept-Encoding", "identity").apply {
            if (rangeHeader != null) header("Range", rangeHeader)
        }.build()
        val call = upstreamClient.newCall(request)
        activeCalls.add(call)
        try {
            val response = try {
                call.execute()
            } catch (e: IOException) {
                if (BuildConfig.DEBUG) Log.w("CastProxyServer", "Upstream request failed: ${e.javaClass.simpleName}")
                writeStatus(out, 502, "Bad Gateway")
                return
            }
            response.use { resp ->
                val body = resp.body
                val contentType = resp.header("Content-Type") ?: "application/octet-stream"
                val contentLength = body?.contentLength()?.takeIf { it >= 0 }
                val contentRange = resp.header("Content-Range")
                val span = contentRange?.let { Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(it) }
                val start = if (resp.code == 200) 0L else span?.groupValues?.get(1)?.toLongOrNull()
                val total = if (resp.code == 200) contentLength else span?.groupValues?.get(3)?.toLongOrNull()
                val identity = resp.header("Content-Encoding").let { it == null || it.equals("identity", true) }
                val patches = if (!headOnly && identity && start != null && total != null && resp.code in listOf(200, 206)) {
                    chapterPatches(target, total) { position, count, budget -> readRemoteMetadata(target.url, position, count, total, budget) }
                } else emptyList()
                val message = resp.message.ifBlank { "OK" }
                trace("Upstream headers status=${resp.code} contentRange=$contentRange length=$contentLength afterMs=${(System.nanoTime() - started) / 1_000_000}")
                if (BuildConfig.DEBUG) {
                    if (rangeHeader != null && resp.code == 200) {
                        Log.w("CastProxyServer", "Upstream ignored byte range; large M4B metadata may require reading the whole file")
                    }
                }
                writeHeaders(out, resp.code, message, contentType, contentLength, contentRange)
                if (!headOnly && body != null) body.byteStream().use {
                    val bytes = copyStream(it, out, start ?: 0, patches)
                    trace("Upstream transfer bytes=$bytes elapsedMs=${(System.nanoTime() - started) / 1_000_000}")
                }
            }
        } finally {
            activeCalls.remove(call)
        }
    }

    private fun localPatches(target: ProxyTarget.Local, raf: RandomAccessFile): List<Mp4HeaderPatch> =
        chapterPatches(target, raf.length()) { position, count, _ ->
            raf.seek(position)
            ByteArray(count).also { raf.readFully(it) }
        }

    private fun chapterPatches(target: ProxyTarget, length: Long, read: (Long, Int, Long) -> ByteArray?): List<Mp4HeaderPatch> {
        if (!target.filterMp4) return emptyList()
        // Compute once per queue target, off the UI thread, including the unchanged fallback.
        return synchronized(target) {
            target.patches ?: try {
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
                val result = Mp4ChapterFilter.discover(length) { position, count ->
                    val remaining = deadline - System.nanoTime()
                    if (remaining <= 0) null else read(position, count, remaining)
                }
                target.patches = result
                trace("MP4 chapter filter patches=${result.size} length=$length")
                result
            } catch (_: IOException) {
                emptyList<Mp4HeaderPatch>().also {
                    target.patches = it
                    trace("MP4 chapter filter unavailable; original bytes retained")
                }
            }
        }
    }

    private fun readRemoteMetadata(url: String, position: Long, count: Int, total: Long, budgetNanos: Long): ByteArray? {
        val end = position + count - 1
        val request = Request.Builder().url(url).header("Accept-Encoding", "identity")
            .header("Range", "bytes=$position-$end").build()
        val call = upstreamClient.newCall(request)
        call.timeout().timeout(minOf(TimeUnit.SECONDS.toNanos(10), budgetNanos), TimeUnit.NANOSECONDS)
        activeCalls.add(call)
        try {
            return call.execute().use { response ->
                // Never download the full book if an upstream ignores Range or changes representation.
                if (response.code != 206 || response.header("Content-Range") != "bytes $position-$end/$total" ||
                    response.header("Content-Encoding").let { it != null && !it.equals("identity", true) }) return@use null
                val stream = response.body?.byteStream() ?: return@use null
                val bytes = ByteArray(count)
                var offset = 0
                while (offset < count) {
                    val read = stream.read(bytes, offset, count - offset)
                    if (read < 0) return@use null
                    offset += read
                }
                bytes
            }
        } finally {
            activeCalls.remove(call)
        }
    }

    private fun copyStream(input: InputStream, out: OutputStream, start: Long, patches: List<Mp4HeaderPatch>): Long {
        val buffer = ByteArray(COPY_BUFFER_SIZE)
        var copied = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            patches.forEach { it.apply(start + copied, buffer, read) }
            out.write(buffer, 0, read)
            copied += read
        }
        return copied
    }

    private fun copyRange(raf: RandomAccessFile, out: OutputStream, length: Long, patches: List<Mp4HeaderPatch>) {
        val started = System.nanoTime()
        val buffer = ByteArray(COPY_BUFFER_SIZE)
        var remaining = length
        try {
            while (remaining > 0) {
                val read = raf.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (read <= 0) break
                patches.forEach { it.apply(raf.filePointer - read, buffer, read) }
                out.write(buffer, 0, read)
                remaining -= read
            }
        } finally {
            trace("Local transfer bytes=${length - remaining} complete=${remaining == 0L} elapsedMs=${(System.nanoTime() - started) / 1_000_000}")
        }
    }

    private fun writeStatus(out: OutputStream, code: Int, message: String) {
        out.write("HTTP/1.1 $code $message\r\nConnection: close\r\n${corsHeaders()}Content-Length: 0\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
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
        sb.append(corsHeaders())
        sb.append("Accept-Ranges: bytes\r\n")
        sb.append("Content-Type: $contentType\r\n")
        if (contentLength != null) sb.append("Content-Length: $contentLength\r\n")
        if (contentRange != null) sb.append("Content-Range: $contentRange\r\n")
        sb.append("\r\n")
        out.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
        out.flush()
    }

    private fun corsHeaders() = "Access-Control-Allow-Origin: *\r\n" +
        "Access-Control-Allow-Methods: GET, HEAD, OPTIONS\r\n" +
        "Access-Control-Allow-Headers: Content-Type, Accept-Encoding, Range\r\n" +
        "Access-Control-Expose-Headers: Content-Length, Content-Range, Accept-Ranges\r\n"

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
        const val COPY_BUFFER_SIZE = 64 * 1024
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
    val match = Regex("""bytes=(\d*)-(\d*)""").matchEntire(header) ?: return null
    val startStr = match.groupValues[1]
    val endStr = match.groupValues[2]
    if (startStr.isEmpty() && endStr.isEmpty()) return null

    val start: Long
    val end: Long
    if (startStr.isEmpty()) {
        val suffixLength = endStr.toLongOrNull()?.takeIf { it > 0 } ?: return null
        start = (totalLength - suffixLength).coerceAtLeast(0)
        end = totalLength - 1
    } else {
        start = startStr.toLongOrNull() ?: return null
        end = if (endStr.isEmpty()) totalLength - 1 else (endStr.toLongOrNull() ?: return null).coerceAtMost(totalLength - 1)
    }
    if (start > end || start >= totalLength) return null
    return start..end
}
