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
    private val prefs: SharedPreferences = try {
        openPrefs(context)
    } catch (e: Exception) {
        // The Keystore master key no longer matches the stored data (restored backup, key
        // invalidated by a lock-screen change or OS update, ...). It is unrecoverable; wipe the
        // store and start fresh, which just signs the user out, instead of crashing at launch.
        context.deleteSharedPreferences(PREFS_NAME)
        context.deleteSharedPreferences(KEYSET_PREFS_NAME)
        runCatching {
            java.security.KeyStore.getInstance("AndroidKeyStore")
                .apply { load(null) }
                .deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
        }
        openPrefs(context)
    }

    private fun openPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
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
        private const val PREFS_NAME = "bookorbit_secure_prefs"
        // Tink keyset store used by EncryptedSharedPreferences (the library's default file name).
        private const val KEYSET_PREFS_NAME = "__androidx_security_crypto_encrypted_prefs__"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_USER = "user"
        const val KEY_COOKIES = "auth_cookies"
        /** The server that owns the original on-device database; other servers get their own. */
        const val KEY_DATA_OWNER = "data_owner"
    }
}
