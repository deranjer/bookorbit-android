package com.bookorbit.feature.sessions

import com.bookorbit.core.auth.SessionOwner
import java.util.UUID

data class ReadingHandle(val owner: SessionOwner, val fileId: Int, val token: String = UUID.randomUUID().toString())
data class NarrationHandle(val owner: SessionOwner, val fileId: Int, val token: String = UUID.randomUUID().toString())

/** Selects one statistical activity, never simultaneous reading and listening durations. */
internal class ReadingActivityArbiter {
    data class Choice(val owner: SessionOwner, val key: String, val fileId: Int, val type: SessionType, val percent: Double?, val active: Boolean)
    private data class Reader(val handle: ReadingHandle, val percent: Double?)
    private data class Audio(val owner: SessionOwner, val bookId: Int, val fileId: Int, val percent: Double?, val playing: Boolean)
    private data class Narration(val handle: NarrationHandle, val percent: Double?, val playing: Boolean)
    private var reader: Reader? = null
    private var audio: Audio? = null
    private var narration: Narration? = null

    fun openReader(owner: SessionOwner, fileId: Int, percent: Double?): ReadingHandle {
        require(fileId > 0)
        return ReadingHandle(owner, fileId).also { reader = Reader(it, percent?.takeIf(Double::isFinite)?.coerceIn(0.0, 100.0)) }
    }

    fun readerProgress(handle: ReadingHandle, percent: Double) {
        if (reader?.handle != handle || !percent.isFinite()) return
        reader = Reader(handle, percent.coerceIn(0.0, 100.0))
        narration?.takeIf { it.handle.owner == handle.owner && it.handle.fileId == handle.fileId }?.let {
            narration = it.copy(percent = percent.coerceIn(0.0, 100.0))
        }
    }

    fun closeReader(handle: ReadingHandle) { if (reader?.handle == handle) reader = null }

    fun listening(owner: SessionOwner, bookId: Int, fileId: Int, percent: Double) {
        require(bookId > 0 && fileId > 0 && percent.isFinite())
        audio = Audio(owner, bookId, fileId, percent.coerceIn(0.0, 100.0), true)
    }

    fun audioPlaying(owner: SessionOwner, playing: Boolean) {
        audio?.takeIf { it.owner == owner }?.let { audio = it.copy(playing = playing) }
    }

    fun closeAudio(owner: SessionOwner) { if (audio?.owner == owner) audio = null }

    fun openNarration(owner: SessionOwner, fileId: Int): NarrationHandle {
        require(fileId > 0)
        val percent = reader?.takeIf { it.handle.owner == owner && it.handle.fileId == fileId }?.percent
        return NarrationHandle(owner, fileId).also { narration = Narration(it, percent, false) }
    }

    fun narrationPlaying(handle: NarrationHandle, playing: Boolean) {
        narration?.takeIf { it.handle == handle }?.let { narration = it.copy(playing = playing) }
    }

    fun closeNarration(handle: NarrationHandle) { if (narration?.handle == handle) narration = null }

    fun clear() { reader = null; audio = null; narration = null }

    fun choose(owner: SessionOwner?, foreground: Boolean): Choice? {
        if (reader?.handle?.owner != owner) reader = null
        if (audio?.owner != owner) audio = null
        if (narration?.handle?.owner != owner) narration = null
        narration?.takeIf { it.playing }?.let { return nativeChoice(it) }
        audio?.takeIf { it.playing }?.let { return audioChoice(it) }
        reader?.takeIf { foreground }?.let { return readChoice(it, true) }
        narration?.let { return nativeChoice(it) }
        audio?.let { return audioChoice(it) }
        return reader?.let { readChoice(it, false) }
    }

    private fun readChoice(value: Reader, active: Boolean) = Choice(value.handle.owner, "read:${value.handle.fileId}",
        value.handle.fileId, SessionType.READ, value.percent, active)
    private fun audioChoice(value: Audio) = Choice(value.owner, "listen:${value.bookId}", value.fileId,
        SessionType.LISTEN, value.percent, value.playing)
    private fun nativeChoice(value: Narration) = Choice(value.handle.owner, "narration:${value.handle.fileId}", value.handle.fileId,
        SessionType.LISTEN, value.percent, value.playing)
}
