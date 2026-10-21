package com.bookorbit.core.auth

import android.content.ContextWrapper
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.KeyStore
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Uses a separate key, files and database marker; never resets the signed-in app's credentials. */
@RunWith(AndroidJUnit4::class)
class SecureStorageRecoveryTest {
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "recovery_test_${System.nanoTime()}"
    private val alias = "$name.key"
    private val ownershipName = "$name.owner"
    private val databaseMarker = File(target.cacheDir, "$name.db")
    private val context = object : ContextWrapper(target) {
        override fun getDatabasePath(name: String): File = databaseMarker
    }

    private fun open() = SecureStorage(context, name, alias, ownershipName)

    @After fun cleanup() {
        context.deleteSharedPreferences(name)
        context.deleteSharedPreferences(ownershipName)
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
        databaseMarker.delete()
    }

    @Test fun lostKeystoreKeyResetsCredentialsButKeepsDatabaseOwner() {
        val store = open()
        store.putString(SecureStorage.KEY_ACCESS_TOKEN, "test-token")
        store.putString(SecureStorage.KEY_DATA_OWNER, "https://original.example")
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
        val recovered = open()
        assertNull(recovered.getString(SecureStorage.KEY_ACCESS_TOKEN))
        assertEquals("https://original.example", recovered.getString(SecureStorage.KEY_DATA_OWNER))
        assertNotEquals("", DataScope.of(recovered.getString(SecureStorage.KEY_DATA_OWNER), "https://other.example"))
        recovered.putString(SecureStorage.KEY_ACCESS_TOKEN, "replacement")
        assertEquals("replacement", open().getString(SecureStorage.KEY_ACCESS_TOKEN))
    }

    @Test fun legacyOwnerMigratesOutOfEncryptedPreferences() {
        val masterKey = MasterKey.Builder(context, alias).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        val legacy = EncryptedSharedPreferences.create(context, name, masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
        assertTrue(legacy.edit().putString(SecureStorage.KEY_DATA_OWNER, "https://original.example").commit())
        assertEquals("https://original.example", open().getString(SecureStorage.KEY_DATA_OWNER))
        context.deleteSharedPreferences(name)
        assertEquals("https://original.example", open().getString(SecureStorage.KEY_DATA_OWNER))
    }

    @Test fun restoredLegacyDatabaseWithoutOwnerIsNeverClaimedByANewServer() {
        databaseMarker.writeText("restored database marker")
        val owner = open().getString(SecureStorage.KEY_DATA_OWNER)
        assertEquals(SecureStorage.UNKNOWN_DATA_OWNER, owner)
        assertNotEquals("", DataScope.of(owner, "https://new.example"))
        assertTrue(databaseMarker.exists())
    }
}
