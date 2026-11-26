package com.bookorbit.feature.dictionary

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.junit.Assert.*
import org.junit.Test
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZOutputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeUnit

class DictionaryRepositoryTest {
    private val tei = """<?xml version="1.0"?><!DOCTYPE TEI SYSTEM "freedict-P5.dtd"><TEI><teiHeader><title>Test dictionary</title><availability><ref target="https://creativecommons.org/licenses/by-sa/3.0/legalcode">CC BY-SA 3.0</ref></availability></teiHeader><text><body><entry><form><orth>Hello</orth></form><sense><cit><quote>привет &amp; здравствуйте</quote></cit></sense></entry></body></text></TEI>"""

    @Test fun verifiedIndexIsOfflineAndFailedUpdateRetainsPreviousDictionary() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val dir = File(app.cacheDir, "dictionary-test-${UUID.randomUUID()}").apply { mkdirs() }
        val repository = DictionaryRepository(object : ContextWrapper(app) { override fun getFilesDir() = dir })
        try {
            val archive = archive(dir, tei)
            val pack = DictionaryPackage("eng-rus", "test", archive.length(), MessageDigest.getInstance("SHA-512").digest(archive.readBytes()).joinToString("") { "%02x".format(it) }, 1)
            repository.installArchive(pack, archive)
            assertTrue(repository.lookup("eng-rus", "HELLO")!!.contains("привет & здравствуйте"))
            assertNull(repository.lookup("eng-rus", "not-present"))
            assertNull(repository.lookup("eng-rus", "x' OR 1=1 --"))
            assertEquals(1, repository.installed().size)
            assertTrue(runCatching { repository.installArchive(pack.copy(sha512 = "0".repeat(128)), archive) }.isFailure)
            assertTrue(repository.lookup("eng-rus", "hello")!!.contains("привет"))
            assertTrue(repository.installed().single().notice.contains("Fixture license"))
            repository.installArchive(pack, archive)
            assertTrue(repository.lookup("eng-rus", "hello")!!.contains("привет"))
            repository.remove("eng-rus")
            assertTrue(repository.installed().isEmpty())
        } finally { dir.deleteRecursively() }
    }

    @Test fun parserRejectsInternalEntitiesAndUnknownLicenseWithoutExternalRequests() {
        val hostile = tei.replace("SYSTEM \"freedict-P5.dtd\"", "[<!ENTITY leak SYSTEM 'http://127.0.0.1:9/secret'>]")
        assertTrue(runCatching { importDictionaryTei(ByteArrayInputStream(hostile.toByteArray()), {}) { _, _ -> } }.isFailure)
        assertTrue(runCatching { importDictionaryTei(ByteArrayInputStream(tei.replace("by-sa/3.0", "unknown/1.0").toByteArray()), {}) { _, _ -> } }.isFailure)
    }

    @Test fun parserAndArchiveRespectCancellationAndEntryLimits() {
        assertTrue(runCatching { importDictionaryTei(ByteArrayInputStream(tei.replace("Hello", "h".repeat(121)).toByteArray()), {}) { _, _ -> } }.isFailure)
        var checks = 0
        assertTrue(runCatching { importDictionaryTei(ByteArrayInputStream(tei.toByteArray()), { if (++checks > 5) throw kotlinx.coroutines.CancellationException() }) { _, _ -> } }.exceptionOrNull() is kotlinx.coroutines.CancellationException)
    }

    @Test fun explicitDownloadHasNoCredentialsAndCancelledOrCorruptUpdateKeepsIndex() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val dir = File(app.cacheDir, "dictionary-network-test-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val archive = archive(dir, tei)
            val bytes = archive.readBytes()
            val pack = DictionaryPackage("eng-rus", "test", bytes.size.toLong(), MessageDigest.getInstance("SHA-512").digest(bytes).joinToString("") { "%02x".format(it) }, 1)
            MockWebServer().use { server ->
                val client = OkHttpClient.Builder().followRedirects(false).retryOnConnectionFailure(false)
                    .callTimeout(5, TimeUnit.SECONDS).addInterceptor { chain ->
                        val request = chain.request()
                        require(request.url.toString() == pack.url)
                        chain.proceed(request.newBuilder().url(server.url(request.url.encodedPath)).build())
                    }.build()
                val repository = DictionaryRepository(File(dir, "private"), client)
                server.enqueue(MockResponse().setBody(Buffer().write(bytes)))
                repository.install(pack) { _, _ -> }
                val request = server.takeRequest()
                assertNull(request.getHeader("Authorization")); assertNull(request.getHeader("Cookie"))
                assertTrue(repository.lookup("eng-rus", "hello")!!.contains("привет"))
                val corrupt = bytes.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
                server.enqueue(MockResponse().setBody(Buffer().write(corrupt)))
                assertTrue(runCatching { repository.install(pack) { _, _ -> } }.isFailure)
                server.enqueue(MockResponse().setBody(Buffer().write(bytes)).throttleBody(8, 1, TimeUnit.SECONDS))
                val started = System.nanoTime()
                val cancelled = runCatching { withTimeout(200) { repository.install(pack) { _, _ -> } } }.exceptionOrNull()
                assertTrue("Expected cancellation, got ${cancelled?.stackTraceToString()}", cancelled is kotlinx.coroutines.CancellationException)
                assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 4000)
                assertTrue(repository.lookup("eng-rus", "hello")!!.contains("привет"))
                assertTrue(File(dir, "private").listFiles()!!.none { it.name.endsWith(".download") || it.name.endsWith(".pending") })
                assertEquals(3, server.requestCount)
            }
        } finally { dir.deleteRecursively() }
    }

    private fun archive(dir: File, xml: String): File = File(dir, "fixture.tar.xz").also { file ->
        XZOutputStream(file.outputStream(), LZMA2Options()).use { xz -> TarArchiveOutputStream(xz).use { tar ->
            for ((name, text) in mapOf("fixture/fixture.tei" to xml, "fixture/COPYING" to "Fixture license CC BY-SA 3.0")) {
                val bytes = text.toByteArray()
                tar.putArchiveEntry(TarArchiveEntry(name).apply { size = bytes.size.toLong() })
                tar.write(bytes); tar.closeArchiveEntry()
            }
        } }
    }
}
