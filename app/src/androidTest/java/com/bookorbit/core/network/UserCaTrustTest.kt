package com.bookorbit.core.network

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android TLS + OkHttp, with an injected test CA; does not install roots on the device. */
@RunWith(AndroidJUnit4::class)
class UserCaTrustTest {
    private val settings = UserCaSettings(InstrumentationRegistry.getInstrumentation().targetContext)
    private val originalConsent = listOf("localhost", "127.0.0.1").associateWith {
        settings.isEnabledForUrl("https://$it")
    }
    private val root = HeldCertificate.Builder().certificateAuthority(0).commonName("BookOrbit instrumentation CA").build()
    private val servers = mutableListOf<MockWebServer>()
    private val clients = mutableListOf<OkHttpClient>()

    @After fun cleanup() {
        originalConsent.forEach { (host, enabled) -> settings.setEnabledForUrl("https://$host", enabled) }
        clients.forEach { it.connectionPool.evictAll(); it.dispatcher.executorService.shutdown() }
        servers.forEach { it.shutdown() }
    }

    private fun server(expired: Boolean = false, wrongHost: Boolean = false): MockWebServer {
        val builder = HeldCertificate.Builder().commonName("localhost").signedBy(root)
        if (wrongHost) builder.addSubjectAlternativeName("wrong-host.invalid")
        else builder.addSubjectAlternativeName("localhost").addSubjectAlternativeName("127.0.0.1")
        if (expired) builder.validityInterval(0L, 1_000L)
        val certificates = HandshakeCertificates.Builder().heldCertificate(builder.build(), root.certificate).build()
        return MockWebServer().apply { useHttps(certificates.sslSocketFactory(), false); start(); servers += this }
    }

    private fun client(): OkHttpClient {
        val store = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null); setCertificateEntry("test", root.certificate) }
        return UserCaTrust(settings) { UserCaTrust.trustManagerFor(store) }.applyTo(OkHttpClient.Builder())
            .connectTimeout(3, TimeUnit.SECONDS).readTimeout(3, TimeUnit.SECONDS)
            .build().also { clients += it }
    }

    private fun rejected(client: OkHttpClient, url: String) {
        try {
            client.newCall(Request.Builder().url(url).build()).execute().use { fail("Expected TLS rejection: $url") }
        } catch (e: IOException) {
            assertTrue("Expected certificate/handshake failure, got $e",
                generateSequence<Throwable>(e) { it.cause }.any { it is java.security.cert.CertificateException || it is javax.net.ssl.SSLException })
        }
    }

    @Test fun consentCanBeEnabledAndRevokedOnTheSameClientIncludingAPooledConnection() {
        settings.setEnabledForUrl("https://localhost", false)
        val server = server()
        server.enqueue(MockResponse().setBody("ok"))
        val client = client()
        val url = server.url("/").toString()
        rejected(client, url)
        settings.setEnabledForUrl(url, true)
        client.newCall(Request.Builder().url(url).build()).execute().use { assertEquals("ok", it.body!!.string()) }
        assertEquals(1, server.requestCount)
        settings.setEnabledForUrl(url, false)
        rejected(client, url)
        assertEquals("Revoked request must not send HTTP headers", 1, server.requestCount)
    }

    @Test fun redirectDoesNotExtendConsentToAnotherHostname() {
        val server = server()
        settings.setEnabledForUrl("https://localhost", true)
        settings.setEnabledForUrl("https://127.0.0.1", false)
        server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", server.url("/other").newBuilder().host("127.0.0.1").build()))
        rejected(client(), server.url("/").toString())
        assertEquals(1, server.requestCount)
    }

    @Test fun installedIssuerDoesNotPermitExpiredCertificates() {
        val server = server(expired = true)
        settings.setEnabledForUrl("https://localhost", true)
        rejected(client(), server.url("/").toString())
        assertEquals(0, server.requestCount)
    }

    @Test fun installedIssuerDoesNotPermitTheWrongHostname() {
        val server = server(wrongHost = true)
        settings.setEnabledForUrl("https://localhost", true)
        rejected(client(), server.url("/").toString())
        assertEquals(0, server.requestCount)
    }
}
