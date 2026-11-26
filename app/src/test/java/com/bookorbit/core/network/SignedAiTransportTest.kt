package com.bookorbit.core.network

import com.bookorbit.core.auth.SessionManager
import com.bookorbit.core.model.AuthUser
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/** Actual HTTPS requests, separate from fake enrollment tests; no provider or production server. */
class SignedAiTransportTest {
    private fun withEnrolled(block: (MockWebServer, OkHttpClient, AndroidAttestation, java.security.KeyPair) -> Unit) = runBlocking {
        val cert = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(cert).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        MockWebServer().use { server ->
            server.useHttps(serverTls.sslSocketFactory(), false)
            val pair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
            val keyId = AndroidAttestProtocol.keyId(pair.public as ECPublicKey)
            val keys = object : AttestKeys {
                override fun generate(alias: String, challenge: String) = AttestedKey(keyId, listOf("synthetic-chain"))
                override fun sign(alias: String, clientData: ByteArray) = AndroidAttestProtocol.sign(pair.private, clientData)
                override fun delete(alias: String) = Unit
            }
            val session = mockk<SessionManager>().also {
                every { it.serverUrl } returns server.url("/proxy").toString()
                every { it.accessToken } returns "synthetic-token"
                every { it.currentUser } returns mockk<AuthUser>().also { user -> every { user.id } returns 7 }
                val owner = com.bookorbit.core.auth.SessionOwner(server.url("/proxy").toString(), 7, 1)
                every { it.owner() } returns owner
                every { it.owns(any()) } answers { firstArg<com.bookorbit.core.auth.SessionOwner>() == owner }
            }
            val stored = mutableMapOf<String, String>()
            val base = OkHttpClient.Builder().sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager).build()
            val attest = AndroidAttestation(session, keys, Json, base,
                { stored[it] }, { k, v -> if (v == null) stored.remove(k) else stored[k] = v })
            server.enqueue(MockResponse().setBody("""{"challenge":"synthetic-abcdefghijklmnop","expiresAt":"2099-01-01T00:00:00Z"}"""))
            server.enqueue(MockResponse().setBody("""{"keyId":"$keyId","environment":"production"}"""))
            attest.enroll()
            repeat(2) { server.takeRequest() }
            // Real wiring: URL/auth interceptors first, immutable body next, signer last.
            val main = base.newBuilder().addInterceptor(BaseUrlInterceptor(session)).addInterceptor(AuthInterceptor(session)).build()
            block(server, singleAttemptAiClient(main, attest), attest, pair)
        }
    }

    private fun request() = Request.Builder().url("http://localhost/mobile/reading-help?lang=ru&fileId=7")
        .post("{\"excerpt\":\"Книга — текст\"}".toRequestBody("application/json".toMediaType())).build()

    @Test fun `wire signature verifies raw body after URL rewrite and no signing header leaks into body`() = withEnrolled { server, client, _, pair ->
        server.enqueue(MockResponse().setBody("ok"))
        client.newCall(request()).execute().use { assertEquals(200, it.code) }
        val wire = server.takeRequest()
        assertEquals("/proxy/api/v1/mobile/reading-help?lang=ru&fileId=7", wire.path)
        val bytes = wire.body.readByteArray()
        assertEquals("{\"excerpt\":\"Книга — текст\"}", bytes.toString(Charsets.UTF_8))
        val data = AndroidAttestProtocol.clientData(wire.method!!, wire.requestUrl!!,
            wire.getHeader("X-BookOrbit-Attest-Time")!!, bytes, wire.getHeader("X-BookOrbit-Attest-Nonce")!!)
        assertTrue(Signature.getInstance("SHA256withECDSA").run { initVerify(pair.public); update(data)
            verify(Base64.getDecoder().decode(wire.getHeader("X-BookOrbit-Attest-Assertion"))) })
        assertEquals("Bearer synthetic-token", wire.getHeader("Authorization"))
        assertEquals(3, server.requestCount)
    }

    @Test fun `signed 503 Retry After cannot repeat a paid POST`() = withEnrolled { server, client, _, _ ->
        server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "0"))
        server.enqueue(MockResponse().setBody("must not be requested"))
        client.newCall(request()).execute().use { assertEquals(503, it.code) }
        assertEquals(3, server.requestCount)
    }

    @Test fun `unknown key never reenrolls or retries the original POST`() = withEnrolled { server, client, _, _ ->
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"errorCode":"APP_ATTEST_KEY_UNKNOWN"}"""))
        client.newCall(request()).execute().use { assertEquals(403, it.code) }
        assertEquals(3, server.requestCount)
    }

    @Test fun `disconnect after signed request cannot transparently replay generation`() = withEnrolled { server, client, _, _ ->
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        server.enqueue(MockResponse().setBody("must not be requested"))
        assertTrue(runCatching { client.newCall(request()).execute().close() }.isFailure)
        assertEquals(3, server.requestCount)
    }

    @Test fun `signed request never follows redirect or authenticator retry`() = withEnrolled { server, client, _, _ ->
        for (code in listOf(307, 401)) {
            server.enqueue(MockResponse().setResponseCode(code).setHeader("Location", server.url("/leak")))
            client.newCall(request()).execute().use { assertEquals(code, it.code) }
        }
        assertEquals(4, server.requestCount)
    }

    @Test fun `non AI guarded reads sign but progress and file routes remain untouched`() = withEnrolled { server, ai, attest, _ ->
        // Use the same URL/auth preparation as the fixture, without the AI-only signer/body wrapper.
        val main = ai.newBuilder().apply {
            interceptors().removeAll { it is SingleAttemptAiInterceptor }
            networkInterceptors().removeAll { it is AndroidAttestInterceptor }
        }.addNetworkInterceptor(AttestedReadInterceptor(attest)).build()
        val routes = listOf("dashboard/widgets/reading-streak", "dashboard/widgets/reading-goal", "smart-scopes/7/books",
            "books/files/7/progress", "books/1/serve", "dashboard/widgets/batch")
        for ((index, route) in routes.withIndex()) {
            server.enqueue(MockResponse().setBody("ok"))
            main.newCall(Request.Builder().url("http://localhost/$route").build()).execute().close()
            val wire = server.takeRequest()
            if (index < 3) assertNotNull(wire.getHeader("X-BookOrbit-Attest-Assertion"))
            else assertNull(wire.getHeader("X-BookOrbit-Attest-Assertion"))
        }
    }
}
