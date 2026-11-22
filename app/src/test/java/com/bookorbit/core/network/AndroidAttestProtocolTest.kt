package com.bookorbit.core.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Base64

class AndroidAttestProtocolTest {
    private val pair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    @Test fun `canonical bytes retain encoded path query order and raw body hash and omit proxy prefix`() {
        val bytes = AndroidAttestProtocol.clientData("post", "https://books.test/proxy/api/v1/mobile/reading-help?x=a%20b&x=2".toHttpUrl(),
            "1795435200000", "{}".toByteArray(), "abcdefghijklmnop")
        assertEquals("bookorbit-android-attest-v1\nPOST\n/api/v1/mobile/reading-help?x=a%20b&x=2\n1795435200000\n44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a\nabcdefghijklmnop",
            bytes.toString(Charsets.UTF_8))
    }

    @Test fun `key ID hashes padded SEC1 point rather than SPKI`() {
        val key = pair.public as ECPublicKey
        fun hex32(value: java.math.BigInteger) = value.toString(16).padStart(64, '0')
            .chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val expected = AndroidAttestProtocol.base64(AndroidAttestProtocol.sha256(byteArrayOf(4) + hex32(key.w.affineX) + hex32(key.w.affineY)))
        assertEquals(expected, AndroidAttestProtocol.keyId(key))
        assertNotEquals(AndroidAttestProtocol.base64(AndroidAttestProtocol.sha256(key.encoded)), expected)
    }

    @Test fun `DER ECDSA assertion verifies and any body clock URL or nonce mutation fails`() {
        val url = "https://books.test/api/v1/mobile/vision-scan".toHttpUrl()
        fun data(body: String = "{\"image\":\"synthetic\"}", time: String = "1795435200000", nonce: String = "abcdefghijklmnop", path: String = url.toString()) =
            AndroidAttestProtocol.clientData("POST", path.toHttpUrl(), time, body.toByteArray(), nonce)
        val sig = Base64.getDecoder().decode(AndroidAttestProtocol.sign(pair.private, data()))
        fun valid(bytes: ByteArray) = Signature.getInstance("SHA256withECDSA").run { initVerify(pair.public); update(bytes); verify(sig) }
        assertTrue(valid(data()))
        assertFalse(valid(data(body = "{ \"image\":\"synthetic\"}")))
        assertFalse(valid(data(time = "1795435200001")))
        assertFalse(valid(data(nonce = "abcdefghijklmnopq")))
        assertFalse(valid(data(path = "$url?other=1")))
    }

    @Test fun `nonces are fresh bounded URL-safe values`() {
        val nonces = (1..1000).map { AndroidAttestProtocol.nonce() }
        assertEquals(1000, nonces.toSet().size)
        assertTrue(nonces.all { it.matches(Regex("[A-Za-z0-9_-]{16,64}")) })
    }

    @Test fun `bodyless signature uses empty hash not JSON null or empty object`() {
        val data = AndroidAttestProtocol.clientData("get", "https://books.test/api/v1/mobile/books/files/7/story-guide?format=1".toHttpUrl(),
            "1795435200000", byteArrayOf(), "abcdefghijklmnop").toString(Charsets.UTF_8)
        assertTrue(data.contains("\ne3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855\n"))
    }

    @Test fun `export synthetic public contract vector for independent upstream verifier`() {
        val method = "POST"
        val url = "https://books.test/proxy/api/v1/mobile/reading-help?fileId=7&lang=ru".toHttpUrl()
        val timestamp = "1795435200000"
        val nonce = "synthetic_contract_nonce_12345678"
        val body = "{\"excerpt\":\"Только выбранный текст\"}".toByteArray(Charsets.UTF_8)
        val data = AndroidAttestProtocol.clientData(method, url, timestamp, body, nonce)
        val vector = kotlinx.serialization.json.buildJsonObject {
            put("method", kotlinx.serialization.json.JsonPrimitive(method))
            put("url", kotlinx.serialization.json.JsonPrimitive(url.toString()))
            put("timestamp", kotlinx.serialization.json.JsonPrimitive(timestamp))
            put("nonce", kotlinx.serialization.json.JsonPrimitive(nonce))
            put("bodyBase64", kotlinx.serialization.json.JsonPrimitive(AndroidAttestProtocol.base64(body)))
            put("clientDataBase64", kotlinx.serialization.json.JsonPrimitive(AndroidAttestProtocol.base64(data)))
            put("spkiBase64", kotlinx.serialization.json.JsonPrimitive(AndroidAttestProtocol.base64(pair.public.encoded)))
            put("keyId", kotlinx.serialization.json.JsonPrimitive(AndroidAttestProtocol.keyId(pair.public as ECPublicKey)))
            put("signature", kotlinx.serialization.json.JsonPrimitive(AndroidAttestProtocol.sign(pair.private, data)))
        }
        java.io.File("build/reports/android-attestation-contract.json").apply { parentFile?.mkdirs(); writeText(vector.toString()) }
    }
}
