package com.bookorbit.feature.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.bookorbit.core.auth.SessionManager
import com.bookorbit.core.network.ImageClient
import com.bookorbit.core.network.ImageUrls
import com.bookorbit.core.network.TokenAuthenticator
import com.bookorbit.core.storage.LocalRef
import com.bookorbit.core.storage.openParcelFileDescriptor
import com.bookorbit.feature.downloads.DownloadsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/** Authenticated image fetching stays on the phone; media clients receive scoped local URIs. */
@Singleton
class AutoArtwork @Inject constructor(
    @ApplicationContext private val context: Context,
    private val session: SessionManager,
    private val downloads: DownloadsRepository,
    private val imageUrls: ImageUrls,
    @ImageClient client: OkHttpClient,
    authenticator: TokenAuthenticator,
) {
    private val client = client.newBuilder().authenticator(authenticator)
        .callTimeout(5, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
    private val locks = ConcurrentHashMap<String, Any>()
    private fun scope(): String = MessageDigest.getInstance("SHA-256")
        .digest("${session.serverUrl}|${session.currentUser?.id}".toByteArray())
        .joinToString("") { "%02x".format(it) }.take(24)

    private fun root(): Uri = Uri.Builder().scheme("content").authority("${context.packageName}.artwork")
        .appendPath(scope()).build()

    fun uri(bookId: Int): Uri = root().buildUpon().appendPath(bookId.toString()).build()

    /** The provider is not exported. Only connected media clients get read access to this scope. */
    fun grantTo(packageName: String) {
        if (packageName == context.packageName) return
        runCatching {
            context.grantUriPermission(packageName, root(),
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        }
    }

    fun open(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r" || session.dataScopeChanged() || uri.authority != "${context.packageName}.artwork" ||
            uri.pathSegments.size != 2 || uri.pathSegments[0] != scope()) {
            throw FileNotFoundException("Invalid artwork request")
        }
        val id = uri.pathSegments[1].toIntOrNull()?.takeIf { it > 0 }
            ?: throw FileNotFoundException("Invalid book")
        return runBlocking(Dispatchers.IO) {
            withTimeout(6_000) {
                val local = downloads.coverPath(id)
                if (local != null) {
                    runCatching { LocalRef.parse(local).openParcelFileDescriptor(context, "r") }.getOrNull()
                        ?.let {
                            if (uri.pathSegments[0] != scope()) { it.close(); throw FileNotFoundException("Account changed") }
                            return@withTimeout it
                        }
                }
                val key = "${scope()}-$id"
                synchronized(locks.getOrPut(key) { Any() }) {
                    val folder = File(context.cacheDir, "auto-artwork").apply { mkdirs() }
                    val cached = File(folder, "$key.jpg")
                    if (!cached.exists()) {
                        if (session.accessToken == null) throw FileNotFoundException("Sign in to load artwork")
                        // The image client retains configured CA trust and refreshes expired tokens.
                        val temporary = File.createTempFile("cover-", ".tmp", folder)
                        try {
                            apiCover(id, temporary)
                            if (!temporary.renameTo(cached)) throw FileNotFoundException("Unable to cache artwork")
                        } finally { temporary.delete() }
                        folder.listFiles()?.filter { it.extension == "jpg" && it != cached }
                            ?.sortedByDescending { it.lastModified() }?.drop(127)?.forEach { it.delete() }
                    }
                    if (uri.pathSegments[0] != scope()) throw FileNotFoundException("Account changed")
                    ParcelFileDescriptor.open(cached, ParcelFileDescriptor.MODE_READ_ONLY)
                }
            }
        }
    }

    private fun apiCover(id: Int, destination: File) {
        client.newCall(Request.Builder().url(imageUrls.thumbnail(id)).build()).execute().use { response ->
            if (!response.isSuccessful) throw FileNotFoundException("Artwork unavailable")
            val body = response.body ?: throw FileNotFoundException("Empty artwork")
            if (body.contentType()?.type != "image") throw FileNotFoundException("Invalid artwork")
            body.byteStream().use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > 5 * 1024 * 1024) throw FileNotFoundException("Artwork too large")
                        output.write(buffer, 0, count)
                    }
                    if (total == 0) throw FileNotFoundException("Empty artwork")
                }
            }
        }
    }
}
