package com.bookorbit.feature.player

import com.bookorbit.core.db.AudioProgressEntity
import com.bookorbit.core.db.DownloadEntity
import com.bookorbit.feature.downloads.DownloadStatus
import com.bookorbit.feature.player.AutoBrowseTree.CompletionStatus
import com.bookorbit.feature.player.AutoBrowseTree.CurrentBookInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Android Auto browse tree's offline filtering/ordering (no Android framework involved). */
class AutoBrowseTreeTest {

    /** Deliberately not the English defaults: if any title were still hard-coded, the tests below would catch it. */
    private val labels = AutoBrowseTree.Labels(
        appName = "App*",
        continueListening = "Continue*",
        downloaded = "Downloaded*",
        audiobook = "Audiobook*",
        chaptersOf = { "Chapters* $it" },
    )

    private fun download(
        id: Int,
        title: String = "Book $id",
        isAudiobook: Boolean = true,
        status: DownloadStatus = DownloadStatus.COMPLETE,
        narrators: String = "Narrator $id",
        authors: String = "Author $id",
        cover: String? = "/covers/$id.jpg",
        downloadedAt: Long = id.toLong(),
    ) = DownloadEntity(
        bookId = id,
        title = title,
        authors = authors,
        narrators = narrators,
        isAudiobook = isAudiobook,
        format = "mp3",
        sizeBytes = 0,
        downloadedAt = downloadedAt,
        coverLocalPath = cover,
        bookJson = "{}",
        filesJson = "[]",
        status = status.name,
        progress = 1f,
    )

    private fun progress(bookId: Int, updatedAt: Long) =
        AudioProgressEntity(bookId, currentFileId = bookId, positionSeconds = 1.0, percentage = 1.0, updatedAt = updatedAt, dirty = false)

    @Test
    fun `bookMediaId round-trips through parseBookId`() {
        assertEquals(42, AutoBrowseTree.parseBookId(AutoBrowseTree.bookMediaId(42)))
        assertNull(AutoBrowseTree.parseBookId(AutoBrowseTree.DOWNLOADS_ID))
        assertNull(AutoBrowseTree.parseBookId("book/notanumber"))
    }

    @Test
    fun `downloadedAudiobooks keeps only complete audiobooks, newest first`() {
        val items = AutoBrowseTree.downloadedAudiobooks(
            listOf(
                download(1, downloadedAt = 100),
                download(2, downloadedAt = 300),
                download(3, isAudiobook = false), // ebook — excluded
                download(4, status = DownloadStatus.DOWNLOADING), // in-flight — excluded
            ),
            labels,
        )
        assertEquals(listOf("book/2", "book/1"), items.map { it.mediaId })
        assertTrue(items.all { it.isPlayable })
    }

    @Test
    fun `book entry uses narrators as subtitle and the local cover`() {
        val entry = AutoBrowseTree.downloadedAudiobooks(listOf(download(7, narrators = "Jane Doe")), labels).single()
        assertEquals("Jane Doe", entry.subtitle)
        assertEquals("/covers/7.jpg", entry.coverPath)
    }

    @Test
    fun `book entry falls back to authors when no narrator`() {
        val entry = AutoBrowseTree.downloadedAudiobooks(listOf(download(7, narrators = "", authors = "A. Writer")), labels).single()
        assertEquals("A. Writer", entry.subtitle)
    }

    @Test
    fun `continueListening lists downloaded in-progress books by most recent play`() {
        val downloads = listOf(download(1), download(2), download(3))
        val progress = listOf(
            progress(bookId = 1, updatedAt = 100),
            progress(bookId = 3, updatedAt = 500),
            progress(bookId = 99, updatedAt = 900), // not downloaded — excluded
        )
        val items = AutoBrowseTree.continueListening(downloads, progress, labels)
        assertEquals(listOf("book/3", "book/1"), items.map { it.mediaId })
    }

    @Test
    fun `root has continue and downloads shelves`() {
        assertEquals(
            listOf(AutoBrowseTree.CONTINUE_ID, AutoBrowseTree.DOWNLOADS_ID),
            AutoBrowseTree.rootChildren(null, labels).map { it.mediaId },
        )
    }

    private val ranges = listOf(ChapterRange(0, "One", 0.0, 60.0), ChapterRange(1, "Two", 60.0, 150.0), ChapterRange(2, "Three", 150.0, 200.0))
    private val current = CurrentBookInfo(bookId = 7, title = "Dune", ranges = ranges, positionSec = 75.0)

    @Test
    fun `rootChildren leads with the chapters node only when a chaptered book is loaded`() {
        assertEquals(listOf(AutoBrowseTree.CONTINUE_ID, AutoBrowseTree.DOWNLOADS_ID), AutoBrowseTree.rootChildren(null, labels).map { it.mediaId })
        val withBook = AutoBrowseTree.rootChildren(current, labels)
        assertEquals(AutoBrowseTree.NOW_PLAYING_CHAPTERS_ID, withBook.first().mediaId)
        assertEquals("Chapters* Dune", withBook.first().title)
        assertEquals(3, withBook.size)
        assertEquals(2, AutoBrowseTree.rootChildren(current.copy(ranges = emptyList()), labels).size)
    }

    @Test
    fun `chapterEntries are playable with durations and completion`() {
        val e = AutoBrowseTree.chapterEntries(current)
        assertEquals(listOf("One", "Two", "Three"), e.map { it.title })
        assertEquals(listOf("1m", "1m", "50s"), e.map { it.subtitle })
        assertTrue(e.all { it.isPlayable })
        assertEquals(listOf(CompletionStatus.FULLY_PLAYED, CompletionStatus.PARTIALLY_PLAYED, CompletionStatus.NOT_PLAYED), e.map { it.completion })
    }

    @Test
    fun `chapter media ids round-trip`() {
        assertEquals(7 to 2, AutoBrowseTree.parseChapterId(AutoBrowseTree.chapterMediaId(7, 2)))
        assertNull(AutoBrowseTree.parseChapterId("book/7"))
        assertNull(AutoBrowseTree.parseChapterId("chapter/x/1"))
        assertNull(AutoBrowseTree.parseBookId(AutoBrowseTree.chapterMediaId(7, 2)))
    }

    @Test
    fun `root shelves use the supplied labels, not built-in text`() {
        val shelves = AutoBrowseTree.rootChildren(null, labels)
        assertEquals(listOf("Continue*", "Downloaded*"), shelves.map { it.title })
        assertEquals("App*", AutoBrowseTree.rootMediaItem(labels).mediaMetadata.title.toString())
    }

    @Test
    fun `an untitled download falls back to the audiobook label`() {
        val entry = AutoBrowseTree.downloadedAudiobooks(listOf(download(9).copy(title = null)), labels).single()
        assertEquals("Audiobook*", entry.title)
    }
}
