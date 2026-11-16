package com.apocalypse03.game2048.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Curves of the board move animation (DESIGN_SPEC section 17); progress 0..1 slide, 1..2 settle. */
class MoveAnimationCurveTest {

    private val eps = 1e-4f

    private fun steps(from: Float, to: Float, n: Int = 50) = (0..n).map { from + (to - from) * it / n }

    @Test
    fun durations() {
        assertEquals(120f, SLIDE_MS, 0f)
        assertEquals(150f, SETTLE_MS, 0f)
    }

    @Test
    fun slideIsEaseOutFromOneToZero() {
        assertEquals(1f, slideRemaining(0f), eps)
        assertEquals(0f, slideRemaining(1f), eps)
        assertEquals(0f, slideRemaining(1.5f), eps)
        assertEquals(0f, slideRemaining(2f), eps)
        val v = steps(0f, 1f).map(::slideRemaining)
        assertTrue("monotonic", v.zipWithNext().all { (a, b) -> b <= a + eps })
        // Ease-out: most of the distance is covered early (linear would leave 0.75 and 0.5).
        assertTrue("at 25 %: ${slideRemaining(0.25f)}", slideRemaining(0.25f) < 0.45f)
        assertTrue("at 50 %: ${slideRemaining(0.5f)}", slideRemaining(0.5f) < 0.2f)
    }

    @Test
    fun popRisesForFortyPercentThenFalls() {
        assertEquals(1f, popScale(0f), eps)
        assertEquals(1f, popScale(0.99f), eps)
        assertEquals(1f, popScale(1f), eps)
        assertEquals(1.14f, POP_SCALE, 0f)
        assertEquals(POP_SCALE, popScale(1.4f), 1e-3f)
        assertEquals(1f, popScale(2f), eps)
        val rise = steps(1f, 1.4f).map(::popScale)
        val fall = steps(1.4f, 2f).map(::popScale)
        assertTrue("rise monotonic", rise.zipWithNext().all { (a, b) -> b >= a - eps })
        assertTrue("fall monotonic", fall.zipWithNext().all { (a, b) -> b <= a + eps })
        assertTrue("never past the peak", (rise + fall).all { it <= POP_SCALE + eps && it >= 1f - eps })
        // Eased, not a linear triangle: the rise is ahead of linear, the fall starts slowly.
        assertTrue(popScale(1.2f) > 1f + (POP_SCALE - 1f) * 0.6f)
        assertTrue(popScale(1.5f) > 1f + (POP_SCALE - 1f) * (1f - 0.1f / 0.6f))
    }

    @Test
    fun spawnIsInvisibleUntilAQuarterOfTheSettlePhase() {
        for (p in steps(0f, 1.25f)) {
            assertEquals("alpha at $p", 0f, spawnAlpha(p), eps)
            assertEquals("scale at $p", 0.6f, spawnScale(p), eps)
        }
        assertTrue(spawnAlpha(1.3f) > 0f)
    }

    @Test
    fun spawnGrowsAndFadesInOverItsWindow() {
        // Window 1.25..2: alpha is full from its middle, scale eases out to 1 at its end.
        assertEquals(1f, spawnAlpha(1.625f), eps)
        assertEquals(1f, spawnAlpha(2f), eps)
        assertEquals(1f, spawnScale(2f), eps)
        val a = steps(1.25f, 2f).map(::spawnAlpha)
        val s = steps(1.25f, 2f).map(::spawnScale)
        assertTrue("alpha monotonic", a.zipWithNext().all { (x, y) -> y >= x - eps })
        assertTrue("scale monotonic", s.zipWithNext().all { (x, y) -> y >= x - eps })
        assertTrue("scale in 0.6..1", s.all { it >= 0.6f - eps && it <= 1f + eps })
        assertTrue("ease-out: ${spawnScale(1.625f)}", spawnScale(1.625f) > 0.6f + 0.4f * 0.8f)
    }
}
