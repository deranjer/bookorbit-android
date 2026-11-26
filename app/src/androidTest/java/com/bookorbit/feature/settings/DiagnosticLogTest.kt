package com.bookorbit.feature.settings

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.bookorbit.core.diagnostics.*
import com.bookorbit.core.network.AttestationProblem
import com.bookorbit.core.network.AttestationStage
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class DiagnosticLogTest {
    @Test fun storageFailureIsVisibleWithoutBreakingHttp() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val id = "diagnostic-io-test-${UUID.randomUUID()}"
        val root = File(app.cacheDir, id).apply { writeText("not a directory") }
        val context = object : ContextWrapper(app) {
            override fun getNoBackupFilesDir(): File = root
            override fun getSharedPreferences(name: String, mode: Int) = app.getSharedPreferences("$id-$name", mode)
        }
        val log = DiagnosticLog(context)
        try {
            log.setEnabled(true)
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setBody("unchanged response"))
                val client = OkHttpClient.Builder().addInterceptor(DiagnosticInterceptor(log)).build()
                withContext(Dispatchers.IO) { client.newCall(Request.Builder().url(server.url("/private")).build()).execute().use {
                    assertEquals("unchanged response", it.body!!.string())
                } }
                log.export()
                assertTrue(log.failed.value)
                assertEquals(1, server.requestCount)
            }
        } finally {
            log.close(); app.deleteSharedPreferences("$id-diagnostic_logging"); root.delete()
        }
    }

    @Test fun optInPersistsExportsSafeEventsAndDisableAndClearWork() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val id = "diagnostic-test-${UUID.randomUUID()}"
        val root = File(app.cacheDir, id)
        val context = object : ContextWrapper(app) {
            override fun getNoBackupFilesDir(): File = root
            override fun getSharedPreferences(name: String, mode: Int) = app.getSharedPreferences("$id-$name", mode)
        }
        val log = DiagnosticLog(context)
        try {
            assertFalse(log.enabled.value)
            log.setEnabled(true)
            val reopened = DiagnosticLog(context)
            assertTrue(reopened.enabled.value); reopened.close()
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setResponseCode(403).setBody("private response body"))
                val client = OkHttpClient.Builder().addInterceptor(DiagnosticInterceptor(log)).build()
                val request = Request.Builder().url(server.url("/private/api/v1/app-attest/android-keys?token=private-query"))
                    .header("Authorization", "Bearer private-token").header("Cookie", "private-cookie")
                    .post("private-certificate-and-book-text".toRequestBody("application/json".toMediaType())).build()
                withContext(Dispatchers.IO) { client.newCall(request).execute().use { assertEquals("private response body", it.body!!.string()) } }
                log.record(DiagnosticEntry(DiagnosticEvent.ATTESTATION_FAILED, stage = AttestationStage.REGISTRATION,
                    problem = AttestationProblem.SERVER, status = 403, serverCode = "APP_ATTEST_INVALID"))
                val exported = log.export()
                val text = exported.decodeToString()
                assertTrue(text.contains("route=ATTEST_REGISTER")); assertTrue(text.contains("status=403")); assertTrue(text.contains("code=APP_ATTEST_INVALID"))
                for (secret in listOf("private-query", "private-token", "private-cookie", "private-certificate", "private response", server.hostName)) assertFalse(text.contains(secret))
                val destination = File(root, "export.txt")
                app.contentResolver.openOutputStream(android.net.Uri.fromFile(destination), "wt")!!.use { it.write(exported) }
                assertArrayEquals(exported, destination.readBytes())
                log.setEnabled(false)
                server.enqueue(MockResponse().setBody("ignored"))
                withContext(Dispatchers.IO) { client.newCall(request).execute().close() }
                assertArrayEquals(exported, log.export())
                assertEquals(2, server.requestCount)
            }
            log.clear()
            assertFalse(log.export().decodeToString().contains("HTTP_COMPLETED"))
            assertFalse(log.export().decodeToString().contains("APP_ATTEST_INVALID"))
        } finally {
            log.close(); app.deleteSharedPreferences("$id-diagnostic_logging"); root.deleteRecursively()
        }
    }
}
