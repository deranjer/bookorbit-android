package com.bookorbit.feature.downloads

import com.bookorbit.core.db.DownloadEntity
import com.bookorbit.core.model.BookDetail
import com.bookorbit.core.model.BookFileRef
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class AdditiveDownloadPlanTest {
    private val json = Json
    private val text = BookFileRef(11, "epub", "primary", sizeBytes = 50, filename = "book.epub")
    private val audio = BookFileRef(12, "mp3", "supplement", sizeBytes = 100, filename = "audio.mp3")
    private val book = BookDetail(id = 1, libraryId = 1, libraryName = "Test", status = "present", addedAt = "2026-11-22", files = listOf(text, audio))
    private val local = DownloadedFile(11, "file:///existing/book.epub", "book.epub", "epub")
    private fun existing() = DownloadEntity(
        bookId = 1, title = "Book", authors = "Author", narrators = "", isAudiobook = false,
        format = "epub", sizeBytes = 50, downloadedAt = 123, coverLocalPath = "file:///existing/cover.jpg",
        bookJson = json.encodeToString(book), filesJson = json.encodeToString(listOf(local)),
        status = "COMPLETE", progress = 1f, selectedFileIdsJson = "[11]", planId = "old", storageId = "disk-location",
    )

    @Test fun `adding audio retains text bytes cover size timestamp and stable storage`() {
        val old = existing()
        val next = DownloadPlan.expand(old, book, setOf(12), json)!!
        assertEquals(listOf(11, 12), DownloadPlan.files(next, book, json).map { it.id })
        assertEquals(old.filesJson, next.filesJson)
        assertEquals(old.coverLocalPath, next.coverLocalPath)
        assertEquals(old.sizeBytes, next.sizeBytes)
        assertEquals(old.downloadedAt, next.downloadedAt)
        assertEquals(old.storageId, next.storageId)
        assertNotEquals(old.planId, next.planId)
        assertEquals("DOWNLOADING", next.status)
        assertTrue(next.isAudiobook)
    }

    @Test fun `already downloaded selection is a no-op`() {
        assertNull(DownloadPlan.expand(existing(), book, setOf(11), json))
    }

    @Test fun `active plan is never replaced`() {
        assertNull(DownloadPlan.expand(existing().copy(status = "DOWNLOADING"), book, setOf(12), json))
    }

    @Test fun `removed server file remains available locally with its original identity`() {
        val next = DownloadPlan.expand(existing(), book.copy(files = listOf(audio)), setOf(12), json)!!
        val snapshot = json.decodeFromString<BookDetail>(next.bookJson)
        assertEquals(text, snapshot.files.single { it.id == 11 })
        assertEquals(local, DownloadPlan.completed(next, json).single())
    }

    @Test fun `adding a format does not silently replace existing edition metadata or bytes`() {
        val changed = book.copy(files = listOf(text.copy(sizeBytes = 900, filename = "replacement.epub"), audio))
        val next = DownloadPlan.expand(existing(), changed, setOf(12), json)!!
        assertEquals(text, json.decodeFromString<BookDetail>(next.bookJson).files.single { it.id == 11 })
        assertEquals(local, DownloadPlan.completed(next, json).single())
    }

    @Test fun `failed extension retains completed files and pending targets`() {
        val next = DownloadPlan.expand(existing().copy(status = "FAILED", selectedFileIdsJson = "[11,12]", lastError = "offline"), book, setOf(12), json)!!
        assertEquals(listOf(11, 12), DownloadPlan.files(next, book, json).map { it.id })
        assertEquals(listOf(local), DownloadPlan.completed(next, json))
        assertNull(next.lastError)
    }

    @Test fun `legacy completed journal is authoritative rather than audio-first heuristic`() {
        val legacy = existing().copy(selectedFileIdsJson = null)
        assertNull(DownloadPlan.expand(legacy, book, setOf(11), json))
    }

    @Test fun `Android Auto keeps published audio during extension and after failure`() {
        val withAudio = existing().copy(isAudiobook = true, filesJson = json.encodeToString(listOf(DownloadedFile(12, "/audio.mp3", format = "MP3"))))
        assertTrue(withAudio.copy(status = "DOWNLOADING").hasPublishedAudio())
        assertTrue(withAudio.copy(status = "FAILED").hasPublishedAudio())
        assertFalse(existing().copy(isAudiobook = true, status = "DOWNLOADING").hasPublishedAudio())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unknown requested ID is not substituted`() {
        DownloadPlan.expand(existing(), book, setOf(999), json)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `cross-book plan cannot reuse another books bytes`() {
        DownloadPlan.expand(existing(), book.copy(id = 2), setOf(12), json)
    }
}
