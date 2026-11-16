package com.apocalypse03.game2048.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.apocalypse03.game2048.game.Board
import com.apocalypse03.game2048.game.GameStatus
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class ResumeFlowTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val z = listOf(0, 0, 0, 0)
    private val board = Board.of(listOf(listOf(2, 2, 4, 8), z, z, z))

    private fun resume() = rule.onNodeWithContentDescription("Resume")
    private fun dialog() = rule.onNodeWithTag("confirm_dialog")
    private fun newGame() = rule.onNodeWithContentDescription("New Game")

    private fun idle() = rule.waitForIdle()

    /** Home screen with a game in progress (PLAYING). */
    private fun homeInProgress(): AppViewModel {
        val vm = AppViewModel(Random(3)).apply {
            startNewGame()
            restoreGame(board, status = GameStatus.PLAYING)
            goHome()
        }
        rule.setContent { AppRoot(vm) }
        idle()
        return vm
    }

    private fun back() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        idle()
    }

    @Test
    fun freshAppHasNoResumeAndNewGameStartsDirectly() {
        val vm = AppViewModel()
        rule.setContent { AppRoot(vm) }
        idle()
        resume().assertDoesNotExist()
        newGame().performClick()
        idle()
        assertEquals(Screen.GAME, vm.screen)
        dialog().assertDoesNotExist()
    }

    @Test
    fun resumeAppearsAfterMoveAndRestoresSameBoard() {
        val vm = AppViewModel(Random(3)).apply { startNewGame() }
        rule.setContent { AppRoot(vm) }
        idle()
        rule.runOnUiThread {
            vm.restoreGame(board)
            vm.move(com.apocalypse03.game2048.game.Direction.LEFT)
        }
        idle()
        val moved = vm.game.board
        rule.onNodeWithContentDescription("Back").performClick()
        idle()
        assertEquals(Screen.HOME, vm.screen)
        resume().assertExists()
        resume().performClick()
        idle()
        assertEquals(Screen.GAME, vm.screen)
        assertEquals(moved, vm.game.board)
    }

    @Test
    fun newGameAsksThenNoKeepsGameYesStartsFresh() {
        val vm = homeInProgress()
        val before = vm.game
        newGame().performClick()
        idle()
        dialog().assertExists()
        assertEquals(Screen.HOME, vm.screen)
        assertEquals(before, vm.game)
        rule.onNodeWithContentDescription("No").performClick()
        idle()
        dialog().assertDoesNotExist()
        assertEquals(Screen.HOME, vm.screen)
        resume().assertExists()
        newGame().performClick()
        idle()
        rule.onNodeWithContentDescription("Yes").performClick()
        idle()
        dialog().assertDoesNotExist()
        assertEquals(Screen.GAME, vm.screen)
        assertEquals(GameStatus.READY, vm.game.status)
    }

    @Test
    fun systemBackClosesDialogOnly() {
        val vm = homeInProgress()
        val before = vm.game
        newGame().performClick()
        idle()
        dialog().assertExists()
        back()
        dialog().assertDoesNotExist()
        assertEquals(Screen.HOME, vm.screen)
        assertEquals(false, vm.confirmNewGame)
        assertEquals(before, vm.game)
        resume().assertExists()
    }

    @Test
    fun dialogHidesHomeFromAccessibility() {
        homeInProgress()
        newGame().performClick()
        idle()
        newGame().assertDoesNotExist()
        resume().assertDoesNotExist()
        rule.onNodeWithContentDescription("Palette").assertDoesNotExist()
        rule.onNodeWithContentDescription("Yes").assertHasClickAction()
        rule.onNodeWithContentDescription("No").assertHasClickAction()
        rule.onNodeWithContentDescription("No").performClick()
        idle()
        newGame().assertExists()
        resume().assertExists()
    }

    @Test
    fun scrimTapClosesButCardTapDoesNot() {
        val vm = homeInProgress()
        newGame().performClick()
        idle()
        dialog().performTouchInput { click(center) }
        idle()
        dialog().assertExists()
        assertEquals(true, vm.confirmNewGame)
        rule.onRoot().performTouchInput { click(Offset(width * 0.5f, height * 0.04f)) }
        idle()
        dialog().assertDoesNotExist()
        assertEquals(Screen.HOME, vm.screen)
    }

    @Test
    fun dialogMessageShown() {
        homeInProgress()
        newGame().performClick()
        idle()
        rule.onNodeWithText("There is still a game in progress. Are you sure you want to start a new game?")
            .assertExists()
    }
}
