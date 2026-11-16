package com.apocalypse03.game2048.ui

import androidx.lifecycle.SavedStateHandle
import com.apocalypse03.game2048.game.Board
import com.apocalypse03.game2048.game.Direction
import com.apocalypse03.game2048.game.GameStatus
import com.apocalypse03.game2048.ui.data.InMemoryGameStore
import com.apocalypse03.game2048.ui.data.InMemorySettingsStore
import com.apocalypse03.game2048.ui.data.SavedSession
import com.apocalypse03.game2048.ui.timer.FakeClock
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Game modes in the ViewModel: Normal (4x4) and 2048+ (6x6), selector, per-mode Best, 6x6 sessions. */
class AppViewModelModeTest {
    private val clock = FakeClock()

    private fun vm(
        settings: InMemorySettingsStore = InMemorySettingsStore(),
        games: InMemoryGameStore = InMemoryGameStore(),
        seed: Int = 7,
    ) = AppViewModel(Random(seed), clock, SavedStateHandle(), settings, games)

    /** n x n cells with the given (index to value) entries, rest empty. */
    private fun cells(n: Int, vararg values: Pair<Int, Int>) =
        IntArray(n * n).apply { values.forEach { (i, v) -> this[i] = v } }

    /** 6x6 board: a 2,2 pair at the start of row 0. */
    private val pair6 = Board.of(cells(6, 0 to 2, 1 to 2))

    /** 4x4 board: a 2,2 pair at the start of row 0. */
    private val pair4 = Board.of(cells(4, 0 to 2, 1 to 2))

    private fun session(cells: IntArray, undo: IntArray? = null, score: Int = 8) = SavedSession(
        cells = cells,
        score = score,
        undoCells = undo,
        undoScore = 4,
        status = "PLAYING",
        timerElapsedMs = 1_000L,
        timerStarted = true,
    )

    private fun storeOf(s: SavedSession) = InMemoryGameStore().apply { save(s) }

    @Test
    fun stepModeStaysInBounds() {
        val vm = vm()
        assertEquals(GameMode.NORMAL, vm.mode)
        vm.stepMode(-1)
        assertEquals(GameMode.NORMAL, vm.mode)
        vm.stepMode(1)
        assertEquals(GameMode.PLUS, vm.mode)
        vm.stepMode(1)
        assertEquals(GameMode.PLUS, vm.mode)
        vm.stepMode(-5)
        assertEquals(GameMode.NORMAL, vm.mode)
    }

    @Test
    fun modePersistsAcrossNewVm() {
        val settings = InMemorySettingsStore()
        vm(settings).stepMode(1)
        assertEquals(GameMode.PLUS, settings.mode)
        assertEquals(GameMode.PLUS, vm(settings).mode)
    }

    @Test
    fun newGameInPlusIs6x6AndReady() {
        val settings = InMemorySettingsStore()
        val vm = vm(settings).apply {
            stepMode(1)
            startNewGame()
        }
        assertEquals(6, vm.game.size)
        assertEquals(36, vm.game.board.toIntArray().size)
        assertEquals(2, vm.game.board.toIntArray().count { it != 0 })
        assertEquals(GameStatus.READY, vm.game.status)
        assertEquals(Screen.GAME, vm.screen)
        assertEquals(GameMode.PLUS, settings.mode)
        vm.stepMode(-1)
        vm.startNewGame()
        assertEquals(4, vm.game.size)
        assertEquals(GameMode.NORMAL, settings.mode)
    }

    @Test
    fun resumeOf6x6SessionKeepsSizeWhileSelectorSaysNormal() {
        val games = storeOf(session(cells(6, 0 to 4, 7 to 2)))
        val vm = vm(InMemorySettingsStore(mode = GameMode.NORMAL), games)
        assertEquals(GameMode.NORMAL, vm.mode)
        assertEquals(6, vm.game.size)
        assertTrue(vm.hasGameInProgress)
        vm.resumeGame()
        assertEquals(Screen.GAME, vm.screen)
        assertEquals(6, vm.game.size)
        assertEquals(36, games.load()!!.cells.size)
        // New Game still asks first, then follows the selector.
        vm.goHome()
        vm.requestNewGame()
        assertTrue(vm.confirmNewGame)
        vm.confirmNewGameYes()
        assertEquals(4, vm.game.size)
    }

    @Test
    fun bestIsKeptPerMode() {
        val settings = InMemorySettingsStore(best = 100, bestPlus = 0)
        val vm = vm(settings).apply {
            stepMode(1)
            startNewGame()
        }
        assertEquals(0, vm.game.best)
        vm.restoreGame(pair6, best = vm.game.best)
        vm.move(Direction.LEFT)
        assertEquals(4, vm.game.score)
        assertEquals(4, settings.bestPlus)
        assertEquals(100, settings.best)

        settings.best = 2
        vm.stepMode(-1)
        vm.startNewGame()
        assertEquals(2, vm.game.best)
        vm.restoreGame(pair4, best = vm.game.best)
        vm.move(Direction.LEFT)
        assertEquals(4, settings.best)
        assertEquals(4, settings.bestPlus)
        vm.restoreGame(Board.of(cells(4, 0 to 8, 1 to 8)), score = 4, best = 4)
        vm.move(Direction.LEFT)
        assertEquals(20, settings.best)
        assertEquals(4, settings.bestPlus)
    }

    @Test
    fun restores36CellSessionWithUndo() {
        val board = cells(6, 0 to 4, 35 to 2)
        val undo = cells(6, 0 to 2, 1 to 2, 35 to 2)
        val settings = InMemorySettingsStore(best = 500, bestPlus = 3)
        val vm = vm(settings, storeOf(session(board, undo)))
        assertEquals(Board.of(board), vm.game.board)
        assertEquals(8, vm.game.score)
        assertTrue(vm.game.canUndo)
        assertEquals(GameStatus.PLAYING, vm.game.status)
        // Restored score above the 2048+ Best raises that Best only.
        assertEquals(8, vm.game.best)
        assertEquals(8, settings.bestPlus)
        assertEquals(500, settings.best)
        vm.undo()
        assertEquals(Board.of(undo), vm.game.board)
        assertEquals(4, vm.game.score)
    }

    @Test
    fun session25CellsIsCleared() {
        val games = storeOf(session(IntArray(25).apply { this[0] = 2 }))
        val vm = vm(games = games)
        assertNull(games.load())
        assertEquals(4, vm.game.size)
        assertEquals(GameStatus.READY, vm.game.status)
    }

    @Test
    fun sessionWithMismatchedUndoSizeIsCleared() {
        val games = storeOf(session(cells(6, 0 to 4), undo = cells(4, 0 to 2, 1 to 2)))
        val vm = vm(games = games)
        assertNull(games.load())
        assertEquals(4, vm.game.size)
        assertEquals(GameStatus.READY, vm.game.status)
    }

    @Test
    fun princessSwapOn6x6AtBottomRightCorner() {
        val vm = vm().apply {
            stepMode(1)
            startNewGame()
            restoreGame(Board.of(cells(6, 0 to 4, 35 to 2)), status = GameStatus.PLAYING)
            enterPrincess()
        }
        vm.onTileTap(5, 5)
        assertEquals(35, vm.selected)
        vm.onTileTap(0, 0)
        assertNull(vm.selected)
        assertEquals(2, vm.game.board[0, 0])
        assertEquals(4, vm.game.board[5, 5])
        vm.undo()
        assertEquals(4, vm.game.board[0, 0])
        assertEquals(2, vm.game.board[5, 5])
    }
}
