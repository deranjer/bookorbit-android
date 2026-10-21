package com.bookorbit.feature.cast

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.net.ServerSocket
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
