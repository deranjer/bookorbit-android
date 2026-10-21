package com.bookorbit.feature.cast

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.net.ServerSocket
import java.io.File
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
                    upstream.accept().use { socket ->
                        val reader = socket.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                            if (line.startsWith("Range:", ignoreCase = true)) requestRange.set(line.substringAfter(':').trim())
                        }
                        socket.getOutputStream().write(("HTTP/1.1 206 Partial Content\r\n" +
                            "Content-Type: audio/mp4\r\nContent-Length: 8\r\n" +
                            "Content-Range: $contentRange\r\nConnection: close\r\n\r\nmetadata").toByteArray())
                    }
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
