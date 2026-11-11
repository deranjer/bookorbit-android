package com.bookorbit.feature.player

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.FileNotFoundException

/** Read-only, URI-granted cover art for Android Auto, system controls and other media clients. */
class AutoArtworkProvider : ContentProvider() {
    @EntryPoint @InstallIn(SingletonComponent::class)
    interface Dependencies { fun artwork(): AutoArtwork }

    override fun onCreate() = true
    override fun getType(uri: Uri) = "image/*"
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor = try {
        EntryPointAccessors.fromApplication(requireNotNull(context), Dependencies::class.java).artwork().open(uri, mode)
    } catch (e: Exception) {
        throw FileNotFoundException("Artwork unavailable").apply { initCause(e) }
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException("Read only")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = throw UnsupportedOperationException("Read only")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = throw UnsupportedOperationException("Read only")
}
