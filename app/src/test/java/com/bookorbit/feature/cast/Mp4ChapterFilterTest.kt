package com.bookorbit.feature.cast

import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class Mp4ChapterFilterTest {
    private fun int(value: Int) = ByteBuffer.allocate(4).putInt(value).array()
    private fun box(type: String, payload: ByteArray = byteArrayOf()) = int(payload.size + 8) + type.toByteArray() + payload
    private fun track(id: Int, handler: String, reference: Int? = null): ByteArray {
        val tkhd = box("tkhd", ByteArray(12) + int(id) + ByteArray(8))
        val hdlr = box("hdlr", ByteArray(8) + handler.toByteArray())
        val ref = reference?.let { box("tref", box("chap", int(it))) } ?: byteArrayOf()
        return box("trak", tkhd + box("mdia", hdlr) + ref)
    }
    private fun movie(reference: Int? = 2, extra: ByteArray = byteArrayOf()) =
        box("ftyp", "M4A ".toByteArray()) + box("moov", track(1, "soun", reference) + track(2, "text") + extra) + box("mdat", ByteArray(120) { 77 })
    private fun discover(bytes: ByteArray) = Mp4ChapterFilter.discover(bytes.size.toLong()) { start, count ->
        bytes.copyOfRange(start.toInt(), start.toInt() + count)
    }

    @Test fun `only referenced chapter metadata changes and every one-byte range gets the same view`() {
        val source = movie()
        val patches = discover(source)
        assertEquals(2, patches.size)
        val full = source.copyOf()
        patches.forEach { it.apply(0, full, full.size) }
        for (position in source.indices) {
            val byte = byteArrayOf(source[position])
            patches.forEach { it.apply(position.toLong(), byte, 1) }
            assertEquals(full[position], byte[0])
            if (patches.none { position.toLong() in it.offset until it.offset + 4 }) assertEquals(source[position], full[position])
        }
        assertTrue(source.takeLast(120).all { it == 77.toByte() })
        assertArrayEquals(source.takeLast(120).toByteArray(), full.takeLast(120).toByteArray())
        patches.forEach { assertEquals("free", String(full, it.offset.toInt(), 4)) }
    }

    @Test fun `unreferenced text audio references and fragmented movies pass through`() {
        assertTrue(discover(movie(reference = null)).isEmpty())
        assertTrue(discover(movie(reference = 1)).isEmpty())
        assertTrue(discover(movie(extra = box("mvex"))).isEmpty())
    }

    @Test fun `moov after an extended size 8 GiB mdat is found without reading media`() {
        val ftyp = box("ftyp", "M4A ".toByteArray())
        val mediaLength = 8L * 1024 * 1024 * 1024
        val mdat = int(1) + "mdat".toByteArray() + ByteBuffer.allocate(8).putLong(mediaLength).array()
        val moov = box("moov", track(1, "soun", 2) + track(2, "text"))
        val movieOffset = ftyp.size + mediaLength
        val reads = mutableListOf<Pair<Long, Int>>()
        val patches = Mp4ChapterFilter.discover(movieOffset + moov.size) { start, count ->
            reads += start to count
            when (start) {
                0L -> (ftyp + mdat).copyOfRange(0, count)
                ftyp.size.toLong() -> mdat.copyOfRange(0, count)
                movieOffset -> moov.copyOfRange(0, count)
                else -> error("Must not read media payload at $start")
            }
        }
        assertEquals(2, patches.size)
        assertTrue(patches.all { it.offset > movieOffset })
        assertTrue(reads.sumOf { it.second } < 1024)
    }

    @Test fun `truncated metadata oversized moov and random headers fail closed without throwing`() {
        val source = movie()
        for (length in 0 until source.size) {
            val truncated = source.copyOf(length)
            val patches = discover(truncated)
            assertTrue(patches.all { it.offset + 4 <= length })
        }
        val ftyp = box("ftyp", "M4A ".toByteArray())
        val largeHeader = ftyp + int(32 * 1024 * 1024) + "moov".toByteArray() + ByteArray(8)
        assertTrue(Mp4ChapterFilter.discover(64L * 1024 * 1024) { start, count ->
            assertTrue(count <= 16)
            largeHeader.copyOfRange(start.toInt(), start.toInt() + count)
        }.isEmpty())
        val random = Random(9876)
        repeat(500) { assertTrue(discover(random.nextBytes(256)).isEmpty()) }
    }
}
