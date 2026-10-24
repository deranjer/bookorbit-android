package com.bookorbit.feature.cast

/** A same-length view of an MP4: only chapter box types become `free`, never audio bytes. */
internal data class Mp4HeaderPatch(val offset: Long) {
    fun apply(position: Long, buffer: ByteArray, count: Int) {
        for (i in FREE.indices) {
            val index = offset + i - position
            if (index >= 0 && index < count) buffer[index.toInt()] = FREE[i]
        }
    }

    private companion object { val FREE = "free".toByteArray(Charsets.US_ASCII) }
}

/**
 * Inspect bounded metadata, including a moov after mdat, through exact positional reads. Unknown,
 * fragmented, malformed or oversized layouts pass through. Offsets and lengths never change, so
 * arbitrary HTTP ranges still address the original source and BookOrbit keeps its own chapters.
 */
internal object Mp4ChapterFilter {
    private const val MAX_MOOV_BYTES = 16 * 1024 * 1024
    private const val MAX_BOXES = 4096
    private data class Box(val type: String, val start: Int, val payload: Int, val end: Int)
    private data class Track(val box: Box, val id: Long, val handler: String, val references: List<Box>)

    fun discover(totalLength: Long, read: (Long, Int) -> ByteArray?): List<Mp4HeaderPatch> {
        if (totalLength < 16) return emptyList()
        var offset = 0L
        var sawFtyp = false
        repeat(16) {
            if (offset > totalLength - 8) return emptyList()
            val header = read(offset, minOf(16L, totalLength - offset).toInt()) ?: return emptyList()
            if (header.size < 8) return emptyList()
            val type = type(header, 4)
            var size = uint(header, 0)
            val headerSize = if (size == 1L) 16 else 8
            if (size == 1L) {
                if (header.size < 16) return emptyList()
                size = longSize(header, 8) ?: return emptyList()
            } else if (size == 0L) size = totalLength - offset
            if (size < headerSize || size > totalLength - offset) return emptyList()
            if (type == "ftyp") sawFtyp = true
            if (type == "moov") {
                if (!sawFtyp || size > MAX_MOOV_BYTES) return emptyList()
                val movie = read(offset, size.toInt()) ?: return emptyList()
                if (movie.size != size.toInt()) return emptyList()
                return parse(movie, headerSize, offset)
            }
            offset += size // Seek over media data; never read it to locate metadata.
        }
        return emptyList()
    }

    private fun parse(data: ByteArray, headerSize: Int, movieOffset: Long): List<Mp4HeaderPatch> {
        val movieChildren = boxes(data, headerSize, data.size) ?: return emptyList()
        if (movieChildren.any { it.type == "mvex" }) return emptyList()
        val tracks = mutableListOf<Track>()
        for (box in movieChildren.filter { it.type == "trak" }) {
            if (tracks.size >= 128) return emptyList()
            val children = boxes(data, box.payload, box.end) ?: return emptyList()
            val tkhd = children.singleOrNull { it.type == "tkhd" } ?: return emptyList()
            if (tkhd.end - tkhd.payload < 4) return emptyList()
            val version = data[tkhd.payload].toInt() and 255
            val idOffset = tkhd.payload + when (version) { 0 -> 12; 1 -> 20; else -> return emptyList() }
            if (idOffset > tkhd.end - 4) return emptyList()
            val id = uint(data, idOffset)
            if (id == 0L || tracks.any { it.id == id }) return emptyList()
            val mdia = children.singleOrNull { it.type == "mdia" } ?: return emptyList()
            val mediaChildren = boxes(data, mdia.payload, mdia.end) ?: return emptyList()
            val hdlr = mediaChildren.singleOrNull { it.type == "hdlr" } ?: return emptyList()
            if (hdlr.end - hdlr.payload < 12) return emptyList()
            val references = mutableListOf<Box>()
            for (tref in children.filter { it.type == "tref" }) {
                references += (boxes(data, tref.payload, tref.end) ?: return emptyList()).filter { it.type == "chap" }
            }
            tracks += Track(box, id, type(data, hdlr.payload + 8), references)
        }
        if (tracks.none { it.handler == "soun" }) return emptyList()
        val chapterIds = mutableSetOf<Long>()
        val referenceBoxes = tracks.flatMap { it.references }
        for (reference in referenceBoxes) {
            val length = reference.end - reference.payload
            if (length == 0 || length % 4 != 0) return emptyList()
            for (p in reference.payload until reference.end step 4) {
                val id = uint(data, p)
                // Only known, referenced QuickTime text chapters; do not hide arbitrary tracks.
                if (tracks.singleOrNull { it.id == id }?.handler != "text") return emptyList()
                chapterIds += id
            }
        }
        if (chapterIds.isEmpty()) return emptyList()
        return (referenceBoxes + tracks.filter { it.id in chapterIds }.map { it.box })
            .map { Mp4HeaderPatch(movieOffset + it.start + 4) }.sortedBy { it.offset }
    }

    private fun boxes(data: ByteArray, start: Int, end: Int): List<Box>? {
        val result = mutableListOf<Box>()
        var position = start
        while (position < end) {
            if (end - position < 8 || result.size >= MAX_BOXES) return null
            var size = uint(data, position)
            val headerSize = if (size == 1L) 16 else 8
            if (size == 1L) {
                if (end - position < 16) return null
                size = longSize(data, position + 8) ?: return null
            } else if (size == 0L) size = (end - position).toLong()
            if (size < headerSize || size > end - position) return null
            result += Box(type(data, position + 4), position, position + headerSize, position + size.toInt())
            position += size.toInt()
        }
        return result
    }

    private fun type(data: ByteArray, offset: Int) = String(data, offset, 4, Charsets.ISO_8859_1)
    private fun uint(data: ByteArray, offset: Int): Long =
        (0..3).fold(0L) { value, i -> (value shl 8) or (data[offset + i].toLong() and 255) }
    private fun longSize(data: ByteArray, offset: Int): Long? {
        if (data[offset].toInt() < 0) return null
        return (0..7).fold(0L) { value, i -> (value shl 8) or (data[offset + i].toLong() and 255) }
    }
}
