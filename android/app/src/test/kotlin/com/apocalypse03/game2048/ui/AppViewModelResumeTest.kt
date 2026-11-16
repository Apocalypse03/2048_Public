package com.apocalypse03.game2048.ui

import androidx.lifecycle.SavedStateHandle
import com.apocalypse03.game2048.game.Board
import com.apocalypse03.game2048.game.Direction
import com.apocalypse03.game2048.game.GameStatus
import com.apocalypse03.game2048.ui.data.InMemoryGameStore
import com.apocalypse03.game2048.ui.data.InMemorySettingsStore
import com.apocalypse03.game2048.ui.timer.FakeClock
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Resume and confirm-new-game logic. */
class AppViewModelResumeTest {
    private val z = listOf(0, 0, 0, 0)
    private val pair = Board.of(listOf(listOf(2, 2, 0, 0), z, z, z))

    /** One RIGHT fills the only gap and leaves no merges: game over. */
    private val lastMove = Board.of(
        listOf(
            listOf(8, 16, 8, 16),
            listOf(16, 8, 16, 8),
            listOf(8, 16, 8, 16),
            listOf(32, 64, 128, 0),
        ),
    )

    private val clock = FakeClock()

    private fun vm(games: InMemoryGameStore = InMemoryGameStore()) = AppViewModel(
        Random(7), clock, SavedStateHandle(), InMemorySettingsStore(), games,
    )

    /** A game in progress, shown Home. */
    private fun playingHome() = vm().apply {
        startNewGame()
        restoreGame(pair)
        move(Direction.LEFT)
        clock.advance(2_000)
        goHome()
    }

    @Test
    fun freshVmHasNothingToResume() {
        val vm = vm()
        assertFalse(vm.hasGameInProgress)
        assertFalse(vm.confirmNewGame)
    }

    @Test
    fun requestNewGameStartsDirectlyWhenNothingInProgress() {
        val vm = vm()
        vm.requestNewGame()
        assertFalse(vm.confirmNewGame)
        assertEquals(Screen.GAME, vm.screen)
        assertEquals(GameStatus.READY, vm.game.status)
    }

    @Test
    fun readyGameIsNotInProgress() {
        val vm = vm().apply {
            startNewGame()
            goHome()
        }
        assertFalse(vm.hasGameInProgress)
        vm.requestNewGame()
        assertFalse(vm.confirmNewGame)
        assertEquals(Screen.GAME, vm.screen)
    }

    @Test
    fun movedGameIsInProgressAfterGoHome() {
        val vm = playingHome()
        assertTrue(vm.hasGameInProgress)
        assertEquals(Screen.HOME, vm.screen)
    }

    @Test
    fun requestNewGameAsksWhenInProgress() {
        val vm = playingHome()
        val before = vm.game
        vm.requestNewGame()
        assertTrue(vm.confirmNewGame)
        assertEquals(before, vm.game)
        assertEquals(Screen.HOME, vm.screen)
    }

    @Test
    fun answeringNoKeepsTheGame() {
        val vm = playingHome()
        val before = vm.game
        vm.requestNewGame()
        vm.confirmNewGameNo()
        assertFalse(vm.confirmNewGame)
        assertEquals(before, vm.game)
        assertEquals(Screen.HOME, vm.screen)
        assertTrue(vm.hasGameInProgress)
        assertEquals(2_000L, vm.elapsedMs())
    }

    @Test
    fun answeringYesStartsFreshGame() {
        val vm = playingHome()
        vm.requestNewGame()
        vm.confirmNewGameYes()
        assertFalse(vm.confirmNewGame)
        assertEquals(Screen.GAME, vm.screen)
        assertEquals(GameStatus.READY, vm.game.status)
        assertEquals(0, vm.game.score)
        assertEquals(0L, vm.elapsedMs())
        assertFalse(vm.hasGameInProgress)
    }

    @Test
    fun overGameIsNotResumable() {
        val vm = vm().apply {
            startNewGame()
            restoreGame(pair)
            move(Direction.LEFT)
            restoreGame(lastMove, status = GameStatus.PLAYING)
            move(Direction.RIGHT)
            goHome()
        }
        assertEquals(GameStatus.OVER, vm.game.status)
        assertFalse(vm.hasGameInProgress)
        vm.requestNewGame()
        assertFalse(vm.confirmNewGame)
        assertEquals(Screen.GAME, vm.screen)
        assertEquals(GameStatus.READY, vm.game.status)
    }

    @Test
    fun resumeGameShowsGameAndRunsTimer() {
        val vm = playingHome()
        val before = vm.game
        vm.resumeGame()
        assertEquals(Screen.GAME, vm.screen)
        assertEquals(before, vm.game)
        assertTrue(vm.timerRunning)
        clock.advance(1_000)
        assertEquals(3_000L, vm.elapsedMs())
    }

    @Test
    fun resumeGameIsNoOpWhenNothingInProgress() {
        val vm = vm()
        vm.resumeGame()
        assertEquals(Screen.HOME, vm.screen)
        assertFalse(vm.timerRunning)
        assertEquals(GameStatus.READY, vm.game.status)
    }

    @Test
    fun resumeGameIsNoOpForOverGame() {
        val vm = vm().apply {
            startNewGame()
            restoreGame(lastMove, status = GameStatus.PLAYING)
            move(Direction.RIGHT)
            goHome()
        }
        vm.resumeGame()
        assertEquals(Screen.HOME, vm.screen)
    }

    @Test
    fun goingHomeSavesToLastMove() {
        val games = InMemoryGameStore()
        val vm = vm(games).apply {
            startNewGame()
            restoreGame(pair)
            move(Direction.LEFT)
            goHome()
        }
        val saved = games.load()!!
        assertEquals(vm.game.board.toIntArray().toList(), saved.cells.toList())
        assertEquals(vm.game.score, saved.score)
    }
}
