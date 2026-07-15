package com.bookorbit.core.storage

import android.webkit.MimeTypeMap
import com.bookorbit.core.model.BookDetail
import com.bookorbit.core.model.BookFileRef

/**
 * Human-readable naming for files written into a user-chosen SAF folder (and, for consistency, the
 * internal-storage fallback too) — as opposed to the old `$bookId/$fileId.ext` scheme, which is
 * meaningless when the folder is browsed in a file manager or opened by an external reader/player.
 */
object DownloadFileNaming {
    private val ILLEGAL_CHARS = Regex("[\\\\/:*?\"<>|]")

    /** Strips filesystem-illegal characters and blank/empty results fall back to a safe default. */
    fun sanitize(raw: String): String =
        raw.replace(ILLEGAL_CHARS, "_").trim().take(120).ifBlank { "untitled" }

    /**
     * Deterministic per-book folder name, re-derived identically at download- and delete-time so no
     * folder Uri needs to be persisted separately. The id suffix keeps it unique even when two books
     * share a title.
     */
    fun bookFolderName(title: String?, bookId: Int): String {
        val base = title?.let(::sanitize)?.takeIf { it.isNotBlank() } ?: "Book"
        return "$base ($bookId)"
    }

    /** Extension for a file: prefers the declared format, then the server filename's extension. */
    fun extensionOf(ref: BookFileRef): String =
        ref.format?.lowercase()?.filter { it.isLetterOrDigit() }?.ifEmpty { null }
            ?: ref.filename?.substringAfterLast('.', "")?.lowercase()?.ifEmpty { null }
            ?: "bin"

    /** Human-readable per-file name inside the per-book folder; prefers the server's real filename. */
    fun fileName(ref: BookFileRef, index: Int, book: BookDetail): String {
        val ext = extensionOf(ref)
        val base = ref.filename?.substringBeforeLast('.')?.let(::sanitize)?.takeIf { it.isNotBlank() }
            ?: sanitize(book.title ?: "book").let { if (index == 0) it else "$it ${index + 1}" }
        return "$base.$ext"
    }

    private val MIME_OVERRIDES = mapOf(
        "m4b" to "audio/mp4",
        "cbz" to "application/vnd.comicbook+zip",
        "cbr" to "application/vnd.comicbook-rar",
        "azw3" to "application/vnd.amazon.ebook",
        "azw" to "application/vnd.amazon.ebook",
        "fb2" to "application/x-fictionbook+xml",
        "mobi" to "application/x-mobipocket-ebook",
        "epub" to "application/epub+zip",
    )

    fun mimeTypeFor(ext: String): String {
        val lower = ext.lowercase()
        return MIME_OVERRIDES[lower]
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(lower)
            ?: "application/octet-stream"
    }
}
