package com.bookorbit.core.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted key/value store backed by [EncryptedSharedPreferences] (Keystore-derived master key).
 * Holds the access token, server URL, serialized user, and persisted auth cookies.
 *
 * Reads/writes are synchronous, which the OkHttp interceptors rely on.
 */
@Singleton
class SecureStorage @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "bookorbit_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun getString(key: String): String? = prefs.getString(key, null)

    fun putString(key: String, value: String?) {
        prefs.edit().apply {
            if (value == null) remove(key) else putString(key, value)
        }.apply()
    }

    fun remove(key: String) = prefs.edit().remove(key).apply()

    companion object {
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_USER = "user"
        const val KEY_COOKIES = "auth_cookies"
    }
}
