package com.apocalypse03.game2048.ui.game

import com.apocalypse03.game2048.game.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SwipeDirectionTest {
    private val t = 50f

    @Test
    fun fourDirections() {
        assertEquals(Direction.LEFT, swipeDirection(-60f, 0f, t))
        assertEquals(Direction.RIGHT, swipeDirection(60f, 0f, t))
        assertEquals(Direction.UP, swipeDirection(0f, -60f, t))
        assertEquals(Direction.DOWN, swipeDirection(0f, 60f, t))
    }

    @Test
    fun exactlyAtThresholdFires() {
        assertEquals(Direction.RIGHT, swipeDirection(50f, 0f, t))
        assertEquals(Direction.DOWN, swipeDirection(0f, 50f, t))
    }

    @Test
    fun belowThresholdIsNull() {
        assertNull(swipeDirection(0f, 0f, t))
        assertNull(swipeDirection(-49f, 30f, t))
        assertNull(swipeDirection(40f, -49.9f, t))
    }

    @Test
    fun diagonalPicksDominantAxis() {
        assertEquals(Direction.LEFT, swipeDirection(-80f, 60f, t))
        assertEquals(Direction.UP, swipeDirection(30f, -90f, t))
        assertEquals(Direction.DOWN, swipeDirection(-70f, 71f, t))
        assertEquals(Direction.RIGHT, swipeDirection(70f, -70f, t))
    }

    @Test
    fun minorAxisNeverDecides() {
        assertNull(swipeDirection(45f, 30f, t))
    }
}
