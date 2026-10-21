package com.bookorbit.core.network

import com.bookorbit.core.settings.AppSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Opt-in trust for CAs the user installed on the device (Settings > Security > Encryption &
 * credentials), for servers behind a private CA. Deliberately not done through
 * network_security_config.xml, which would make every user-store CA a trust root for all app traffic
 * with no way to turn it off.
 *
 * The trust manager always tries the system store first and only falls back to the user store when
 * the setting is on, so public certificates are validated exactly as before. The setting is read live,
 * so toggling it needs no client rebuild. Hostname verification is still done by OkHttp.
 */
@Singleton
class UserCaTrust @Inject constructor(settings: AppSettingsStore) {

    @Volatile
    private var enabled: Boolean = runBlocking { settings.trustUserCas.first() }

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            settings.trustUserCas.collect { enabled = it }
        }
    }

    private val system: X509TrustManager = trustManagerFor(null)

    val trustManager: X509TrustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
            system.checkClientTrusted(chain, authType)

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            try {
                system.checkServerTrusted(chain, authType)
            } catch (e: CertificateException) {
                if (!enabled) throw e
                val user = userTrustManager() ?: throw e
                try {
                    user.checkServerTrusted(chain, authType)
                } catch (_: CertificateException) {
                    throw e
                }
            }
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> =
            system.acceptedIssuers + if (enabled) userTrustManager()?.acceptedIssuers.orEmpty() else emptyArray()
    }

    val sslSocketFactory: SSLSocketFactory by lazy {
        SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager), null) }.socketFactory
    }

    fun applyTo(builder: OkHttpClient.Builder): OkHttpClient.Builder =
        builder.sslSocketFactory(sslSocketFactory, trustManager)

    /** Built per failed handshake so certificates installed or removed while running are picked up. */
    private fun userTrustManager(): X509TrustManager? = runCatching {
        val androidStore = KeyStore.getInstance("AndroidCAStore").apply { load(null) }
        val userStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null) }
        for (alias in androidStore.aliases()) {
            if (alias.startsWith("user:")) {
                androidStore.getCertificate(alias)?.let { userStore.setCertificateEntry(alias, it) }
            }
        }
        if (userStore.size() == 0) null else trustManagerFor(userStore)
    }.getOrNull()

    private fun trustManagerFor(store: KeyStore?): X509TrustManager =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(store) }
            .trustManagers.filterIsInstance<X509TrustManager>().first()
}
