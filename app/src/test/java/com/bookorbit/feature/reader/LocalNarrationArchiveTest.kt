package com.bookorbit.feature.reader

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class LocalNarrationArchiveTest {
    @Test fun `SMIL clock formats preserve exact asset seconds`() {
        assertEquals(3723.25, LocalNarrationArchive.clock("01:02:03.25")!!, 0.00001)
        assertEquals(62.5, LocalNarrationArchive.clock("1:02.5")!!, 0.00001)
        assertEquals(0.125, LocalNarrationArchive.clock("125ms")!!, 0.00001)
        assertEquals(90.0, LocalNarrationArchive.clock("1.5min")!!, 0.00001)
        assertEquals(3600.0, LocalNarrationArchive.clock("1h")!!, 0.00001)
        assertNull(LocalNarrationArchive.clock(""))
        listOf("-1", "NaN", "1junk", "1:2:3:4").forEach { raw ->
            assertThrows(IllegalArgumentException::class.java) { LocalNarrationArchive.clock(raw) }
        }
    }

    @Test fun `references decode once like the EPUB reader and reject external paths`() {
        assertEquals("EPUB/audio/a% b+.mp3" to null, LocalNarrationArchive.resolve("../audio/a%25%20b+.mp3", "EPUB/smil/ch.smil"))
        assertEquals("EPUB/текст.xhtml" to "p1", LocalNarrationArchive.resolve("%D1%82%D0%B5%D0%BA%D1%81%D1%82.xhtml#p1", "EPUB/ch.smil"))
        assertEquals("EPUB/a%20.mp3" to null, LocalNarrationArchive.resolve("a%2520.mp3", "EPUB/ch.smil"))
        assertEquals("EPUB/a%2Fb.mp3" to null, LocalNarrationArchive.resolve("a%2Fb.mp3", "EPUB/ch.smil"))
        listOf("https://example.test/a", "//example.test/a", "../../a", "/a", "a\\b").forEach { raw ->
            assertThrows(IllegalArgumentException::class.java) { LocalNarrationArchive.resolve(raw, "EPUB/ch.smil") }
        }
    }

    @Test fun `local playlist follows spine not manifest and extracts only selected audio`() {
        fixture().use { fixture ->
            val archive = LocalNarrationArchive(fixture.file)
            val timeline = archive.timeline(42, 7)
            assertEquals(listOf("EPUB/b.xhtml#p", "EPUB/a.xhtml#p"), timeline.clips.map { it.fragment })
            assertEquals(listOf(0, 1), timeline.clips.map { it.sectionIndex })
            assertEquals(1.25, timeline.clips.first().clipBeginSeconds, 0.00001)
            val position = timeline.checkpoint(0, 625, 1.0)!!
            assertEquals(1.875, position.audioSeconds, 0.00001)
            assertEquals(625L, timeline.restore(position)!!.clipOffsetMs)
            val out = Files.createTempFile("narration-audio", ".bin").toFile()
            try { archive.extract("EPUB/voice.bin", out); assertArrayEquals(byteArrayOf(1, 2, 3), out.readBytes()) }
            finally { out.delete() }
        }
    }

    @Test fun `DTD entities and mismatched text are refused`() {
        fixture(smilPrefix = "<!DOCTYPE smil [<!ENTITY x SYSTEM 'file:///etc/passwd'>]>").use { fixture ->
            assertThrows(IllegalArgumentException::class.java) { LocalNarrationArchive(fixture.file).timeline(42, 7) }
        }
        fixture(text = "other.xhtml").use { fixture ->
            assertThrows(IllegalArgumentException::class.java) { LocalNarrationArchive(fixture.file).timeline(42, 7) }
        }
    }

    @Test fun `duplicate ZIP names are refused rather than choosing a different edition resource`() {
        fixture().use { fixture ->
            val bytes = fixture.file.readBytes()
            val from = "EPUB/a.xhtml".toByteArray()
            val to = "EPUB/b.xhtml".toByteArray()
            for (offset in 0..bytes.size - from.size) {
                if (from.indices.all { bytes[offset + it] == from[it] }) to.copyInto(bytes, offset)
            }
            fixture.file.writeBytes(bytes)
            assertThrows(IllegalArgumentException::class.java) { LocalNarrationArchive(fixture.file) }
        }
    }

    @Test fun `oversized XML metadata is rejected before decompression`() {
        val file = Files.createTempFile("large-narration", ".epub").toFile()
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("META-INF/container.xml"))
                zip.write(ByteArray(4 * 1024 * 1024 + 1))
                zip.closeEntry()
            }
            assertThrows(IllegalArgumentException::class.java) { LocalNarrationArchive(file).timeline(42, 7) }
        } finally { file.delete() }
    }

    private class Fixture(val file: java.io.File) : AutoCloseable { override fun close() { file.delete() } }
    private fun fixture(smilPrefix: String = "", text: String? = null): Fixture {
        val file = Files.createTempFile("local-narration", ".epub").toFile()
        ZipOutputStream(file.outputStream()).use { zip ->
            fun entry(name: String, value: String) { zip.putNextEntry(ZipEntry(name)); zip.write(value.toByteArray()); zip.closeEntry() }
            entry("META-INF/container.xml", """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="EPUB/package.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""")
            entry("EPUB/package.opf", """<package xmlns="http://www.idpf.org/2007/opf"><manifest>
                <item id="a" href="a.xhtml" media-type="application/xhtml+xml" media-overlay="sa"/>
                <item id="b" href="b.xhtml" media-type="application/xhtml+xml" media-overlay="sb"/>
                <item id="sa" href="a.smil" media-type="application/smil+xml"/>
                <item id="sb" href="b.smil" media-type="application/smil+xml"/>
                <item id="voice" href="voice.bin" media-type="audio/mpeg"/>
                </manifest><spine><itemref idref="b"/><itemref idref="a"/></spine></package>""")
            for (name in listOf("a", "b")) {
                entry("EPUB/$name.xhtml", "<html/>")
                entry("EPUB/$name.smil", """$smilPrefix<smil xmlns="http://www.w3.org/ns/SMIL"><body><seq><par><text src="${text ?: "$name.xhtml"}#p"/><audio src="voice.bin" clipBegin="1250ms" clipEnd="00:03"/></par></seq></body></smil>""")
            }
            zip.putNextEntry(ZipEntry("EPUB/voice.bin")); zip.write(byteArrayOf(1, 2, 3)); zip.closeEntry()
        }
        return Fixture(file)
    }
}
