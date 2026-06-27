package com.bookorbit.core.model

/**
 * File-type helpers used to decide which actions a book supports (read / listen / open) and which
 * files to download.
 */
object BookFiles {
    /** Audio container formats handled by the player. */
    val AUDIO_FORMATS = setOf("m4b", "mp3", "m4a", "opus", "ogg", "flac", "aac", "wav")

    /** Formats the in-app foliate reader can render (PDF excluded; azw/txt unreliable). */
    val READER_SUPPORTED = setOf("epub", "mobi", "azw3", "fb2", "cbz", "cbr")

    /** Formats handled by the native PdfRenderer-based reader (a separate engine from foliate). */
    val PDF_FORMATS = setOf("pdf")

    /** Formats that can be handed to an external viewer via the share sheet. */
    val EBOOK_OPENABLE = setOf("epub", "pdf", "cbz", "cbr", "mobi", "azw3", "azw", "fb2", "txt")

    fun audioFiles(book: BookDetail): List<BookFileRef> =
        book.files.filter { it.format != null && it.format.lowercase() in AUDIO_FORMATS }

    fun isAudiobook(book: BookDetail): Boolean = audioFiles(book).isNotEmpty()

    fun isReadableEbook(format: String?): Boolean =
        format != null && format.lowercase() in READER_SUPPORTED

    fun isPdfFormat(format: String?): Boolean =
        format != null && format.lowercase() in PDF_FORMATS

    fun isOpenableEbook(format: String?): Boolean =
        format != null && format.lowercase() in EBOOK_OPENABLE

    /** The file the in-app foliate reader should open: primary if readable, else the first readable file. */
    fun readableFile(book: BookDetail): BookFileRef? {
        val primary = book.files.firstOrNull { it.role == "primary" }
        if (primary != null && isReadableEbook(primary.format)) return primary
        return book.files.firstOrNull { isReadableEbook(it.format) }
    }

    fun isReadable(book: BookDetail): Boolean = readableFile(book) != null

    /** The file the native PDF reader should open: primary if PDF, else the first PDF file. */
    fun pdfFile(book: BookDetail): BookFileRef? {
        val primary = book.files.firstOrNull { it.role == "primary" }
        if (primary != null && isPdfFormat(primary.format)) return primary
        return book.files.firstOrNull { isPdfFormat(it.format) }
    }

    fun isPdf(book: BookDetail): Boolean = pdfFile(book) != null

    /** Which reading engine a book should open in, and the file to feed it. */
    sealed interface ReadingTarget {
        data class Foliate(val file: BookFileRef) : ReadingTarget
        data class Pdf(val file: BookFileRef) : ReadingTarget
        data object None : ReadingTarget
    }

    /**
     * Picks the reading engine for a book. The foliate reader wins when a foliate-readable file
     * exists (it covers the common ebook formats); otherwise a PDF opens in the native PDF reader.
     */
    fun readingTarget(book: BookDetail): ReadingTarget {
        readableFile(book)?.let { return ReadingTarget.Foliate(it) }
        pdfFile(book)?.let { return ReadingTarget.Pdf(it) }
        return ReadingTarget.None
    }

    /** Files to persist for offline use: all audio files, else the primary/first non-audio file. */
    fun downloadableFiles(book: BookDetail): List<BookFileRef> {
        if (isAudiobook(book)) return audioFiles(book)
        val primary = book.files.firstOrNull { it.role == "primary" }
        if (primary != null) return listOf(primary)
        val audioIds = audioFiles(book).map { it.id }.toSet()
        return book.files.firstOrNull { it.id !in audioIds }?.let { listOf(it) } ?: emptyList()
    }
}
