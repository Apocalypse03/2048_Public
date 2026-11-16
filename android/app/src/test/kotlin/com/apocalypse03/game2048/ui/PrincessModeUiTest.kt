package com.apocalypse03.game2048.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.apocalypse03.game2048.game.Board
import com.apocalypse03.game2048.ui.game.BOARD_TAG
import com.apocalypse03.game2048.ui.timer.FakeClock
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Princess Mode through the UI (DESIGN_SPEC section 11). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class PrincessModeUiTest {

    @get:Rule
    val rule = createComposeRule()

    private val z = listOf(0, 0, 0, 0)
    private val row = Board.of(listOf(listOf(2, 4, 8, 0), z, z, z))
    private val clock = FakeClock()

    private fun princessGame(): AppViewModel {
        val vm = AppViewModel(Random(3), clock).apply { startNewGame() }
        rule.setApp(vm, row)
        val timer = rule.onNodeWithContentDescription("Elapsed time", substring = true)
        repeat(5) {
            timer.performClick()
            clock.advance(300)
        }
        rule.waitForIdle()
        return vm
    }

    private val crown get() = rule.onNodeWithContentDescription("Exit Princess Mode")

    @Test
    fun fiveTimerTapsShowCrownAndHideHint() {
        val vm = princessGame()
        assertTrue(vm.game.princess)
        crown.assertExists()
        rule.onNodeWithText("Swipe to begin.").assertDoesNotExist()
    }

    @Test
    fun swipeDoesNotMoveInPrincess() {
        val vm = princessGame()
        rule.onNodeWithTag(BOARD_TAG).performTouchInput { swipeRight() }
        rule.onNodeWithTag(BOARD_TAG).performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertEquals(row, vm.game.board)
        assertNull("a swipe is not a tap", vm.selected)
    }

    @Test
    fun tapTwoTilesSwapsAndUndoReverts() {
        val vm = princessGame()
        val undo = rule.onNodeWithContentDescription("Undo")
        undo.assertIsNotEnabled()
        rule.onNodeWithTag("tile_0_0").performClick()
        rule.waitForIdle()
        assertEquals(0, vm.selected)
        rule.onNodeWithTag("tile_0_1").performClick()
        rule.waitForIdle()
        assertNull(vm.selected)
        assertEquals(4, vm.game.board[0, 0])
        assertEquals(2, vm.game.board[0, 1])
        undo.assertIsEnabled().performClick()
        rule.waitForIdle()
        assertEquals(row, vm.game.board)
        undo.assertIsNotEnabled()
    }

    @Test
    fun crownTapExits() {
        val vm = princessGame()
        crown.performClick()
        rule.mainClock.advanceTimeBy(500)
        rule.waitForIdle()
        assertFalse(vm.game.princess)
        crown.assertDoesNotExist()
        rule.onNodeWithText("Swipe to begin.").assertExists()
    }
}
