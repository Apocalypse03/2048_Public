package com.apocalypse03.game2048.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import com.apocalypse03.game2048.game.Board
import com.apocalypse03.game2048.game.GameStatus
import com.apocalypse03.game2048.ui.game.BOARD_TAG
import com.apocalypse03.game2048.ui.game.GAME_TAG
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class GameplayTest {

    @get:Rule
    val rule = createComposeRule()

    private val z = listOf(0, 0, 0, 0)

    /** Row 0 = 2,2,4,8: one LEFT gives 4,4,8 at the start; two would give 8,8. */
    private val row = Board.of(listOf(listOf(2, 2, 4, 8), z, z, z))
    private val single = Board.of(listOf(z, listOf(0, 2, 0, 0), z, z))

    private fun game(b: Board, status: GameStatus = GameStatus.READY): AppViewModel =
        AppViewModel(Random(3)).apply {
            startNewGame()
            restoreGame(b, status = status)
        }.also { rule.setApp(it) }

    private val boardNode get() = rule.onNodeWithTag(BOARD_TAG)
    private val gameNode get() = rule.onNodeWithTag(GAME_TAG)

    @Test
    fun swipeLeftMovesOnceAndUpdatesDisplay() {
        val vm = game(row)
        assertEquals(2, tiles("2"))
        assertEquals(1, tiles("4"))
        rule.onNodeWithText("Best 0").assertExists()
        boardNode.performTouchInput { swipeLeft() }
        rule.waitForIdle()
        val b = vm.game.board
        assertEquals(listOf(4, 4, 8), listOf(b[0, 0], b[0, 1], b[0, 2]))
        assertEquals(4, vm.game.score)
        rule.onNodeWithText("Best 4").assertExists()
        // Displayed tiles match the new board: two 4s at least (plus a possible spawned 4).
        assertEquals(b.toIntArray().count { it == 4 }, tiles("4"))
        assertEquals(b.toIntArray().count { it == 2 }, tiles("2"))
    }

    private fun tiles(text: String) =
        rule.onAllNodes(hasText(text) and hasAnyAncestor(hasTestTag(BOARD_TAG))).fetchSemanticsNodes().size

    @Test
    fun swipeEachDirection() {
        val vm = game(single)
        val cases = listOf("right" to (1 to 3), "left" to (1 to 0), "down" to (3 to 1), "up" to (0 to 1))
        for ((dir, cell) in cases) {
            rule.runOnIdle { vm.restoreGame(single) }
            rule.waitForIdle()
            boardNode.performTouchInput {
                when (dir) {
                    "right" -> swipeRight()
                    "left" -> swipeLeft()
                    "down" -> swipeDown()
                    else -> swipeUp()
                }
            }
            rule.waitForIdle()
            assertEquals("swipe $dir", 2, vm.game.board[cell.first, cell.second])
            assertEquals("swipe $dir", GameStatus.PLAYING, vm.game.status)
        }
    }

    @Test
    fun arrowKeyMoves() {
        val vm = game(row)
        gameNode.assertIsFocused()
        gameNode.performKeyInput { pressKey(Key.DirectionLeft) }
        rule.waitForIdle()
        assertEquals(4, vm.game.board[0, 0])
        assertEquals(4, vm.game.score)
    }

    @Test
    fun dpadKeysMapToDirections() {
        val vm = game(single)
        gameNode.performKeyInput { pressKey(Key.DirectionDown) }
        rule.waitForIdle()
        assertEquals(2, vm.game.board[3, 1])
        rule.runOnIdle { vm.restoreGame(single) }
        gameNode.performKeyInput { pressKey(Key.DirectionRight) }
        rule.waitForIdle()
        assertEquals(2, vm.game.board[1, 3])
    }

    @Test
    fun hintDisappearsAfterFirstMove() {
        game(row)
        rule.onNodeWithText("Swipe to begin.").assertExists()
        boardNode.performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.onNodeWithText("Swipe to begin.").assertDoesNotExist()
        rule.onNodeWithText("Game over.").assertDoesNotExist()
    }

    @Test
    fun undoButtonFlow() {
        val vm = game(row)
        val undo = rule.onNodeWithContentDescription("Undo")
        undo.assertExists().assertIsNotEnabled()
        undo.performClick()
        rule.waitForIdle()
        assertEquals(row, vm.game.board)
        boardNode.performTouchInput { swipeLeft() }
        rule.waitForIdle()
        undo.assertIsEnabled().performClick()
        rule.waitForIdle()
        assertEquals(row, vm.game.board)
        assertEquals(0, vm.game.score)
        undo.assertExists().assertIsNotEnabled()
    }

    @Test
    fun overShowsGameOverAndIgnoresInput() {
        val full = Board.of(
            listOf(listOf(2, 4, 2, 4), listOf(4, 2, 4, 2), listOf(2, 4, 2, 4), listOf(4, 2, 4, 2)),
        )
        val vm = game(full, GameStatus.OVER)
        rule.onNodeWithText("Game over.").assertExists()
        rule.onNodeWithText("Swipe to begin.").assertDoesNotExist()
        val before = vm.game
        boardNode.performTouchInput { swipeLeft() }
        boardNode.performTouchInput { swipeDown() }
        gameNode.performKeyInput { pressKey(Key.DirectionUp) }
        rule.waitForIdle()
        assertEquals(before, vm.game)
        rule.onNodeWithText("Game over.").assertExists()
    }

    @Test
    fun themeChangeKeepsBoard() {
        val vm = game(row)
        boardNode.performTouchInput { swipeLeft() }
        rule.waitForIdle()
        val before = vm.game
        rule.onNodeWithContentDescription("Palette").performClick()
        rule.onNodeWithContentDescription("Theme Blue").tapSwatch()
        rule.waitForIdle()
        assertEquals(before, vm.game)
        assertEquals(Screen.GAME, vm.screen)
    }
}
