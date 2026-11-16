package com.apocalypse03.game2048.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.apocalypse03.game2048.game.Board
import com.apocalypse03.game2048.game.Direction
import com.apocalypse03.game2048.game.GameStatus
import com.apocalypse03.game2048.ui.timer.FakeClock
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Selector, dialog and Best label through the UI. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class ModeFlowEdgeTest {
    @get:Rule
    val rule = createComposeRule()

    private val clock = FakeClock()

    private fun board6() = Board.of(IntArray(36).apply { this[0] = 2; this[1] = 2 })

    /** VM with a moved 6x6 game at Home, selector Normal. */
    private fun plusAtHomeSelectorNormal(): AppViewModel {
        val vm = AppViewModel(Random(3), clock)
        vm.stepMode(1)
        vm.startNewGame()
        vm.restoreGame(board6())
        vm.move(Direction.LEFT)
        vm.goHome()
        vm.stepMode(-1)
        return vm
    }

    @Test
    fun arrowsHiddenAtEnds() {
        rule.setApp(AppViewModel())
        rule.onNodeWithContentDescription("Previous mode").assertDoesNotExist()
        rule.onNodeWithContentDescription("Next mode").assertExists()
        rule.onNodeWithContentDescription("Next mode").performClick()
        rule.onNodeWithContentDescription("Next mode").assertDoesNotExist()
        rule.onNodeWithContentDescription("Previous mode").assertExists()
    }

    @Test
    fun resumeOfPlusGameShowsPlusBestOnGameScreen() {
        val settings = com.apocalypse03.game2048.ui.data.InMemorySettingsStore(best = 5000)
        val vm = AppViewModel(
            Random(3), clock, androidx.lifecycle.SavedStateHandle(), settings,
            com.apocalypse03.game2048.ui.data.InMemoryGameStore(),
        )
        vm.stepMode(1)
        vm.startNewGame()
        vm.restoreGame(board6())
        vm.move(Direction.LEFT)
        vm.goHome()
        vm.stepMode(-1)
        rule.setApp(vm)
        rule.onNodeWithText("Resume").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Best 4").assertExists()
    }

    @Test
    fun dialogYesInNormalShowsNormalBestAndNoKeepsPlus() {
        val vm = plusAtHomeSelectorNormal()
        rule.setApp(vm)
        rule.onNodeWithText("New Game").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("No").performClick()
        rule.waitForIdle()
        assertEquals(6, vm.game.size)
        assertEquals(Screen.HOME, vm.screen)
        rule.onNodeWithText("New Game").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Yes").performClick()
        rule.waitForIdle()
        assertEquals(4, vm.game.size)
        assertEquals(GameStatus.READY, vm.game.status)
        assertEquals(Screen.GAME, vm.screen)
        rule.onNodeWithText("Best 0").assertExists()
    }
}
