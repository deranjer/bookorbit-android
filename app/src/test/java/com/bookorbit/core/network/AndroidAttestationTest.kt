package com.bookorbit.core.network

import com.bookorbit.core.auth.SessionManager
import com.bookorbit.core.model.AuthUser
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

class AndroidAttestationTest {
    private val json = Json { ignoreUnknownKeys = true }
    private class Keys : AttestKeys {
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        var challenge: String? = null
        var generated: String? = null
        val deleted = mutableListOf<String>()
        override fun generate(alias: String, challenge: String): AttestedKey {
            this.challenge = challenge; generated = alias
            return AttestedKey(AndroidAttestProtocol.keyId(pair.public as ECPublicKey), listOf("synthetic-leaf", "synthetic-root"))
        }
        override fun sign(alias: String, clientData: ByteArray): String = AndroidAttestProtocol.sign(pair.private, clientData)
        override fun delete(alias: String) { deleted.add(alias) }
    }
    private class Harness {
        val keys = Keys()
        val storage = ConcurrentHashMap<String, String>()
        val requests = mutableListOf<Request>()
        var server = "https://books.test/prefix"
        var userId = 3
        var registrationCode = 200
        var receiptId: String? = null
        var environment = "production"
        var afterChallenge: () -> Unit = {}
        val challenge = "synthetic-challenge-abcdefghijklmnop"
        val session = mockk<SessionManager>().also { session ->
            every { session.serverUrl } answers { server }
            every { session.currentUser } answers { mockk<AuthUser>().also { every { it.id } returns userId } }
            every { session.accessToken } returns "synthetic-access-token"
        }
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request(); requests.add(request)
            val challengeRequest = request.url.encodedPath.endsWith("/challenges")
            val body = if (challengeRequest) {
                afterChallenge()
                """{"challenge":"$challenge","expiresAt":"2099-01-01T00:00:00Z"}"""
            } else """{"keyId":"${receiptId ?: AndroidAttestProtocol.keyId(keys.pair.public as ECPublicKey)}","environment":"$environment"}"""
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(if (challengeRequest) 200 else registrationCode)
                .message("Test response").body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val attest = AndroidAttestation(session, keys, Json { ignoreUnknownKeys = true }, client,
            { storage[it] }, { k, v -> if (v == null) storage.remove(k) else storage[k] = v })
        fun request(body: RequestBody? = SingleAttemptJsonBody("{\"excerpt\":\"Я читаю\"}".toByteArray(), "application/json".toMediaType())) =
            Request.Builder().url("$server/api/v1/mobile/reading-help?exact=7").header("Authorization", "Bearer synthetic-access-token")
                .method(if (body == null) "GET" else "POST", body).build()
    }

    @Test fun `explicit enrollment binds UTF8 challenge certificates and key ID then signs exact bytes`() = runBlocking {
        val h = Harness()
        assertFalse(h.attest.isEnrolled())
        assertNull(h.attest.sign(h.request()).header("X-BookOrbit-Attest-Key"))
        assertEquals(0, h.requests.size)
        h.attest.enroll()
        assertTrue(h.attest.isEnrolled())
        assertEquals(h.challenge, h.keys.challenge)
        assertEquals(listOf("/prefix/api/v1/app-attest/challenges", "/prefix/api/v1/app-attest/android-keys"), h.requests.map { it.url.encodedPath })
        assertTrue(h.requests.all { it.header("Authorization") == "Bearer synthetic-access-token" && it.body!!.isOneShot() })
        val registrationBytes = okio.Buffer().also { h.requests.last().body!!.writeTo(it) }.readUtf8()
        val registered = json.parseToJsonElement(registrationBytes).jsonObject
        assertEquals(h.challenge, registered["challenge"]!!.jsonPrimitive.content)
        assertEquals("synthetic-leaf", registered["certificateChain"]!!.jsonArray.first().jsonPrimitive.content)
        val original = h.request()
        val signed = h.attest.sign(original)
        assertSame(original.body, signed.body)
        val expected = AndroidAttestProtocol.clientData(signed.method, signed.url, signed.header("X-BookOrbit-Attest-Time")!!,
            (original.body as SingleAttemptJsonBody).bytes, signed.header("X-BookOrbit-Attest-Nonce")!!)
        assertTrue(Signature.getInstance("SHA256withECDSA").run { initVerify(h.keys.pair.public); update(expected)
            verify(Base64.getDecoder().decode(signed.header("X-BookOrbit-Attest-Assertion"))) })
        assertNotEquals(signed.header("X-BookOrbit-Attest-Nonce"), h.attest.sign(original).header("X-BookOrbit-Attest-Nonce"))
        assertEquals(2, h.requests.size) // Signing never enrolls or calls a provider.
    }

    @Test fun `HTTP enrollment is refused before certificate generation or any request`() = runBlocking {
        val h = Harness(); h.server = "http://books.test"
        assertTrue(runCatching { h.attest.enroll() }.isFailure)
        assertNull(h.keys.generated); assertTrue(h.requests.isEmpty()); assertTrue(h.storage.isEmpty())
    }

    @Test fun `rejected registration cleans new key and never enables signing`() = runBlocking {
        val h = Harness(); h.registrationCode = 400
        assertTrue(runCatching { h.attest.enroll() }.isFailure)
        assertFalse(h.attest.isEnrolled()); assertEquals(listOf(h.keys.generated), h.keys.deleted)
        assertEquals(2, h.requests.size)
    }

    @Test fun `development environment or wrong key receipt cannot enable signing`() = runBlocking {
        for (development in listOf(true, false)) {
            val h = Harness()
            if (development) h.environment = "development" else h.receiptId = "not-the-attested-key"
            assertTrue(runCatching { h.attest.enroll() }.isFailure)
            assertFalse(h.attest.isEnrolled()); assertEquals(listOf(h.keys.generated), h.keys.deleted)
        }
    }

    @Test fun `account changes during enrollment stop before generating a key`() = runBlocking {
        val h = Harness(); h.afterChallenge = { h.userId = 99 }
        assertTrue(runCatching { h.attest.enroll() }.isFailure)
        assertNull(h.keys.generated); assertTrue(h.storage.isEmpty()); assertEquals(1, h.requests.size)
    }

    @Test fun `registered key never crosses account or server or proxy mount boundary`() = runBlocking {
        val h = Harness(); h.attest.enroll()
        h.userId = 99
        assertNull(h.attest.sign(h.request()).header("X-BookOrbit-Attest-Key"))
        h.userId = 3; h.server = "https://elsewhere.test/prefix"
        assertNull(h.attest.sign(h.request()).header("X-BookOrbit-Attest-Key"))
        h.server = "https://books.test/other"
        assertFalse(h.attest.isEnrolled())
        h.server = "https://books.test/prefix"
        for (url in listOf("https://elsewhere.test/prefix/api/v1/mobile/reading-help", "https://books.test/prefix-other/api/v1/mobile/reading-help", "http://books.test/prefix/api/v1/mobile/reading-help")) {
            assertTrue(runCatching { h.attest.sign(h.request().newBuilder().url(url).build()) }.exceptionOrNull() is IOException)
        }
    }

    @Test fun `one-shot body must be materialized before signing but bodyless GET works`() = runBlocking {
        val h = Harness(); h.attest.enroll()
        assertTrue(runCatching { h.attest.sign(h.request("{}".toRequestBody("application/json".toMediaType()))) }.isFailure)
        assertNotNull(h.attest.sign(h.request(null)).header("X-BookOrbit-Attest-Assertion"))
        assertTrue(runCatching { h.attest.sign(h.request().newBuilder().header("Authorization", "Bearer old-account").build()) }.isFailure)
    }

    @Test fun `failed re-enrollment preserves old registration and disable removes local key`() = runBlocking {
        val h = Harness(); h.attest.enroll()
        val previous = h.keys.generated
        val originalStorage = h.storage.toMap()
        h.registrationCode = 403
        assertTrue(runCatching { h.attest.enroll() }.isFailure)
        assertEquals(originalStorage, h.storage)
        assertFalse(h.keys.deleted.contains(previous))
        h.attest.disable()
        assertTrue(h.storage.isEmpty()); assertTrue(h.keys.deleted.contains(previous))
        assertNull(h.attest.sign(h.request()).header("X-BookOrbit-Attest-Key"))
        assertEquals(4, h.requests.size) // Disabling makes no network call or policy changes.
    }
}
