package com.bookorbit.feature.player

import com.bookorbit.core.db.AudioProgressEntity
import com.bookorbit.core.db.DownloadEntity
import com.bookorbit.feature.downloads.DownloadsRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AutoSearchTest {
    private val catalog = mockk<AutoCatalog>()
    private val downloads = mockk<DownloadsRepository>()
    private val progress = mockk<AudioProgressRepository>()
    private val records = MutableStateFlow<List<DownloadEntity>>(emptyList())
    private val search = AutoSearch(catalog, downloads, progress)
    private val labels = AutoBrowseTree.Labels("App", "Continue", "Downloads", "Audiobook", { it })
    init {
        every { downloads.downloads } returns records
        coEvery { progress.recent() } returns emptyList()
        coEvery { catalog.page(any(), any(), any()) } returns AutoCatalog.Page(emptyList(), 0)
    }
    private fun download(id: Int, title: String, author: String = "Author", narrator: String = "Narrator") =
        DownloadEntity(id, title, author, narrator, true, "mp3", 0, id.toLong(), null, "{}", "[]", "COMPLETE", 1f)
    private fun entry(id: Int, title: String) = AutoBrowseTree.BrowseEntry("book/$id", title, "Author", null, true)

    @Test fun `exact titles outrank a more recently downloaded partial title`() = runTest {
        records.value = listOf(download(1, "Dune"), download(2, "Dune Messiah"))
        assertEquals(listOf("book/1", "book/2"), search.find("Dune", labels).map { it.mediaId })
    }

    @Test fun `offline search matches authors and narrators without accents or case`() = runTest {
        records.value = listOf(download(1, "Book", author = "José Writer", narrator = "Jane Reader"))
        coEvery { catalog.page(any(), any(), any()) } throws java.io.IOException("offline")
        assertEquals(listOf("book/1"), search.find("  JOSE writer ", labels).map { it.mediaId })
        assertEquals(listOf("book/1"), search.find("jane reader", labels).map { it.mediaId })
    }

    @Test fun `duplicate editions prefer the active book before history`() = runTest {
        coEvery { catalog.page(any(), any(), any()) } returns AutoCatalog.Page(listOf(entry(1, "Dune"), entry(2, "Dune")), 2)
        coEvery { progress.recent() } returns listOf(AudioProgressEntity(1, 1, 1.0, 10.0, 1, false))
        assertEquals(listOf("book/2", "book/1"), search.find("Dune", labels, currentBookId = 2).map { it.mediaId })
    }

    @Test fun `a downloaded result also on the server is shown once`() = runTest {
        records.value = listOf(download(1, "Dune"))
        coEvery { catalog.page(any(), any(), any()) } returns AutoCatalog.Page(listOf(entry(1, "Dune")), 1)
        assertEquals(1, search.find("Dune", labels).size)
    }

    @Test fun `empty search uses continue listening`() = runTest {
        coEvery { catalog.continueListening(any()) } returns listOf(entry(7, "Current book"))
        assertEquals(listOf("book/7"), search.find(" ", labels).map { it.mediaId })
    }

    @Test fun `incomplete downloads and ebooks never appear as offline results`() = runTest {
        records.value = listOf(download(1, "Dune").copy(isAudiobook = false),
            download(2, "Dune").copy(status = "DOWNLOADING"))
        assertEquals(emptyList<String>(), search.find("Dune", labels).map { it.mediaId })
    }

    @Test fun `server failure without an offline match is not reported as zero results`() = runTest {
        coEvery { catalog.page(any(), any(), any()) } throws java.io.IOException("offline")
        try {
            search.find("Missing book", labels)
            org.junit.Assert.fail("Expected a network failure")
        } catch (_: java.io.IOException) { }
    }
}
