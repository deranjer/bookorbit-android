package com.bookorbit.core.network

import android.net.http.X509TrustManagerExtensions
import okhttp3.OkHttpClient
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/** System trust by default, with user roots permitted only for explicitly opted-in HTTPS hosts. */
@Singleton
class UserCaTrust internal constructor(
    settings: UserCaSettings,
    private val userTrustManager: () -> X509TrustManager?,
) {
    @Inject constructor(settings: UserCaSettings) : this(settings, ::loadUserTrustManager)

    private val system = trustManagerFor(null)
    private val systemExtensions = X509TrustManagerExtensions(system)
    val trustManager = HostScopedTrustManager(
        system = system,
        systemCheck = { chain, authType, host -> systemExtensions.checkServerTrusted(chain, authType, host) },
        userCheck = { chain, authType, host ->
            val user = try {
                userTrustManager()
            } catch (e: GeneralSecurityException) {
                throw CertificateException("Cannot read user-installed CA certificates", e)
            } catch (e: IOException) {
                throw CertificateException("Cannot read user-installed CA certificates", e)
            } ?: throw CertificateException("No user-installed CA certificates")
            X509TrustManagerExtensions(user).checkServerTrusted(chain, authType, host)
        },
        enabled = settings::isEnabledForHost,
    )

    private val sslSocketFactory = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf(trustManager), null)
    }.socketFactory

    fun applyTo(builder: OkHttpClient.Builder): OkHttpClient.Builder = builder
        .sslSocketFactory(sslSocketFactory, trustManager)
        .addNetworkInterceptor { chain ->
            // Recheck BEFORE sending HTTP headers on every exchange, including redirected requests,
            // pooled connections and resumed TLS sessions. Disabling consent blocks the next request;
            // a response already in progress (e.g. an audio stream) is not retroactively cancelled.
            if (chain.request().url.isHttps) {
                val certificates: Array<X509Certificate> = chain.connection()?.handshake()?.peerCertificates
                    ?.filterIsInstance<X509Certificate>()?.toTypedArray() ?: emptyArray()
                try {
                    trustManager.checkServerTrusted(certificates, "RSA", chain.request().url.host)
                } catch (e: CertificateException) {
                    throw SSLPeerUnverifiedException("Certificate not trusted for ${chain.request().url.host}").apply { initCause(e) }
                }
            }
            chain.proceed(chain.request())
        }

    companion object {
        internal fun trustManagerFor(store: KeyStore?): X509TrustManager =
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                .apply { init(store) }.trustManagers.filterIsInstance<X509TrustManager>().first()

        private fun loadUserTrustManager(): X509TrustManager? {
            val androidStore = KeyStore.getInstance("AndroidCAStore").apply { load(null) }
            val userStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null) }
            for (alias in androidStore.aliases()) {
                if (alias.startsWith("user:")) {
                    androidStore.getCertificate(alias)?.let { userStore.setCertificateEntry(alias, it) }
                }
            }
            return if (userStore.size() == 0) null else trustManagerFor(userStore)
        }
    }
}
