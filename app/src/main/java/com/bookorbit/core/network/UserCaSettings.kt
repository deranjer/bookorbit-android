package com.bookorbit.core.network

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** Consent applies only to this HTTPS hostname, including its other ports; never to redirects. */
@Singleton
class UserCaSettings @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("bookorbit_user_ca_trust", Context.MODE_PRIVATE)
    private val _hosts = MutableStateFlow(prefs.getStringSet("hosts", emptySet())!!.toSet())
    val hosts = _hosts.asStateFlow()

    fun isEnabledForUrl(url: String?): Boolean = httpsHost(url)?.let(::isEnabledForHost) == true
    fun isEnabledForHost(host: String): Boolean = host.lowercase() in _hosts.value

    /** Call off the UI thread. Return only once consent is persisted and visible to all clients. */
    @Synchronized
    fun setEnabledForUrl(url: String?, enabled: Boolean) {
        val host = httpsHost(url) ?: return
        val updated = if (enabled) _hosts.value + host else _hosts.value - host
        check(prefs.edit().putStringSet("hosts", updated).commit()) { "Cannot save certificate trust preference" }
        _hosts.value = updated
    }

    companion object {
        fun httpsHost(url: String?): String? = url?.trim()?.toHttpUrlOrNull()?.takeIf { it.isHttps }?.host
    }
}
