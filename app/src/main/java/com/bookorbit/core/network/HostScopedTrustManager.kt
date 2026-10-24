package com.bookorbit.core.network

import androidx.annotation.Keep
import java.net.Socket
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509TrustManager

/** Host-aware at both the TLS handshake and Android/OkHttp chain-cleaning stages. */
class HostScopedTrustManager internal constructor(
    private val system: X509TrustManager,
    private val systemCheck: (Array<X509Certificate>, String, String) -> List<X509Certificate>,
    private val userCheck: (Array<X509Certificate>, String, String) -> List<X509Certificate>,
    private val enabled: (String) -> Boolean,
) : X509ExtendedTrustManager() {
    // Android's X509TrustManagerExtensions discovers this exact public overload reflectively.
    @Keep
    @Throws(CertificateException::class)
    fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, host: String): List<X509Certificate> {
        if (chain.isEmpty()) throw CertificateException("Missing peer certificate chain")
        try {
            return systemCheck(chain, authType, host)
        } catch (systemFailure: CertificateException) {
            if (!enabled(host)) throw systemFailure
            return userCheck(chain, authType, host)
        }
    }

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket) {
        val host = (socket as? SSLSocket)?.handshakeSession?.peerHost
            ?: throw CertificateException("TLS peer hostname unavailable")
        checkServerTrusted(chain, authType, host)
    }

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine) {
        val host = engine.peerHost ?: throw CertificateException("TLS peer hostname unavailable")
        checkServerTrusted(chain, authType, host)
    }

    // A caller without a hostname cannot opt in to user roots.
    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) =
        system.checkServerTrusted(chain, authType)

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
        system.checkClientTrusted(chain, authType)
    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket) =
        system.checkClientTrusted(chain, authType)
    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine) =
        system.checkClientTrusted(chain, authType)
    override fun getAcceptedIssuers(): Array<X509Certificate> = system.acceptedIssuers
}
