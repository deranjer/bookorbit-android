package com.bookorbit.feature.reader

import org.w3c.dom.Element
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

/** Bounded EPUB metadata reader. Archive names are identities, never output filesystem paths. */
internal class LocalNarrationArchive(private val file: File) {
    private val entries = ZipFile(file).use { zip ->
        zip.entries().asSequence().take(100_001).toList().also { entries ->
            require(entries.size <= 100_000 && entries.map { it.name }.distinct().size == entries.size)
        }.filterNot { it.isDirectory }.associate { it.name to it.size }
    }
    private var metadataBytes = 0L

    fun timeline(bookId: Int, fileId: Int): NativeNarrationTimeline {
        val container = xml("META-INF/container.xml")
        val opf = container.elements("rootfile").firstOrNull {
            it.getAttribute("media-type") == "application/oebps-package+xml"
        }?.getAttribute("full-path") ?: error("Missing EPUB package")
        require(NativeNarrationTimeline.path(opf))
        val doc = xml(opf)
        val manifest = doc.elements("manifest").single().children("item")
        require(manifest.size <= 100_000 && manifest.map { it.attr("id") }.distinct().size == manifest.size)
        val byId = manifest.associateBy { it.attr("id") }
        val mimeByPath = manifest.associate { resolve(it.attr("href"), opf).first to it.attr("media-type") }
        require(mimeByPath.size == manifest.size) { "Ambiguous EPUB manifest paths" }
        val clips = mutableListOf<NativeNarrationClip>()
        val sections = mutableListOf<NativeNarrationSection>()
        val resources = linkedMapOf<String, NativeNarrationResource>()
        var elapsed = 0.0
        doc.elements("spine").single().children("itemref").forEachIndexed { index, ref ->
            val text = byId[ref.attr("idref")] ?: error("Missing spine item")
            val overlayId = text.getAttribute("media-overlay")
            if (overlayId.isEmpty()) return@forEachIndexed
            val overlay = byId[overlayId] ?: error("Missing media overlay")
            require(overlay.attr("media-type") == "application/smil+xml")
            val smil = resolve(overlay.attr("href"), opf).first
            val href = resolve(text.attr("href"), opf).first
            require(entries.containsKey(href))
            val start = elapsed
            val firstClip = clips.size
            xml(smil).elements("par").forEach { par ->
                val textNode = par.children("text").singleOrNull() ?: return@forEach
                val audio = par.children("audio").singleOrNull() ?: return@forEach
                val (textHref, fragment) = resolve(textNode.attr("src"), smil)
                require(textHref == href) { "SMIL text does not match its spine section" }
                val (audioHref, audioFragment) = resolve(audio.attr("src"), smil)
                require(audioFragment == null)
                val mime = mimeByPath[audioHref] ?: error("Audio missing from manifest")
                require(mime.startsWith("audio/"))
                val size = entries[audioHref] ?: error("Audio missing from EPUB")
                require(size in 1..MAX_AUDIO_BYTES)
                resources[audioHref] = NativeNarrationResource(audioHref, mime, size)
                val begin = clock(audio.getAttribute("clipBegin")) ?: 0.0
                val end = clock(audio.getAttribute("clipEnd"))
                require(clips.size < 100_000)
                clips += NativeNarrationClip(clips.size, index, smil, href, fragment, audioHref, mime, begin, end)
                if (end != null) elapsed += end - begin
            }
            if (clips.size > firstClip) sections += NativeNarrationSection(index, href, smilHref = smil, startSeconds = start)
        }
        return NativeNarrationTimeline(NativeNarrationPlaylist(bookId, fileId, items = clips,
            sections = sections, resources = resources.values.toList()), bookId, fileId)
    }

    fun extract(href: String, target: File) {
        val size = entries[href] ?: throw IOException("Missing narration resource")
        require(size in 1..MAX_AUDIO_BYTES)
        ZipFile(file).use { zip ->
            zip.getInputStream(zip.getEntry(href)).use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= size && total <= MAX_AUDIO_BYTES)
                        output.write(buffer, 0, count)
                    }
                    require(total == size)
                }
            }
        }
    }

    private fun xml(href: String): Element {
        val size = entries[href] ?: error("Missing EPUB metadata")
        require(size in 1..(4 * 1024 * 1024))
        metadataBytes += size
        require(metadataBytes <= 32 * 1024 * 1024)
        val bytes = ZipFile(file).use { zip -> zip.getInputStream(zip.getEntry(href)).use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= size)
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } }
        require(bytes.size.toLong() == size)
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            // Android Harmony does not implement these JAXP features. DTD refusal below and
            // the throwing resolver are mandatory on every platform, not best-effort flags.
            for (feature in listOf("http://xml.org/sax/features/external-general-entities",
                "http://xml.org/sax/features/external-parameter-entities")) {
                try { setFeature(feature, false) } catch (_: javax.xml.parsers.ParserConfigurationException) { }
            }
            isExpandEntityReferences = false
        }
        // Reject declarations before parsing, including UTF-16/32 encodings. No DTDs are needed in OPF/SMIL.
        val lexical = bytes.filter { it != 0.toByte() }.toByteArray().toString(Charsets.ISO_8859_1)
        require(lexical.trimStart('\u00ef', '\u00bb', '\u00bf', '\u00fe', '\u00ff', ' ', '\t', '\r', '\n').startsWith('<'))
        require(!lexical.contains("<!DOCTYPE", true))
        val builder = factory.newDocumentBuilder()
        builder.setEntityResolver { _, _ -> throw IOException("External XML entity refused") }
        return builder.parse(bytes.inputStream()).documentElement
    }

    companion object {
        const val MAX_AUDIO_BYTES = 1024 * 1024 * 1024L
        fun clock(raw: String): Double? {
            if (raw.isBlank()) return null
            val value = raw.trim().removePrefix("npt=")
            val number = if (':' in value) {
                val parts = value.split(':')
                require(parts.size in 2..3)
                require(parts.all { it.matches(Regex("[0-9]+(?:\\.[0-9]+)?")) })
                parts.fold(0.0) { sum, part -> sum * 60 + part.toDouble() }
            } else {
                val match = requireNotNull(Regex("([0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(h|min|ms|s)?").matchEntire(value)) { "Invalid SMIL clock" }
                match.groupValues[1].toDouble() * when (match.groupValues[2]) { "h" -> 3600.0; "min" -> 60.0; "ms" -> 0.001; else -> 1.0 }
            }
            require(number.isFinite() && number in 0.0..1e9)
            return number
        }
        fun resolve(raw: String, base: String): Pair<String, String?> {
            require(raw.isNotEmpty() && !raw.startsWith('/') && '\\' !in raw)
            val uri = URI(null, null, base, null).resolve(URI(raw.replace(" ", "%20"))).normalize()
            require(!uri.isAbsolute && uri.rawAuthority == null)
            // decodeURI preserves reserved escapes; '+' is a literal EPUB path character.
            fun decode(value: String): String {
                val protected = Regex("%[0-9a-fA-F]{2}").replace(value) {
                    if (it.value.substring(1).toInt(16).toChar() in ";/?:@&=+$,#") "%25" + it.value.substring(1) else it.value
                }
                return URLDecoder.decode(protected.replace("+", "%2B"), "UTF-8")
            }
            val path = decode(uri.rawPath)
            require(NativeNarrationTimeline.path(path))
            return path to uri.rawFragment?.let(::decode)
        }
        private fun Element.attr(name: String) = getAttribute(name).also { require(it.isNotEmpty()) }
        private fun Element.children(name: String) = (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }
            .filter { it.localName == name || it.nodeName == name }
        private fun Element.elements(name: String) = getElementsByTagNameNS("*", name).let { nodes ->
            (0 until nodes.length).map { nodes.item(it) as Element }
        }
    }
}
