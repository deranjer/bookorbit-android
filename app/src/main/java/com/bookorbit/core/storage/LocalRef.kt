package com.bookorbit.core.storage

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.net.toFile
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream

/**
 * A downloaded book file's on-disk location, dispatching on how [parse] reads the stored path
 * string: a `content://` SAF document, a `file://` URI (internal storage, written going forward),
 * or a legacy bare absolute path (rows written before SAF support existed). This is the single
 * place every downloads consumer goes through instead of constructing [File]/[Uri] directly, so
 * SAF and internal storage stay interchangeable everywhere a [DownloadedFile.localPath] or
 * [DownloadEntity.coverLocalPath][com.bookorbit.core.db.DownloadEntity.coverLocalPath] is read.
 */
sealed class LocalRef {
    data class PlainFile(val file: File) : LocalRef()
    data class ContentDoc(val uri: Uri) : LocalRef()

    companion object {
        fun parse(stored: String): LocalRef = when {
            stored.startsWith("content://") -> ContentDoc(Uri.parse(stored))
            stored.startsWith("file://") -> PlainFile(Uri.parse(stored).toFile())
            else -> PlainFile(File(stored))
        }
    }
}

/** A [Uri] pointing at this ref, suitable for Media3/Coil/artwork — no I/O, never throws. */
fun LocalRef.toUri(): Uri = when (this) {
    is LocalRef.PlainFile -> Uri.fromFile(file)
    is LocalRef.ContentDoc -> uri
}

fun LocalRef.exists(context: Context): Boolean = when (this) {
    is LocalRef.PlainFile -> file.exists()
    is LocalRef.ContentDoc -> DocumentFile.fromSingleUri(context, uri)?.exists() == true
}

fun LocalRef.length(context: Context): Long = when (this) {
    is LocalRef.PlainFile -> file.length()
    is LocalRef.ContentDoc -> DocumentFile.fromSingleUri(context, uri)?.length() ?: 0L
}

fun LocalRef.openInputStream(context: Context): InputStream = when (this) {
    is LocalRef.PlainFile -> file.inputStream()
    is LocalRef.ContentDoc -> context.contentResolver.openInputStream(uri)
        ?: throw FileNotFoundException("Unable to open $uri")
}

fun LocalRef.openParcelFileDescriptor(context: Context, mode: String = "r"): ParcelFileDescriptor = when (this) {
    is LocalRef.PlainFile -> ParcelFileDescriptor.open(
        file,
        if (mode == "r") ParcelFileDescriptor.MODE_READ_ONLY else ParcelFileDescriptor.MODE_READ_WRITE,
    )
    is LocalRef.ContentDoc -> context.contentResolver.openFileDescriptor(uri, mode)
        ?: throw FileNotFoundException("Unable to open $uri")
}

/** Best-effort delete; never throws. Returns whether the underlying file/document was removed. */
fun LocalRef.delete(context: Context): Boolean = when (this) {
    is LocalRef.PlainFile -> runCatching { file.delete() }.getOrDefault(false)
    is LocalRef.ContentDoc -> runCatching { DocumentFile.fromSingleUri(context, uri)?.delete() == true }.getOrDefault(false)
}
