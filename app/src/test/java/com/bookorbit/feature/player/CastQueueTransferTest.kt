package com.bookorbit.feature.player

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.bookorbit.feature.cast.CastProxyServer
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Test

@UnstableApi
class CastQueueTransferTest {
    private val local = mockk<Player>(relaxed = true)
    private val cast = mockk<Player>(relaxed = true)
    private val items = listOf(mockk<MediaItem>(), mockk<MediaItem>())
    private var localPlaying = true
    private var castPlaying = false

    init {
        every { local.playWhenReady } answers { localPlaying }
        every { local.pause() } answers { localPlaying = false }
        every { local.playWhenReady = any() } answers { localPlaying = firstArg() }
        every { local.currentMediaItemIndex } returns 1
        every { local.currentPosition } returns 75_000L
        every { cast.playWhenReady = any() } answers { castPlaying = firstArg() }
    }

    @Test fun `playing handoff silences phone and supplies resume position in the initial queue load`() {
        transferQueueToCast(local, cast, items)
        assertFalse("The phone must stop producing audio", localPlaying)
        assertTrue(castPlaying)
        verify(exactly = 1) { cast.setMediaItems(items, 1, 75_000L) }
        verify(exactly = 0) { cast.seekTo(any<Int>(), any<Long>()) }
    }

    @Test fun `paused handoff stays paused on both players`() {
        localPlaying = false
        transferQueueToCast(local, cast, items)
        assertFalse(localPlaying)
        assertFalse(castPlaying)
    }

    @Test fun `failed queue load restores original local play state`() {
        every { cast.setMediaItems(any(), any<Int>(), any<Long>()) } throws IllegalArgumentException("Cannot load")
        try {
            transferQueueToCast(local, cast, items)
            fail("Expected load failure")
        } catch (_: IllegalArgumentException) { }
        assertTrue(localPlaying)
        assertFalse(castPlaying)
    }

    @Test fun `disconnect during replacement restores newly requested book and resume point`() {
        val oldBook = listOf(mockk<MediaItem>())
        val queue = CastProxyServer.SourceQueue(items, items, 1, 42_000)
        assertEquals(1 to 42_000L, castReturnPosition(queue, oldBook, 0, 900_000))
    }

    @Test fun `disconnect from current queue uses receiver file index and offset`() {
        val realItems = (0..1).map { MediaItem.Builder().setMediaId("$it").build() }
        val queue = CastProxyServer.SourceQueue(realItems, realItems, 0, 0)
        assertEquals(1 to 80_000L, castReturnPosition(queue, realItems, 1, 80_000))
    }

    @Test fun `127 item queue with unloaded distant windows still matches its active file`() {
        val originals = (0 until 127).map { MediaItem.Builder().setMediaId("$it").build() }
        val partial = originals.mapIndexed { index, item -> if (index == 40) item else MediaItem.EMPTY }
        assertTrue(matchesCurrentQueueItem(originals, partial.size, 40, partial[40]))
        assertFalse(matchesCurrentQueueItem(originals, partial.size, 41, partial[41]))
        val queue = CastProxyServer.SourceQueue(originals, originals, 0, 0)
        assertEquals(40 to 15_000L, castReturnPosition(queue, partial, 40, 15_000))
    }
}
