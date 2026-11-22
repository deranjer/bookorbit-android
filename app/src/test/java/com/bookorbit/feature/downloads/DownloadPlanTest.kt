package com.bookorbit.feature.downloads

import com.bookorbit.core.db.DownloadEntity
import com.bookorbit.core.model.BookDetail
import com.bookorbit.core.model.BookFileRef
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadPlanTest {
    private val book = BookDetail(
        id = 7, libraryId = 1, libraryName = "Test", status = "active", addedAt = "2026-11-22",
        files = listOf(BookFileRef(11, "epub", "primary"), BookFileRef(12, "mp3", "supplementary")),
    )

    private fun entity(selection: String?) = DownloadEntity(
        bookId = book.id, title = null, authors = "", narrators = "", isAudiobook = false,
        format = "epub", sizeBytes = 0, downloadedAt = 0, coverLocalPath = null,
        bookJson = "{}", filesJson = "[]", status = "FAILED", progress = 0.25f,
        selectedFileIdsJson = selection,
    )

    @Test fun `persisted text-only plan never adds paired audio on retry`() {
        assertEquals(listOf(11), DownloadPlan.files(entity("[11]"), book, Json).map { it.id })
    }

    @Test fun `both selection survives status and progress changes`() {
        val plan = entity("[11,12]").copy(status = "DOWNLOADING", progress = 0.5f)
        assertEquals(listOf(11, 12), DownloadPlan.files(plan, book, Json).map { it.id })
    }

    @Test fun `old plans preserve legacy audio selection`() {
        assertEquals(listOf(12), DownloadPlan.files(entity(null), book, Json).map { it.id })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `missing selected file fails rather than substituting another file`() {
        DownloadPlan.files(entity("[99]"), book, Json)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `corrupt selection fails closed`() {
        DownloadPlan.files(entity("not-json"), book, Json)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `persisted empty selection does not become default selection`() {
        DownloadPlan.files(entity("[]"), book, Json)
    }
}
