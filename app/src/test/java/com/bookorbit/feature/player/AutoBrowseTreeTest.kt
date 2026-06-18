package com.bookorbit.feature.player

import com.bookorbit.core.db.AudioProgressEntity
import com.bookorbit.core.db.DownloadEntity
import com.bookorbit.feature.downloads.DownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Android Auto browse tree's offline filtering/ordering (no Android framework involved). */
class AutoBrowseTreeTest {

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
        )
        assertEquals(listOf("book/2", "book/1"), items.map { it.mediaId })
        assertTrue(items.all { it.isPlayable })
    }

    @Test
    fun `book entry uses narrators as subtitle and the local cover`() {
        val entry = AutoBrowseTree.downloadedAudiobooks(listOf(download(7, narrators = "Jane Doe"))).single()
        assertEquals("Jane Doe", entry.subtitle)
        assertEquals("/covers/7.jpg", entry.coverPath)
    }

    @Test
    fun `book entry falls back to authors when no narrator`() {
        val entry = AutoBrowseTree.downloadedAudiobooks(listOf(download(7, narrators = "", authors = "A. Writer"))).single()
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
        val items = AutoBrowseTree.continueListening(downloads, progress)
        assertEquals(listOf("book/3", "book/1"), items.map { it.mediaId })
    }

    @Test
    fun `root has continue and downloads shelves`() {
        assertEquals(
            listOf(AutoBrowseTree.CONTINUE_ID, AutoBrowseTree.DOWNLOADS_ID),
            AutoBrowseTree.rootChildren().map { it.mediaId },
        )
    }
}
