package com.apocalypse03.game2048.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import com.apocalypse03.game2048.game.Board
import com.apocalypse03.game2048.game.GameStatus
import com.apocalypse03.game2048.ui.game.BOARD_TAG
import com.apocalypse03.game2048.ui.timer.FakeClock
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Mode selector and the 6x6 (2048+) board through the UI. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class ModeUiTest {

    @get:Rule
    val rule = createComposeRule()

    private val clock = FakeClock()

    private val tileNodes = SemanticsMatcher("tile_r_c tag") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("tile_") == true
    }

    private fun tileCount() = rule.onAllNodes(tileNodes).fetchSemanticsNodes().size

    /** 6x6 board with (index to value) entries, rest empty. */
    private fun board6(vararg values: Pair<Int, Int>) =
        Board.of(IntArray(36).apply { values.forEach { (i, v) -> this[i] = v } })

    private fun plusGame(board: Board): AppViewModel {
        val vm = AppViewModel(Random(3), clock).apply {
            stepMode(1)
            startNewGame()
        }
        rule.setApp(vm, board)
        return vm
    }

    @Test
    fun selectorSwitchesToPlus() {
        rule.setApp(AppViewModel())
        rule.onNodeWithText("Normal").assertExists()
        rule.onNodeWithContentDescription("Next mode").performClick()
        rule.onNodeWithText("2048⁺").assertExists()
        rule.onNodeWithText("Normal").assertDoesNotExist()
    }

    @Test
    fun newGameInPlusShows36Tiles() {
        val vm = AppViewModel(Random(3))
        rule.setApp(vm)
        rule.onNodeWithContentDescription("Next mode").performClick()
        rule.onNodeWithText("New Game").performClick()
        rule.waitForIdle()
        assertEquals(Screen.GAME, vm.screen)
        assertEquals(36, tileCount())
        rule.onNodeWithTag("tile_5_5").assertExists()
        rule.onNodeWithTag("tile_6_0").assertDoesNotExist()
        rule.onNodeWithText("Swipe to begin.").assertExists()
    }

    @Test
    fun normalNewGameShows16Tiles() {
        rule.setApp(AppViewModel(Random(3)))
        rule.onNodeWithText("New Game").performClick()
        rule.waitForIdle()
        assertEquals(16, tileCount())
        rule.onNodeWithTag("tile_4_0").assertDoesNotExist()
    }

    @Test
    fun swipeMovesOn6x6() {
        val vm = plusGame(board6(5 to 2, 4 to 2))
        rule.onNodeWithTag(BOARD_TAG).performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertEquals(4, vm.game.board[0, 0])
        assertEquals(4, vm.game.score)
        assertEquals(GameStatus.PLAYING, vm.game.status)
        rule.onNodeWithTag(BOARD_TAG).performTouchInput { swipeDown() }
        rule.waitForIdle()
        assertEquals(4, vm.game.board[5, 0])
    }

    @Test
    fun princessTapOn6x6SelectsTappedTile() {
        val vm = plusGame(board6(35 to 2, 15 to 8))
        val timer = rule.onNodeWithContentDescription("Elapsed time", substring = true)
        repeat(5) {
            timer.performClick()
            clock.advance(300)
        }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Exit Princess Mode").assertExists()
        rule.onNodeWithTag("tile_5_5").performClick()
        rule.waitForIdle()
        assertEquals(35, vm.selected)
        rule.onNodeWithTag("tile_2_3").performClick()
        rule.waitForIdle()
        assertNull(vm.selected)
        assertEquals(2, vm.game.board[2, 3])
        assertEquals(8, vm.game.board[5, 5])
    }
}
