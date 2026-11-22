package com.bookorbit.core.network

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.spec.ECGenParameterSpec
import java.security.interfaces.ECPublicKey
import javax.inject.Inject
import javax.inject.Singleton

internal data class AttestedKey(val keyId: String, val certificateChain: List<String>)

internal interface AttestKeys {
    fun generate(alias: String, challenge: String): AttestedKey
    fun sign(alias: String, clientData: ByteArray): String
    fun delete(alias: String)
}

/** Never substitutes a software/exportable key when hardware attestation is unavailable. */
@Singleton
internal class AndroidAttestKeys @Inject constructor() : AttestKeys {
    private fun store() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    override fun generate(alias: String, challenge: String): AttestedKey {
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
        generator.initialize(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            // Server compares the UTF-8 string, not base64-decoded challenge bytes.
            .setAttestationChallenge(challenge.toByteArray(Charsets.UTF_8)).build())
        val pair = generator.generateKeyPair()
        val info = KeyFactory.getInstance(pair.private.algorithm, "AndroidKeyStore").getKeySpec(pair.private, KeyInfo::class.java)
        @Suppress("DEPRECATION")
        check(info.isInsideSecureHardware) { "Hardware-backed Android key unavailable" }
        val chain = checkNotNull(store().getCertificateChain(alias))
        check(chain.size in 2..10) { "Hardware attestation certificate chain unavailable" }
        return AttestedKey(AndroidAttestProtocol.keyId(pair.public as ECPublicKey), chain.map { AndroidAttestProtocol.base64(it.encoded) })
    }

    override fun sign(alias: String, clientData: ByteArray): String {
        val key = store().getKey(alias, null) as? java.security.PrivateKey ?: error("Attestation key unavailable")
        return AndroidAttestProtocol.sign(key, clientData)
    }

    override fun delete(alias: String) { store().deleteEntry(alias) }
}
