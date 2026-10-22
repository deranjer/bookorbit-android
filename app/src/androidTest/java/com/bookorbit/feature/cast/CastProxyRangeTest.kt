package com.bookorbit.feature.cast

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.net.ServerSocket
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** An 8 GiB virtual source sends only its eight-byte tail: no large download is needed. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class CastProxyRangeTest {
    private fun int(value: Int) = ByteBuffer.allocate(4).putInt(value).array()
    private fun box(type: String, bytes: ByteArray = byteArrayOf()) = int(bytes.size + 8) + type.toByteArray() + bytes
    private fun mp4(): ByteArray {
        fun track(id: Int, handler: String, ref: Boolean): ByteArray = box("trak",
            box("tkhd", ByteArray(12) + int(id) + ByteArray(8)) +
                box("mdia", box("hdlr", ByteArray(8) + handler.toByteArray())) +
                if (ref) box("tref", box("chap", int(2))) else byteArrayOf())
        return box("ftyp", "M4A ".toByteArray()) + box("moov", track(1, "soun", true) + track(2, "text", false)) +
            box("mdat", ByteArray(200) { 77 })
    }

    @Test fun chapterFilteringIsIdenticalForLocalRemoteAndPartialRangeResponses() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val original = mp4()
        val patches = Mp4ChapterFilter.discover(original.size.toLong()) { start, count -> original.copyOfRange(start.toInt(), start.toInt() + count) }
        assertEquals(2, patches.size)
        val expected = original.copyOf().also { bytes -> patches.forEach { it.apply(0, bytes, bytes.size) } }
        val source = File.createTempFile("cast-chapters", ".m4a", context.cacheDir).apply { writeBytes(original) }
        val ranges = CopyOnWriteArrayList<String>()
        val client = OkHttpClient()
        val proxy = CastProxyServer(context, client)
        ServerSocket(0).use { upstream ->
            val server = Thread {
                try {
                    while (!upstream.isClosed) upstream.accept().use { socket ->
                        val reader = socket.getInputStream().bufferedReader()
                        val requestLine = reader.readLine().orEmpty()
                        var range: String? = null
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                            if (line.startsWith("Range:", true)) range = line.substringAfter(':').trim()
                        }
                        range?.let { ranges.add(it) }
                        if (requestLine.contains("/no-ranges")) range = null
                        val span = parseRangeHeader(range, original.size.toLong()) ?: 0L until original.size.toLong()
                        val body = original.copyOfRange(span.first.toInt(), span.last.toInt() + 1)
                        val headers = "HTTP/1.1 ${if (range != null) "206 Partial Content" else "200 OK"}\r\n" +
                            "Content-Type: audio/mp4\r\nContent-Length: ${body.size}\r\nConnection: close\r\n" +
                            (if (range != null) "Content-Range: bytes ${span.first}-${span.last}/${original.size}\r\n" else "") + "\r\n"
                        socket.getOutputStream().write(headers.toByteArray() + body)
                    }
                } catch (_: java.io.IOException) { }
            }.apply { isDaemon = true; start() }
            try {
                proxy.start()
                for (uri in listOf(source.toURI().toString(), "http://127.0.0.1:${upstream.localPort}/extensionless")) {
                    val item = MediaItem.Builder().setMediaId("chapters").setUri(uri).setMimeType("audio/mp4").build()
                    val url = proxy.rewriteForCast(listOf(item)).single().localConfiguration!!.uri.toString()
                    client.newCall(Request.Builder().url(url).build()).execute().use {
                        assertEquals(original.size.toLong(), it.body!!.contentLength())
                        assertArrayEquals(expected, it.body!!.bytes())
                    }
                    for (patch in patches) {
                        // Request only the middle two bytes of a four-byte patch.
                        val start = patch.offset + 1
                        client.newCall(Request.Builder().url(url).header("Range", "bytes=$start-${start+1}").build()).execute().use {
                            assertEquals(206, it.code)
                            assertEquals("bytes $start-${start+1}/${original.size}", it.header("Content-Range"))
                            assertArrayEquals(expected.copyOfRange(start.toInt(), start.toInt()+2), it.body!!.bytes())
                        }
                    }
                    client.newCall(Request.Builder().url(url).header("Range", "bytes=-200").build()).execute().use {
                        assertArrayEquals(original.takeLast(200).toByteArray(), it.body!!.bytes())
                    }
                }
                assertEquals("Metadata discovery is cached across later ranges", 1, ranges.count { it == "bytes=0-15" })
                val noRanges = MediaItem.Builder().setMediaId("no-ranges")
                    .setUri("http://127.0.0.1:${upstream.localPort}/no-ranges").setMimeType("audio/mp4").build()
                val noRangesUrl = proxy.rewriteForCast(listOf(noRanges)).single().localConfiguration!!.uri.toString()
                client.newCall(Request.Builder().url(noRangesUrl).build()).execute().use {
                    assertEquals(200, it.code)
                    assertArrayEquals("Range-ignoring upstream must stay byte-for-byte original", original, it.body!!.bytes())
                }
                assertTrue(ranges.contains("bytes=0-15"))
                assertArrayEquals(original, source.readBytes())
            } finally {
                proxy.stop()
                source.delete()
                upstream.close()
                server.join(1_000)
                client.connectionPool.evictAll()
                client.dispatcher.executorService.shutdown()
            }
        }
    }

    @Test fun replacingBookInvalidatesOldUrlsAndServesNewLocalRangesWithCors() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val first = File.createTempFile("cast-first", ".m4a", context.cacheDir).apply { writeText("old-book") }
        val second = File.createTempFile("cast-second", ".m4b", context.cacheDir).apply { writeText("new-book-metadata") }
        val client = OkHttpClient()
        val proxy = CastProxyServer(context, client)
        try {
            proxy.start()
            fun item(file: File) = MediaItem.Builder().setMediaId("same-id")
                .setUri(file.toURI().toString()).setMimeType("audio/mp4").build()
            val oldUrl = proxy.rewriteForCast(listOf(item(first))).single().localConfiguration!!.uri.toString()
            val originals = listOf(item(second))
            val newUrl = proxy.rewriteForCast(originals, 0, 12_345).single().localConfiguration!!.uri.toString()
            assertEquals(originals, proxy.sourceQueue!!.items)
            assertEquals(12_345L, proxy.sourceQueue!!.startPositionMs)
            client.newCall(Request.Builder().url(oldUrl).build()).execute().use { assertEquals(403, it.code) }
            client.newCall(Request.Builder().url(newUrl).method("OPTIONS", null).build()).execute().use {
                assertEquals(204, it.code)
                assertEquals("*", it.header("Access-Control-Allow-Origin"))
                assertTrue(it.header("Access-Control-Allow-Headers")!!.contains("Range"))
            }
            client.newCall(Request.Builder().url(newUrl).header("Range", "bytes=-8").build()).execute().use {
                assertEquals(206, it.code)
                assertEquals("bytes 9-16/17", it.header("Content-Range"))
                assertEquals("metadata", it.body!!.string())
                assertEquals("audio/mp4", it.header("Content-Type"))
            }
            client.newCall(Request.Builder().url(newUrl).header("Range", "bytes=900000000000000000000-").build()).execute().use {
                assertEquals(416, it.code)
                assertEquals("bytes */17", it.header("Content-Range"))
            }
        } finally {
            proxy.stop()
            first.delete()
            second.delete()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    @Test fun remoteTailRangePreservesLargeOffsetsAndTransfersOnlyRequestedBytes() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val requestRange = AtomicReference<String>()
        val upstreamFailure = AtomicReference<Throwable>()
        val total = 8L * 1024 * 1024 * 1024
        val first = total - 8
        val contentRange = "bytes $first-${total - 1}/$total"
        val client = OkHttpClient()
        val proxy = CastProxyServer(context, client)
        ServerSocket(0).use { upstream ->
            val serving = Thread {
                try {
                    repeat(2) { upstream.accept().use { socket ->
                        val reader = socket.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                        var range: String? = null
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                            if (line.startsWith("Range:", ignoreCase = true)) range = line.substringAfter(':').trim()
                        }
                        if (range == "bytes=0-15") {
                            // Bounded metadata probe: invalid MP4 header means safe passthrough.
                            socket.getOutputStream().write(("HTTP/1.1 206 Partial Content\r\n" +
                                "Content-Length: 16\r\nContent-Range: bytes 0-15/$total\r\nConnection: close\r\n\r\n").toByteArray() + ByteArray(16))
                        } else {
                            requestRange.set(range)
                            socket.getOutputStream().write(("HTTP/1.1 206 Partial Content\r\n" +
                                "Content-Type: audio/mp4\r\nContent-Length: 8\r\n" +
                                "Content-Range: $contentRange\r\nConnection: close\r\n\r\nmetadata").toByteArray())
                        }
                    } }
                } catch (e: Throwable) { upstreamFailure.set(e) }
            }.apply { isDaemon = true; start() }
            try {
                proxy.start()
                val item = MediaItem.Builder().setMediaId("tail-test")
                    .setUri("http://127.0.0.1:${upstream.localPort}/extensionless")
                    .setMimeType("audio/mp4").build()
                val uri = proxy.rewriteForCast(listOf(item)).single().localConfiguration!!.uri.toString()
                client.newCall(Request.Builder().url(uri).header("Range", "bytes=$first-").build()).execute().use {
                    assertEquals(206, it.code)
                    assertEquals(contentRange, it.header("Content-Range"))
                    assertEquals(8L, it.body!!.contentLength())
                    assertEquals("metadata", it.body!!.string())
                }
                serving.join(1_000)
                assertNull(upstreamFailure.get())
                assertEquals("bytes=$first-", requestRange.get())
            } finally {
                proxy.stop()
                client.connectionPool.evictAll()
                client.dispatcher.executorService.shutdown()
            }
        }
    }
}
