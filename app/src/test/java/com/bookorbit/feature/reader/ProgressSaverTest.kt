package com.bookorbit.feature.reader

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressSaverTest {
    private fun TestScope.saver(saved: MutableList<Int>) =
        ProgressSaver<Int>(backgroundScope, intervalMs = 2_000, clock = { currentTime }) { saved += it }

    @Test
    fun `the first turn saves at once and quick turns after it are held`() = runTest {
        val saved = mutableListOf<Int>()
        val s = saver(saved)
        s.offer(17)
        s.offer(18)
        s.offer(19)
        assertEquals(listOf(17), saved)
    }

    @Test
    fun `the last held turn is saved when the interval runs out`() = runTest {
        val saved = mutableListOf<Int>()
        val s = saver(saved)
        s.offer(17)
        advanceTimeBy(500)
        s.offer(18)
        advanceTimeBy(500)
        s.offer(19)
        advanceTimeBy(999)
        runCurrent()
        assertEquals(listOf(17), saved)
        advanceTimeBy(2)
        runCurrent()
        assertEquals(listOf(17, 19), saved)
    }

    @Test
    fun `flush saves the held turn straight away, once`() = runTest {
        val saved = mutableListOf<Int>()
        val s = saver(saved)
        s.offer(17)
        s.offer(20)
        s.flush()
        assertEquals(listOf(17, 20), saved)
        advanceTimeBy(5_000)
        runCurrent()
        s.flush()
        assertEquals(listOf(17, 20), saved)
    }

    @Test
    fun `a turn after a quiet spell saves at once`() = runTest {
        val saved = mutableListOf<Int>()
        val s = saver(saved)
        s.offer(17)
        advanceTimeBy(3_000)
        s.offer(18)
        assertEquals(listOf(17, 18), saved)
    }
}
