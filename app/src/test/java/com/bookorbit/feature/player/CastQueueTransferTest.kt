package com.bookorbit.feature.player

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
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
}
