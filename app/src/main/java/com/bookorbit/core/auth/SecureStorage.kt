package com.bookorbit.core.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.inject.Inject
import javax.inject.Singleton

/** Credentials are device-encrypted; the non-secret database ownership mapping is backed up. */
@Singleton
class SecureStorage internal constructor(
    private val context: Context,
    private val prefsName: String,
    private val masterKeyAlias: String,
    ownershipPrefsName: String,
) {
    @Inject constructor(@ApplicationContext context: Context) : this(
        context, "bookorbit_secure_prefs", MasterKey.DEFAULT_MASTER_KEY_ALIAS, "bookorbit_data_ownership",
    )

    private val ownership = context.getSharedPreferences(ownershipPrefsName, Context.MODE_PRIVATE)

    private val prefs: SharedPreferences = try {
        openAndValidate()
    } catch (e: Exception) {
        // Do not erase credentials for unrelated programming/runtime errors.
        if (e !is GeneralSecurityException && e !is IOException && e !is SecurityException) throw e
        Log.w("SecureStorage", "Encrypted credentials cannot be read; resetting sign-in", e)
        // Both Tink keysets are entries INSIDE this file, not a second preference file.
        // Drain pending apply() writes first: deleting the file alone lets an old write recreate
        // it with the old keysets after we have replaced the Keystore key.
        if (!context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().clear().commit()) {
            throw IOException("Cannot clear encrypted preferences", e)
        }
        if (!context.deleteSharedPreferences(prefsName)) throw IOException("Cannot reset encrypted preferences", e)
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(masterKeyAlias)
        openAndValidate() // One retry only; surface persistent storage/Keystore failures.
    }

    init {
        if (!ownership.contains(KEY_DATA_OWNER)) {
            // Migrate while the old encrypted store is still readable. If restored legacy data
            // has lost its mapping, quarantine it rather than assigning it to the next server.
            val owner = prefs.getString(KEY_DATA_OWNER, null)
                ?: prefs.getString(KEY_SERVER_URL, null)?.let(DataScope::key)
                ?: UNKNOWN_DATA_OWNER.takeIf { context.getDatabasePath("bookorbit.db").exists() }
            if (owner != null) saveOwner(owner)
        }
    }

    private fun openAndValidate(): SharedPreferences {
        val masterKey = MasterKey.Builder(context, masterKeyAlias)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            prefsName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        ).also { it.all } // Detect corrupt encrypted values as well as undecryptable keysets.
    }

    private fun saveOwner(value: String?) {
        check(ownership.edit().putString(KEY_DATA_OWNER, value).commit()) { "Cannot persist database ownership" }
    }

    fun getString(key: String): String? =
        if (key == KEY_DATA_OWNER) ownership.getString(key, null) else prefs.getString(key, null)

    fun putString(key: String, value: String?) {
        if (key == KEY_DATA_OWNER) saveOwner(value)
        else prefs.edit().putString(key, value).apply()
    }

    fun remove(key: String) {
        if (key == KEY_DATA_OWNER) saveOwner(null) else prefs.edit().remove(key).apply()
    }

    companion object {
        internal const val UNKNOWN_DATA_OWNER = "unknown-restored-owner"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_USER = "user"
        const val KEY_COOKIES = "auth_cookies"
        const val KEY_DATA_OWNER = "data_owner"
    }
}
