package com.apocalypse03.game2048.ui.timer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameTimerTest {
    private val clock = FakeClock()
    private val timer = GameTimer(clock)

    @Test
    fun startsIdleAtZero() {
        assertEquals(0L, timer.elapsedMs())
        assertFalse(timer.running)
        assertFalse(timer.started)
        clock.advance(5_000)
        assertEquals(0L, timer.elapsedMs())
    }

    @Test
    fun elapsedGrowsWhileRunning() {
        timer.setRunning(true)
        assertTrue(timer.running)
        assertTrue(timer.started)
        clock.advance(1_500)
        assertEquals(1_500L, timer.elapsedMs())
        clock.advance(500)
        assertEquals(2_000L, timer.elapsedMs())
    }

    @Test
    fun accumulatesAcrossSegments() {
        timer.setRunning(true)
        clock.advance(1_000)
        timer.setRunning(false)
        clock.advance(10_000)
        assertEquals(1_000L, timer.elapsedMs())
        assertTrue(timer.started)
        timer.setRunning(true)
        clock.advance(2_500)
        timer.setRunning(false)
        assertEquals(3_500L, timer.elapsedMs())
        assertFalse(timer.running)
    }

    @Test
    fun setRunningIsIdempotent() {
        timer.setRunning(true)
        clock.advance(1_000)
        timer.setRunning(true)
        clock.advance(1_000)
        assertEquals(2_000L, timer.elapsedMs())
        timer.setRunning(false)
        timer.setRunning(false)
        assertEquals(2_000L, timer.elapsedMs())
    }

    @Test
    fun resetReturnsToIdleZero() {
        timer.setRunning(true)
        clock.advance(4_000)
        timer.reset()
        assertEquals(0L, timer.elapsedMs())
        assertFalse(timer.running)
        assertFalse(timer.started)
        clock.advance(4_000)
        assertEquals(0L, timer.elapsedMs())
    }

    @Test
    fun restoreSetsElapsedWithoutRunning() {
        timer.setRunning(true)
        clock.advance(1_000)
        timer.restore(elapsedMs = 5_000, started = true)
        assertEquals(5_000L, timer.elapsedMs())
        assertFalse(timer.running)
        assertTrue(timer.started)
        clock.advance(2_000)
        assertEquals(5_000L, timer.elapsedMs())
        timer.setRunning(true)
        clock.advance(1_000)
        assertEquals(6_000L, timer.elapsedMs())
    }

    @Test
    fun restoreIdle() {
        timer.restore(elapsedMs = 0, started = false)
        assertEquals(0L, timer.elapsedMs())
        assertFalse(timer.started)
        assertFalse(timer.running)
    }
}
