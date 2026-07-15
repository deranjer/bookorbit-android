package com.bookorbit.core.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.downloadLocationDataStore by preferencesDataStore("download_location")

/**
 * The user-chosen SAF folder for new downloads (opt-in — null means "use app-private storage",
 * today's behavior, and is the default until a folder is explicitly picked in Settings).
 */
@Singleton
class DownloadLocationStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val treeUriKey = stringPreferencesKey("tree_uri")

    val treeUri: Flow<Uri?> = context.downloadLocationDataStore.data.map { it[treeUriKey]?.let(Uri::parse) }

    /** Persists the pick and takes a durable read/write grant on it, releasing any prior grant. */
    suspend fun setTreeUri(uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        val previous = treeUri.first()
        if (previous != null && previous != uri) releasePermission(previous)
        context.downloadLocationDataStore.edit { it[treeUriKey] = uri.toString() }
    }

    /** Reverts to app-private storage. The previously picked folder itself is left untouched. */
    suspend fun clear() {
        treeUri.first()?.let(::releasePermission)
        context.downloadLocationDataStore.edit { it.remove(treeUriKey) }
    }

    private fun releasePermission(uri: Uri) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    /** Whether [uri] still has a live write grant and resolves to a writable tree right now. */
    fun isAccessible(uri: Uri): Boolean {
        val granted = context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }
        if (!granted) return false
        val doc = DocumentFile.fromTreeUri(context, uri) ?: return false
        return doc.exists() && doc.canWrite()
    }
}
