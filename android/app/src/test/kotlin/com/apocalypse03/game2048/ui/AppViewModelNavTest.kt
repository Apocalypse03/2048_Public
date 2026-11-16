package com.apocalypse03.game2048.ui

import androidx.lifecycle.SavedStateHandle
import com.apocalypse03.game2048.game.Board
import com.apocalypse03.game2048.game.Direction
import com.apocalypse03.game2048.ui.data.InMemoryGameStore
import com.apocalypse03.game2048.ui.data.InMemorySettingsStore
import com.apocalypse03.game2048.ui.timer.FakeClock
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Settings / Results navigation and its SavedStateHandle restore (DESIGN_SPEC sections 15-16). */
class AppViewModelNavTest {
    private val z = listOf(0, 0, 0, 0)
    private val pair = Board.of(listOf(listOf(2, 2, 0, 0), z, z, z))
    private val clock = FakeClock()
    private val settings = InMemorySettingsStore()
    private val games = InMemoryGameStore()

    private fun vm(handle: SavedStateHandle = SavedStateHandle()) =
        AppViewModel(Random(7), clock, handle, settings, games)

    @Test
    fun settingsOpensFromHomeAndGoesBack() {
        val vm = vm()
        vm.openSettings()
        assertEquals(Screen.SETTINGS, vm.screen)
        vm.goHome()
        assertEquals(Screen.HOME, vm.screen)
    }

    @Test
    fun resultsOpenOnHomeModeAndStepWithoutChangingIt() {
        val vm = vm()
        vm.stepMode(1)
        vm.openResults()
        assertEquals(Screen.RESULTS, vm.screen)
        assertEquals(GameMode.PLUS, vm.resultsMode)
        vm.stepResultsMode(-1)
        assertEquals(GameMode.NORMAL, vm.resultsMode)
        vm.stepResultsMode(-1)
        assertEquals(GameMode.NORMAL, vm.resultsMode)
        assertEquals(GameMode.PLUS, vm.mode)
        assertEquals(GameMode.PLUS, settings.mode)
        vm.goHome()
        vm.openResults() // re-initialised from Home's mode
        assertEquals(GameMode.PLUS, vm.resultsMode)
    }

    @Test
    fun subScreensOpenOnlyFromHome() {
        val vm = vm()
        vm.startNewGame()
        vm.openSettings()
        assertEquals(Screen.GAME, vm.screen)
        vm.openResults()
        assertEquals(Screen.GAME, vm.screen)
    }

    @Test
    fun timerDoesNotRunOffTheGameScreen() {
        val vm = vm()
        vm.startNewGame()
        vm.restoreGame(pair)
        vm.move(Direction.LEFT)
        vm.goHome()
        vm.openSettings()
        assertFalse(vm.timerRunning)
        clock.advance(4_000)
        vm.goHome()
        vm.openResults()
        assertFalse(vm.timerRunning)
        assertEquals(0L, vm.elapsedMs())
    }

    @Test
    fun settingsAndResultsRestoreFromSavedStateWithoutASession() {
        val handle = SavedStateHandle()
        vm(handle).openSettings()
        assertEquals(Screen.SETTINGS, vm(handle).screen)

        val handle2 = SavedStateHandle()
        vm(handle2).apply {
            openResults()
            stepResultsMode(1)
        }
        val restored = vm(handle2)
        assertEquals(Screen.RESULTS, restored.screen)
        assertEquals(GameMode.PLUS, restored.resultsMode)
        assertEquals(GameMode.NORMAL, restored.mode)
    }

    @Test
    fun goHomeFromSubScreenSavesHome() {
        val handle = SavedStateHandle()
        vm(handle).apply {
            openSettings()
            goHome()
        }
        assertEquals(Screen.HOME, vm(handle).screen)
    }

    @Test
    fun infoOpensFromHomeAndCloses() {
        val vm = vm()
        assertFalse(vm.infoOpen)
        vm.openInfo()
        assertTrue(vm.infoOpen)
        assertEquals(Screen.HOME, vm.screen)
        vm.closeInfo()
        assertFalse(vm.infoOpen)
    }

    @Test
    fun infoIsIgnoredOffHome() {
        val vm = vm()
        vm.openSettings()
        vm.openInfo()
        assertFalse(vm.infoOpen)
    }

    @Test
    fun infoIsNotRestored() {
        val handle = SavedStateHandle()
        vm(handle).openInfo()
        assertFalse(vm(handle).infoOpen)
    }
}
