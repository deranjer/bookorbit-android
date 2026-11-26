package com.bookorbit.feature.reader

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.FileDataSource
import com.bookorbit.core.auth.SessionManager
import com.bookorbit.core.auth.SessionOwner
import com.bookorbit.core.storage.LocalRef
import com.bookorbit.core.storage.openInputStream
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** A verified private snapshot keeps SAF replacement/removal from changing an active edition. */
@Singleton
class LocalNativeNarration @Inject constructor(
    @ApplicationContext private val context: Context, private val session: SessionManager,
) {
    private val directory by lazy {
        File(context.cacheDir, "native-narration").apply {
            mkdirs()
            // Process-private singleton: files from the previous process have no active readers.
            listFiles()?.filter { it.name.startsWith("epub-") || it.name.startsWith("audio-") }?.forEach { it.delete() }
        }
    }
    internal suspend fun open(ref: LocalRef, bookId: Int, owner: ReadAlongOwner, account: SessionOwner): NativeNarrationSource {
        var pendingSnapshot: File? = null
        try {
            return withContext(Dispatchers.IO) {
                require(session.owns(account) && owner.server == account.serverUrl && owner.userId == account.userId)
                val snapshot = File.createTempFile("epub-", ".zip", directory)
                pendingSnapshot = snapshot
                val digest = MessageDigest.getInstance("SHA-256")
                ref.openInputStream(context).use { input -> snapshot.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= 2L * 1024 * 1024 * 1024) { "EPUB exceeds narration snapshot bound" }
                        if (!session.owns(account)) throw IOException("Narration account changed")
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                } }
                val fingerprint = digest.digest().joinToString("") { "%02x".format(it) }
                require(fingerprint == owner.fingerprint) { "Local EPUB revision changed" }
                val archive = LocalNarrationArchive(snapshot)
                val timeline = archive.timeline(bookId, owner.fileId)
                LocalNarrationPlan(timeline, account, session, archive, directory, snapshot)
            }
        } catch (failure: Exception) {
            // withContext can discard an already computed result when its caller was cancelled.
            pendingSnapshot?.delete()
            throw failure
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
private class LocalNarrationPlan(
    override val timeline: NativeNarrationTimeline, override val owner: SessionOwner,
    private val session: SessionManager, private val archive: LocalNarrationArchive, private val directory: File,
    private val snapshot: File,
) : NativeNarrationSource {
    private val lock = Any()
    private var closed = false
    private var leases = 0
    override fun close() = synchronized(lock) {
        closed = true
        if (leases == 0) snapshot.delete()
        Unit
    }
    private val uris = timeline.resources.mapIndexed { index, resource ->
        resource.href to "bookorbit-narration://local/$index"
    }.toMap()
    private val hrefs = uris.entries.associate { it.value to it.key }
    override fun resourceUri(href: String) = uris[href] ?: throw IOException("Unknown local audio")
    override fun dataSourceFactory() = DataSource.Factory {
        val delegate = FileDataSource()
        object : DataSource by delegate {
            private var extracted: File? = null
            private var leased = false
            override fun open(dataSpec: DataSpec): Long {
                checkOwner()
                val href = hrefs[dataSpec.uri.toString()]
                    ?: throw IOException("Local narration resource identity changed")
                try {
                    synchronized(lock) {
                        if (closed || leased) throw IOException("Local narration source closed")
                        leases++
                        leased = true
                    }
                    val file = File.createTempFile("audio-", ".bin", directory)
                    extracted = file
                    archive.extract(href, file)
                    checkOwner()
                    return delegate.open(dataSpec.buildUpon().setUri(Uri.fromFile(file)).build())
                } catch (failure: Exception) { close(); throw failure }
            }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                checkOwner()
                return delegate.read(buffer, offset, length)
            }
            override fun close() {
                try { delegate.close() } finally {
                    extracted?.delete(); extracted = null
                    synchronized(lock) {
                        if (leased) { leases--; leased = false }
                        if (closed && leases == 0) snapshot.delete()
                    }
                }
            }
            private fun checkOwner() {
                if (!session.owns(owner)) throw IOException("Narration account changed")
            }
        }
    }
}
