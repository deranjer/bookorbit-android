package com.bookorbit.core.model

/**
 * File-type helpers used to decide which actions a book supports (read / listen / open) and which
 * files to download.
 */
object BookFiles {
    /** Audio container formats handled by the player. */
    val AUDIO_FORMATS = setOf("m4b", "mp3", "m4a", "opus", "ogg", "flac", "aac", "wav")

    /** Formats the in-app foliate reader can render (PDF excluded; azw/txt unreliable). */
    val READER_SUPPORTED = setOf("epub", "mobi", "azw3", "fb2", "cbz")

    /**
     * Comic archives foliate can't open (it only understands ZIP). The server renders their pages as
     * images (`/cbz/files/{id}/pages/{n}`), which the native comic reader pages through.
     */
    val COMIC_SERVER_PAGED = setOf("cbr", "cb7")

    /** Formats handled by the native PdfRenderer-based reader (a separate engine from foliate). */
    val PDF_FORMATS = setOf("pdf")

    /** Formats that can be handed to an external viewer via the share sheet. */
    val EBOOK_OPENABLE = setOf("epub", "pdf", "cbz", "cbr", "cb7", "mobi", "azw3", "azw", "fb2", "txt")

    /** Legitimate book content formats, as opposed to incidental files like a folder's cover image. */
    private val CONTENT_FORMATS = AUDIO_FORMATS + READER_SUPPORTED + COMIC_SERVER_PAGED + PDF_FORMATS

    /**
     * Format to show as a card's badge: the primary file if it's a known content format, else the
     * first file that is, else null. Guards against a non-content file (e.g. a stray cover.jpg
     * picked up during ingest) winning the "first file" slot and showing as the book's format.
     */
    fun badgeFormat(files: List<BookFileRef>): String? {
        val primary = files.firstOrNull { it.role == "primary" }?.format?.lowercase()
        if (primary != null && primary in CONTENT_FORMATS) return primary
        return files.firstOrNull { it.format?.lowercase() in CONTENT_FORMATS }?.format?.lowercase()
    }

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

    fun isComicServerPaged(format: String?): Boolean =
        format != null && format.lowercase() in COMIC_SERVER_PAGED

    /** The CBR/CB7 file the native comic reader should open: primary if it is one, else the first. */
    fun comicFile(book: BookDetail): BookFileRef? {
        val primary = book.files.firstOrNull { it.role == "primary" }
        if (primary != null && isComicServerPaged(primary.format)) return primary
        return book.files.firstOrNull { isComicServerPaged(it.format) }
    }

    /** Which reading engine a book should open in, and the file to feed it. */
    sealed interface ReadingTarget {
        data class Foliate(val file: BookFileRef) : ReadingTarget
        data class Pdf(val file: BookFileRef) : ReadingTarget
        data class Comic(val file: BookFileRef) : ReadingTarget
        data object None : ReadingTarget
    }

    /**
     * Picks the reading engine for a book. The foliate reader wins when a foliate-readable file
     * exists (it covers the common ebook formats); then CBR/CB7 comics open in the native comic reader,
     * otherwise a PDF opens in the native PDF reader.
     */
    fun readingTarget(book: BookDetail): ReadingTarget {
        readableFile(book)?.let { return ReadingTarget.Foliate(it) }
        comicFile(book)?.let { return ReadingTarget.Comic(it) }
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
