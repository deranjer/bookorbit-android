package com.bookorbit.feature.dictionary

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.tukaani.xz.SingleXZInputStream
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.FilterInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.CodingErrorAction

internal data class DictionaryLicense(val title: String, val notice: String)

/** Streams only regular TEI/COPYING members; archive names are never filesystem destinations. */
internal fun importDictionaryArchive(input: InputStream, check: () -> Unit, entry: (String, String) -> Unit): DictionaryLicense {
    var license: DictionaryLicense? = null
    var copying = ""
    var count = 0
    SingleXZInputStream(input, 64 * 1024).use { xz ->
        TarArchiveInputStream(LimitedDictionaryInput(xz, 768L * 1024 * 1024)).use { tar ->
            while (true) {
                check()
                val member = tar.nextEntry ?: break
                require(++count <= 2048 && member.size in 0..(512L * 1024 * 1024))
                require(!member.isSymbolicLink && !member.isLink)
                if (!member.isFile) continue
                when {
                    member.name.endsWith(".tei") -> {
                        require(license == null)
                        license = importDictionaryTei(tar, check, entry)
                    }
                    member.name.substringAfterLast('/') == "COPYING" -> {
                        require(member.size <= 64 * 1024)
                        copying = String(tar.readBytes(), Charsets.UTF_8)
                    }
                }
            }
        }
    }
    val result = requireNotNull(license)
    require(copying.isNotBlank())
    return result.copy(notice = result.notice + "\n\n" + copying)
}

internal fun importDictionaryTei(input: InputStream, check: () -> Unit, entry: (String, String) -> Unit): DictionaryLicense {
    val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
    // KXml's token mode with document processing disabled cannot load an external DTD.
    parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
    parser.setInput(InputStreamReader(input, Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)))
    var depth = 0
    var entryDepth = -1
    var orthDepth = -1
    var header = true
    val availability = StringBuilder()
    var availabilityDepth = -1
    var entries = 0
    val words = mutableListOf<String>()
    val word = StringBuilder()
    val body = StringBuilder()
    val notice = StringBuilder()
    var title = "FreeDict/WikDict"
    while (true) {
        check()
        when (parser.nextToken()) {
            XmlPullParser.END_DOCUMENT -> break
            XmlPullParser.DOCDECL -> require(parser.text.trim().matches(Regex("TEI\\s+SYSTEM\\s+[\"']freedict-P5\\.dtd[\"']")))
            XmlPullParser.START_TAG -> {
                require(++depth <= 64)
                require(parser.attributeCount <= 32)
                for (index in 0 until parser.attributeCount) require(parser.getAttributeValue(index).length <= 8192)
                val name = parser.name
                if (header && name == "availability") availabilityDepth = depth
                if (header && availabilityDepth >= 0 && name == "ref") parser.getAttributeValue(null, "target")?.let {
                    notice.append("\n").append(it).append("\n")
                    require(notice.length <= 131072)
                }
                if (name == "entry") {
                    require(!header && entryDepth < 0 && ++entries <= 1_000_000 && availability.isNotBlank())
                    entryDepth = depth; words.clear(); body.clear()
                }
                if (entryDepth >= 0 && name == "orth") { orthDepth = depth; word.clear() }
            }
            XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF -> {
                val text = requireNotNull(parser.text) // Unresolved entities are not dropped or expanded.
                if (header) { notice.append(text); require(notice.length <= 131072) }
                if (header && availabilityDepth >= 0) availability.append(text)
                if (entryDepth >= 0) { body.append(text); require(body.length <= 131072) }
                if (orthDepth >= 0) { word.append(text); require(word.length <= 1024) }
            }
            XmlPullParser.END_TAG -> {
                if (orthDepth == depth) { words.add(dictionaryWord(word.toString())); require(words.size <= 32); orthDepth = -1 }
                if (entryDepth == depth) {
                    require(words.isNotEmpty())
                    val definition = body.toString().replace(Regex("[ \\t]+"), " ").replace(Regex("\\n\\s*\\n"), "\n").trim()
                    words.distinct().forEach { entry(it, definition) }
                    entryDepth = -1
                } else if (entryDepth >= 0) body.append('\n')
                if (header && parser.name == "title") title = notice.toString().trim().takeLast(256)
                if (parser.name == "teiHeader") header = false
                if (availabilityDepth == depth) availabilityDepth = -1
                depth--
            }
        }
    }
    require(entries > 0 && availability.isNotBlank() && depth == 0)
    return DictionaryLicense(title, notice.toString().trim())
}

private class LimitedDictionaryInput(input: InputStream, private val limit: Long) : FilterInputStream(input) {
    private var bytes = 0L
    override fun read(): Int = super.read().also { if (it >= 0) { bytes++; require(bytes <= limit) } }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = `in`.read(buffer, offset, length).also {
        if (it > 0) { bytes += it; require(bytes <= limit) }
    }
    override fun skip(n: Long): Long {
        val buffer = ByteArray(8192)
        var skipped = 0L
        while (skipped < n) {
            val read = read(buffer, 0, minOf(buffer.size.toLong(), n - skipped).toInt())
            if (read < 0) break
            skipped += read
        }
        return skipped
    }
}
