package com.apocalypse03.game2048.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import com.apocalypse03.game2048.game.Board
import com.apocalypse03.game2048.ui.game.BOARD_TAG
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Window-wide swipe input through AppRoot (DESIGN_SPEC section 7). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class FullScreenSwipeTest {

    @get:Rule
    val rule = createComposeRule()

    private val z = listOf(0, 0, 0, 0)

    /** One 2 at (1,1): every direction is a valid move. */
    private val single = Board.of(listOf(z, listOf(0, 2, 0, 0), z, z))

    private fun game(): AppViewModel =
        AppViewModel(Random(3)).apply {
            startNewGame()
            restoreGame(single)
        }.also { rule.setApp(it) }

    /** Swipe on the window root from ([fx], [fy]) (fractions of its size) by ([dx], [dy]) px. */
    private fun rootSwipe(fx: Float, fy: Float, dx: Float, dy: Float) {
        rule.onRoot().performTouchInput {
            val start = Offset(width * fx, height * fy)
            swipe(start, start + Offset(dx, dy))
        }
        rule.waitForIdle()
    }

    @Test
    fun swipeInTheHeaderAreaMovesTheBoard() {
        val vm = game()
        // y 20 % of the window: on the timer row (y 381-451 rp), above the board.
        rootSwipe(0.6f, 0.2f, dx = -150f, dy = 0f)
        assertEquals(1, vm.game.moves)
        assertEquals(2, vm.game.board[1, 0])
    }

    @Test
    fun swipeBelowTheBoardMovesTheBoard() {
        val vm = game()
        rootSwipe(0.3f, 0.85f, dx = 150f, dy = 0f)
        assertEquals(1, vm.game.moves)
        assertEquals(2, vm.game.board[1, 3])
    }

    @Test
    fun swipeInTheLetterboxMovesTheBoard() {
        val vm = game()
        // 411 x 891 dp: the 989:2048 frame is 851 dp tall, leaving 20 dp bands above and below it.
        rule.onRoot().performTouchInput { swipe(Offset(width * 0.3f, 5f), Offset(width * 0.7f, 5f)) }
        rule.waitForIdle()
        assertEquals(1, vm.game.moves)
        assertEquals(2, vm.game.board[1, 3])
    }

    @Test
    fun swipeStartingOnUndoMovesAndDoesNotUndo() {
        val vm = game()
        rule.onNodeWithTag(BOARD_TAG).performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertEquals(1, vm.game.moves)
        assertTrue(vm.game.canUndo)
        rule.onNodeWithContentDescription("Undo").performTouchInput { swipe(center, center + Offset(0f, 300f)) }
        rule.waitForIdle()
        // A second move was made; an undo would have restored the starting board with 0 moves.
        assertEquals(2, vm.game.moves)
        assertTrue(vm.game.board != single)
    }

    @Test
    fun tapOnUndoStillUndoes() {
        val vm = game()
        rule.onNodeWithTag(BOARD_TAG).performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Undo").performClick()
        rule.waitForIdle()
        assertEquals(single, vm.game.board)
    }

    @Test
    fun swipeStartingOnBackMovesAndDoesNotNavigate() {
        val vm = game()
        rule.onNodeWithContentDescription("Back").performTouchInput { swipe(center, center + Offset(200f, 0f)) }
        rule.waitForIdle()
        assertEquals(Screen.GAME, vm.screen)
        assertEquals(1, vm.game.moves)
        assertEquals(2, vm.game.board[1, 3])
    }

    @Test
    fun tapOnBackStillNavigates() {
        val vm = game()
        rule.onNodeWithContentDescription("Back").performClick()
        rule.waitForIdle()
        assertEquals(Screen.HOME, vm.screen)
        assertEquals(0, vm.game.moves)
    }

    @Test
    fun swipeStartingOnThePaletteToggleMovesAndDoesNotOpenTheBar() {
        val vm = game()
        rule.onNodeWithContentDescription("Palette").performTouchInput { swipe(center, center + Offset(0f, 300f)) }
        rule.waitForIdle()
        assertEquals(1, vm.game.moves)
        assertEquals(2, vm.game.board[3, 1])
        // The bar stayed closed: its swatches are only composed while it is open.
        rule.onNodeWithContentDescription("Theme Lavender").assertDoesNotExist()
    }

    @Test
    fun tapOnThePaletteToggleStillOpensTheBar() {
        val vm = game()
        rule.onNodeWithContentDescription("Palette").performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Theme Lavender").assertExists()
        assertEquals(0, vm.game.moves)
    }

    @Test
    fun swipeWithThePaletteOpenDoesNotMove() {
        val vm = game()
        rule.onNodeWithContentDescription("Palette").performClick()
        rule.waitForIdle()
        // The scrim takes the gesture (its release closes the bar, as before); the board stays.
        rule.onNodeWithTag(BOARD_TAG).performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertEquals(0, vm.game.moves)
        assertEquals(single, vm.game.board)
    }

    @Test
    fun swipeBelowTheBoardWithThePaletteOpenDoesNotMove() {
        val vm = game()
        rule.onNodeWithContentDescription("Palette").performClick()
        rule.waitForIdle()
        rootSwipe(0.3f, 0.85f, dx = 150f, dy = 0f)
        assertEquals(0, vm.game.moves)
        assertEquals(single, vm.game.board)
    }

    @Test
    fun swipeOffTheGameScreenDoesNothing() {
        val vm = game()
        rule.onNodeWithContentDescription("Back").performClick()
        rule.waitForIdle()
        rootSwipe(0.6f, 0.2f, dx = -150f, dy = 0f)
        assertEquals(Screen.HOME, vm.screen)
        assertEquals(0, vm.game.moves)
        assertEquals(single, vm.game.board)
    }
}
