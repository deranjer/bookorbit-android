package com.bookorbit.feature.player

import android.content.ContentResolver
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bookorbit.core.db.DownloadEntity
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileNotFoundException

@RunWith(AndroidJUnit4::class)
class AutoArtworkProviderTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dependencies get() = EntryPointAccessors.fromApplication(context, AutoArtworkProvider.Dependencies::class.java)

    @Test fun downloadedCoverIsReadableThroughContentResolver() = runBlocking {
        val database = EntryPointAccessors.fromApplication(context, AutoTestDependencies::class.java).database()
        val dao = database.downloadDao()
        val id = 2_000_000_001
        val previous = dao.get(id)
        val file = File.createTempFile("auto-art-test", ".jpg", context.cacheDir)
        val bytes = byteArrayOf(1, 2, 3, 4)
        file.writeBytes(bytes)
        try {
            dao.upsert(DownloadEntity(id, "Test", "", "", true, "mp3", 0, 0,
                Uri.fromFile(file).toString(), "{}", "[]", "COMPLETE", 1f))
            val uri = dependencies.artwork().uri(id)
            assertEquals(ContentResolver.SCHEME_CONTENT, uri.scheme)
            assertArrayEquals(bytes, context.contentResolver.openInputStream(uri)!!.use { it.readBytes() })
        } finally {
            if (previous == null) dao.delete(id) else dao.upsert(previous)
            file.delete()
        }
    }

    @Test fun providerRejectsWritesAndForeignScopes() {
        val uri = dependencies.artwork().uri(1)
        for ((request, mode) in listOf(uri to "rw", uri.buildUpon().path("/other-account/1").build() to "r")) {
            try {
                context.contentResolver.openFileDescriptor(request, mode)?.close()
                fail("Artwork request should have been rejected")
            } catch (_: FileNotFoundException) { }
        }
    }

    @Suppress("DEPRECATION")
    @Test fun providerRequiresExplicitReadGrants() {
        val provider = context.packageManager.resolveContentProvider("${context.packageName}.artwork", 0)!!
        assertFalse(provider.exported)
        assertTrue(provider.grantUriPermissions)
    }
}
