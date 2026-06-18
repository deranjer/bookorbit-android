package com.bookorbit.feature.player

import com.bookorbit.core.model.AudioMetadata
import com.bookorbit.core.model.AudiobookChapter
import com.bookorbit.core.model.BookDetail
import com.bookorbit.core.model.BookFileRef
import org.junit.Assert.assertEquals
import org.junit.Test

/** Covers the playback queue math in [PlaybackQueue]. */
class PlaybackQueueTest {

    private val files = listOf(
        BookFileRef(id = 1, format = "mp3", role = "primary", durationSeconds = 100.0),
        BookFileRef(id = 2, format = "mp3", role = "supplementary", durationSeconds = 200.0),
        BookFileRef(id = 3, format = "mp3", role = "supplementary", durationSeconds = 50.0),
    )

    @Test
    fun `totalDuration sums file durations`() {
        assertEquals(350.0, PlaybackQueue.totalDurationSec(files), 0.0001)
    }

    @Test
    fun `toAbsolute adds preceding file durations`() {
        assertEquals(130.0, PlaybackQueue.toAbsoluteSec(files, index = 1, offsetSec = 30.0), 0.0001)
    }

    @Test
    fun `locateAbsolute finds the containing file and offset`() {
        val loc = PlaybackQueue.locateAbsolute(files, 130.0)
        assertEquals(1, loc.index)
        assertEquals(30.0, loc.offsetSec, 0.0001)
    }

    @Test
    fun `locateAbsolute clamps into the last file`() {
        val loc = PlaybackQueue.locateAbsolute(files, 10_000.0)
        assertEquals(2, loc.index)
    }

    @Test
    fun `percentageFor is whole-book percent`() {
        assertEquals(50.0, PlaybackQueue.percentageFor(files, index = 1, offsetSec = 75.0), 0.0001)
    }

    @Test
    fun `currentChapterIndex returns the last chapter at or before the offset`() {
        val book = BookDetail(
            id = 1, libraryId = 1, libraryName = "L", status = "unread", addedAt = "now",
            audioMetadata = AudioMetadata(
                chapters = listOf(
                    AudiobookChapter("One", 0),
                    AudiobookChapter("Two", 60_000),
                    AudiobookChapter("Three", 120_000),
                ),
            ),
        )
        val chapters = PlaybackQueue.resolveChapters(book)
        assertEquals(1, PlaybackQueue.currentChapterIndex(chapters, 90.0))
        assertEquals(2, PlaybackQueue.currentChapterIndex(chapters, 120.0))
        assertEquals(-1, PlaybackQueue.currentChapterIndex(emptyList(), 90.0))
    }
}
