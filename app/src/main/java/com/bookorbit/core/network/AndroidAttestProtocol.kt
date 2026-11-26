package com.bookorbit.core.network

import okhttp3.HttpUrl
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.util.Base64
import java.util.Locale

/** Wire format of BookOrbit 3.9.0, independent of Android/Keystore for contract tests. */
internal object AndroidAttestProtocol {
    fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
    fun base64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    fun nonce(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(24).also { SecureRandom().nextBytes(it) })

    fun keyId(key: ECPublicKey): String {
        require(key.params.curve.field.fieldSize == 256)
        fun coordinate(value: java.math.BigInteger): ByteArray {
            require(value.signum() >= 0)
            val unsigned = value.toByteArray().let { if (it.size == 33 && it[0] == 0.toByte()) it.copyOfRange(1, 33) else it }
            require(unsigned.size <= 32)
            return ByteArray(32 - unsigned.size) + unsigned
        }
        // The server hashes SEC1 uncompressed point bytes, NOT SubjectPublicKeyInfo.
        return base64(sha256(byteArrayOf(4) + coordinate(key.w.affineX) + coordinate(key.w.affineY)))
    }

    fun clientData(method: String, url: HttpUrl, timestamp: String, body: ByteArray, nonce: String): ByteArray {
        require(timestamp.matches(Regex("[0-9]{1,16}")) && nonce.matches(Regex("[A-Za-z0-9_-]{16,64}")))
        val pathAndQuery = url.encodedPath + (url.encodedQuery?.let { "?$it" } ?: "")
        val at = pathAndQuery.indexOf("/api/v1/")
        require(at >= 0)
        val bodyHash = sha256(body).joinToString("") { "%02x".format(it) }
        return listOf("bookorbit-android-attest-v1", method.uppercase(Locale.ROOT), pathAndQuery.substring(at),
            timestamp, bodyHash, nonce).joinToString("\n").toByteArray(Charsets.UTF_8)
    }

    fun sign(key: java.security.PrivateKey, bytes: ByteArray): String = base64(Signature.getInstance("SHA256withECDSA").run {
        initSign(key); update(bytes); sign()
    })
}
