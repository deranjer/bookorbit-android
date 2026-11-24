package com.bookorbit.feature.reader.comic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

/** Local CBZ pages, extracted lazily by index. ZIP entry paths never become filesystem paths. */
class ComicArchive(private val file: File, private val cache: File) {
    private val size = file.length()
    private val stamp = file.lastModified()
    private var legacyPages: List<String> = emptyList()
    val pages: List<String> = ZipFile(file).use { zip ->
        val entries = zip.entries().asSequence().take(100_001).toList()
        require(entries.size <= 100_000)
        legacyPages = entries.filter { !it.isDirectory && listOf(".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp", ".svg", ".jxl", ".avif").any(it.name::endsWith) }.map { it.name }.sorted()
        entries.filter { !it.isDirectory && isComicImage(it.name) }.map { it.name }.sortedWith(::compareComicNames)
            .also { require(it.isNotEmpty() && it.distinct().size == it.size) }
    }

    fun legacyPage(index: Int): Int = legacyPages.getOrNull(index)?.let(pages::indexOf)?.takeIf { it >= 0 }
        ?: throw IOException("Saved comic image is unavailable")

    suspend fun page(index: Int): File = withContext(Dispatchers.IO) {
        val name = pages.getOrNull(index) ?: throw IOException("Comic page missing")
        if (file.length() != size || file.lastModified() != stamp) throw IOException("Comic archive changed")
        cache.mkdirs()
        val dest = File(cache, "$index.${name.substringAfterLast('.').lowercase()}")
        if (dest.exists()) return@withContext dest
        val part = File(cache, "$index-${java.util.UUID.randomUUID()}.part")
        try {
            ZipFile(file).use { zip ->
                val entry = zip.getEntry(name) ?: throw IOException("Comic page missing")
                if (entry.size > MAX_PAGE_BYTES) throw IOException("Comic page too large")
                zip.getInputStream(entry).use { input -> part.outputStream().use { output ->
                    val buffer = ByteArray(16_384)
                    var copied = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        copied += count
                        if (copied > MAX_PAGE_BYTES) throw IOException("Comic page too large")
                        output.write(buffer, 0, count)
                    }
                } }
            }
            if (!part.renameTo(dest)) throw IOException("Comic page cache failed")
            val cached = cache.listFiles().orEmpty().filter { it.name.matches(Regex("\\d+\\.[a-z]+")) }.sortedBy { it.lastModified() }
            var bytes = cached.sumOf { it.length() }
            var count = cached.size
            for (old in cached) {
                if (bytes <= 256 * 1024 * 1024L && count <= 128) break
                val oldBytes = old.length()
                if (old != dest && old.delete()) { bytes -= oldBytes; count-- }
            }
        } finally { part.delete() }
        dest
    }

    companion object {
        const val MAX_PAGE_BYTES = 64 * 1024 * 1024L
        fun isComicImage(name: String): Boolean = name.split('/').none { it.startsWith('.') || it == "__MACOSX" } &&
            name.substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "avif")
    }
}

/** Numeric image ordering matches the server for numbered archive paths. */
fun compareComicNames(a: String, b: String): Int {
    val parts = Regex("\\d+|\\D+")
    val left = parts.findAll(a.lowercase()).map { it.value }.toList()
    val right = parts.findAll(b.lowercase()).map { it.value }.toList()
    for (i in 0 until minOf(left.size, right.size)) {
        val x = left[i]; val y = right[i]
        val comparison = if (x.first().isDigit() && y.first().isDigit()) {
            val nx = x.trimStart('0').ifEmpty { "0" }; val ny = y.trimStart('0').ifEmpty { "0" }
            nx.length.compareTo(ny.length).takeIf { it != 0 } ?: nx.compareTo(ny)
        } else x.compareTo(y)
        if (comparison != 0) return comparison
    }
    return left.size.compareTo(right.size).takeIf { it != 0 } ?: a.compareTo(b)
}
