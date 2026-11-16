package com.apocalypse03.game2048.ui.game

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.apocalypse03.game2048.game.Direction
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** [swipeToMove]: threshold, one move per gesture, flick, disabled and consumed gestures. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class SwipeToMoveTest {

    @get:Rule
    val rule = createComposeRule()

    private val dirs = mutableListOf<Direction>()
    private var clicks = 0
    private var enabled by mutableStateOf(true)

    private fun show(child: @Composable () -> Unit = {}) {
        rule.setContent {
            Box(Modifier.fillMaxSize().testTag("area").swipeToMove(enabled) { dirs += it }) { child() }
        }
        rule.waitForIdle()
    }

    private fun touch(block: TouchInjectionScope.() -> Unit) {
        rule.onNodeWithTag("area").performTouchInput(block)
        rule.waitForIdle()
    }

    private fun TouchInjectionScope.px(dp: Int): Float = dp.dp.toPx()

    @Test
    fun firesOncePastTwentyDp() {
        show()
        touch {
            down(center)
            moveBy(Offset(px(19), 0f), delayMillis = 200)
        }
        assertEquals("below the threshold", emptyList<Direction>(), dirs)
        touch { moveBy(Offset(px(2), 0f), delayMillis = 200) }
        assertEquals(listOf(Direction.RIGHT), dirs)
        touch { up() }
        assertEquals(listOf(Direction.RIGHT), dirs)
    }

    @Test
    fun eachDirectionFollowsTheDominantAxis() {
        show()
        val cases = listOf(
            Offset(-40f, 10f) to Direction.LEFT,
            Offset(40f, -10f) to Direction.RIGHT,
            Offset(10f, -40f) to Direction.UP,
            Offset(-10f, 40f) to Direction.DOWN,
        )
        for ((d, _) in cases) {
            touch {
                down(center)
                moveBy(Offset(px(d.x.toInt()), px(d.y.toInt())), delayMillis = 200)
                up()
            }
        }
        assertEquals(cases.map { it.second }, dirs)
    }

    @Test
    fun oneMovePerGestureHoweverFarItTravels() {
        show()
        touch {
            down(center)
            moveBy(Offset(px(30), 0f))
            moveBy(Offset(px(60), 0f))
            moveBy(Offset(px(-150), 0f))
            moveBy(Offset(0f, px(120)))
            up()
        }
        assertEquals(listOf(Direction.RIGHT), dirs)
        // The next gesture fires again.
        touch {
            down(center)
            moveBy(Offset(0f, px(-30)))
            up()
        }
        assertEquals(listOf(Direction.RIGHT, Direction.UP), dirs)
    }

    @Test
    fun disabledDoesNothing() {
        enabled = false
        show()
        touch {
            down(center)
            moveBy(Offset(px(80), 0f))
            up()
        }
        assertEquals(emptyList<Direction>(), dirs)
        // Enabling it later makes the same swipe move.
        enabled = true
        rule.waitForIdle()
        touch {
            down(center)
            moveBy(Offset(px(80), 0f))
            up()
        }
        assertEquals(listOf(Direction.RIGHT), dirs)
    }

    @Test
    fun fastFlickBelowTheThresholdFires() {
        show()
        // 15 dp in 15 ms (1000 dp/s): under 20 dp of travel, over 10 dp and 400 dp/s.
        touch {
            down(center)
            repeat(3) { moveBy(Offset(px(-5), 0f), delayMillis = 5) }
        }
        assertEquals("nothing before the release", emptyList<Direction>(), dirs)
        touch { up() }
        assertEquals(listOf(Direction.LEFT), dirs)
    }

    @Test
    fun slowShortDragDoesNotFire() {
        show()
        // The same 15 dp, but over 600 ms.
        touch {
            down(center)
            repeat(3) { moveBy(Offset(px(-5), 0f), delayMillis = 200) }
            up()
        }
        assertEquals(emptyList<Direction>(), dirs)
    }

    @Test
    fun fastFlickUnderTenDpDoesNotFire() {
        show()
        touch {
            down(center)
            repeat(3) { moveBy(Offset(0f, px(3)), delayMillis = 3) }
            up()
        }
        assertEquals(emptyList<Direction>(), dirs)
    }

    @Test
    fun changesConsumedByAChildDoNotFire() {
        show {
            Box(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent().changes.forEach { it.consume() }
                    }
                },
            )
        }
        touch {
            down(center)
            moveBy(Offset(px(80), 0f))
            moveBy(Offset(px(80), 0f))
            up()
        }
        assertEquals(emptyList<Direction>(), dirs)
    }

    @Test
    fun tapOnAChildButtonStillClicksAndDoesNotMove() {
        show { Box(Modifier.size(100.dp).testTag("btn").clickable { clicks++ }) }
        rule.onNodeWithTag("btn").performTouchInput { click() }
        rule.waitForIdle()
        assertEquals(1, clicks)
        assertEquals(emptyList<Direction>(), dirs)
    }

    @Test
    fun swipeStartingOnAChildButtonMovesAndDoesNotClick() {
        show { Box(Modifier.size(100.dp).testTag("btn").clickable { clicks++ }) }
        // Stays inside the 100 dp button the whole way, so only the consumption cancels its click.
        rule.onNodeWithTag("btn").performTouchInput {
            down(Offset(px(10), centerY))
            moveBy(Offset(px(30), 0f), delayMillis = 200)
            moveBy(Offset(px(30), 0f), delayMillis = 200)
            up()
        }
        rule.waitForIdle()
        assertEquals(listOf(Direction.RIGHT), dirs)
        assertEquals(0, clicks)
    }
}
