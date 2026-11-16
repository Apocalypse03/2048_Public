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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cross-mode edge cases: dialog, Best, undo, Princess, game over, restore, timer. */
class AppViewModelModeEdgeTest {
    private val clock = FakeClock()

    private fun cells(n: Int, vararg v: Pair<Int, Int>) = IntArray(n * n).apply { v.forEach { (i, x) -> this[i] = x } }
    private val pair4 = Board.of(cells(4, 0 to 2, 1 to 2))
    private val pair6 = Board.of(cells(6, 0 to 2, 1 to 2))

    private fun vm(
        settings: InMemorySettingsStore = InMemorySettingsStore(),
        games: InMemoryGameStore = InMemoryGameStore(),
        handle: SavedStateHandle = SavedStateHandle(),
    ) = AppViewModel(Random(5), clock, handle, settings, games)

    /** A PLAYING 6x6 game (score 4) shown Home, selector set to [selector]. */
    private fun playing6Home(settings: InMemorySettingsStore, selector: GameMode): AppViewModel {
        val vm = vm(settings)
        vm.stepMode(1)
        vm.startNewGame()
        vm.restoreGame(pair6, best = vm.game.best)
        vm.move(Direction.LEFT)
        vm.goHome()
        vm.stepMode(selector.ordinal - vm.mode.ordinal)
        return vm
    }

    private fun playing4Home(settings: InMemorySettingsStore, selector: GameMode): AppViewModel {
        val vm = vm(settings)
        vm.startNewGame()
        vm.restoreGame(pair4, best = vm.game.best)
        vm.move(Direction.LEFT)
        vm.goHome()
        vm.stepMode(selector.ordinal - vm.mode.ordinal)
        return vm
    }

    @Test
    fun otherSizeNewGameAsksThenYesStartsSelectedSizeWithItsBest() {
        val s = InMemorySettingsStore(best = 900, bestPlus = 0)
        val vm = playing6Home(s, GameMode.NORMAL)
        assertEquals(4, s.bestPlus)
        vm.requestNewGame()
        assertTrue(vm.confirmNewGame)
        assertEquals(6, vm.game.size)
        vm.confirmNewGameYes()
        assertFalse(vm.confirmNewGame)
        assertEquals(4, vm.game.size)
        assertEquals(900, vm.game.best)
        assertEquals(0, vm.game.score)
        assertEquals(GameStatus.READY, vm.game.status)
        assertEquals(Screen.GAME, vm.screen)
        assertFalse(vm.timerRunning)
        assertEquals(0L, vm.elapsedMs())
        assertEquals(4, s.bestPlus)
    }

    @Test
    fun otherSizeNewGameNoKeepsOldGameAndBest() {
        val s = InMemorySettingsStore(best = 900)
        val vm = playing6Home(s, GameMode.NORMAL)
        val board = vm.game.board
        vm.requestNewGame()
        vm.confirmNewGameNo()
        assertEquals(board, vm.game.board)
        assertEquals(6, vm.game.size)
        assertEquals(4, vm.game.best)
        assertEquals(Screen.HOME, vm.screen)
        assertTrue(vm.hasGameInProgress)
        vm.resumeGame()
        assertEquals(6, vm.game.size)
        assertEquals(4, vm.game.best)
    }

    @Test
    fun plusGameInProgressSelectorPlusAlsoAsks() {
        val vm = playing6Home(InMemorySettingsStore(), GameMode.PLUS)
        vm.requestNewGame()
        assertTrue(vm.confirmNewGame)
        vm.confirmNewGameYes()
        assertEquals(6, vm.game.size)
        assertEquals(0, vm.game.score)
        assertEquals(4, vm.game.best)
    }

    @Test
    fun normalGameInProgressSwitchToPlusYesGivesPlusBest() {
        val s = InMemorySettingsStore(best = 0, bestPlus = 77)
        val vm = playing4Home(s, GameMode.PLUS)
        vm.requestNewGame()
        assertTrue(vm.confirmNewGame)
        vm.confirmNewGameYes()
        assertEquals(6, vm.game.size)
        assertEquals(77, vm.game.best)
        assertEquals(4, s.best)
    }

    @Test
    fun playingAfterModeSwitchRaisesOnlyThatModesBest() {
        val s = InMemorySettingsStore(best = 10, bestPlus = 20)
        val vm = playing6Home(s, GameMode.NORMAL)
        vm.requestNewGame()
        vm.confirmNewGameYes()
        vm.restoreGame(Board.of(cells(4, 0 to 8, 1 to 8)), best = vm.game.best)
        vm.move(Direction.LEFT)
        assertEquals(16, s.best)
        assertEquals(20, s.bestPlus)
        assertEquals(16, vm.game.best)
    }

    @Test
    fun restartedVmResumesPlusGameWithPlusBestWhileSelectorNormal() {
        val s = InMemorySettingsStore(best = 5000)
        val games = InMemoryGameStore()
        val first = AppViewModel(Random(5), clock, SavedStateHandle(), s, games)
        first.stepMode(1)
        first.startNewGame()
        first.restoreGame(pair6)
        first.move(Direction.LEFT)
        first.stepMode(-1)
        val second = AppViewModel(Random(5), clock, SavedStateHandle(), s, games)
        assertEquals(GameMode.NORMAL, second.mode)
        assertEquals(6, second.game.size)
        assertEquals(4, second.game.best)
        assertEquals(5000, s.best)
    }

    @Test
    fun restoredScoreAboveBestOfOwnModeRaisesOnlyThatBest() {
        val s = InMemorySettingsStore(best = 3, bestPlus = 3, mode = GameMode.PLUS)
        val games = InMemoryGameStore()
        val a = AppViewModel(Random(5), clock, SavedStateHandle(), s, games)
        a.startNewGame()
        a.restoreGame(Board.of(cells(6, 0 to 1024, 1 to 1024, 35 to 2)), score = 5000, best = 5000)
        s.bestPlus = 0
        s.best = 0
        s.mode = GameMode.NORMAL
        val b = AppViewModel(Random(5), clock, SavedStateHandle(), s, games)
        assertEquals(5000, b.game.best)
        assertEquals(5000, s.bestPlus)
        assertEquals(0, s.best)
    }

    @Test
    fun undoDoesNotCrossModeSwitch() {
        val vm = playing6Home(InMemorySettingsStore(), GameMode.NORMAL)
        assertTrue(vm.game.canUndo)
        vm.requestNewGame()
        vm.confirmNewGameYes()
        assertFalse(vm.game.canUndo)
        vm.undo()
        assertEquals(4, vm.game.size)
        assertEquals(0, vm.game.score)
        assertEquals(GameStatus.READY, vm.game.status)
    }

    @Test
    fun princessHistoryDoesNotSurviveNewGameOfOtherSize() {
        val vm = vm()
        vm.stepMode(1)
        vm.startNewGame()
        vm.restoreGame(Board.of(cells(6, 0 to 2, 35 to 4)), status = GameStatus.PLAYING)
        vm.enterPrincess()
        vm.onTileTap(0, 0)
        vm.onTileTap(5, 5)
        assertTrue(vm.game.canUndo)
        vm.exitPrincess()
        vm.goHome()
        vm.stepMode(-1)
        vm.requestNewGame()
        assertTrue(vm.confirmNewGame)
        vm.confirmNewGameYes()
        assertEquals(4, vm.game.size)
        assertFalse(vm.game.princess)
        assertFalse(vm.game.canUndo)
        vm.undo()
        assertEquals(4, vm.game.size)
        assertEquals(2, vm.game.board.toIntArray().count { it != 0 })
        assertNull(vm.selected)
    }

    @Test
    fun newGameOfOtherSizeClearsPrincessSelection() {
        val vm = vm()
        vm.stepMode(1)
        vm.startNewGame()
        vm.restoreGame(Board.of(cells(6, 0 to 2, 35 to 4)), status = GameStatus.PLAYING)
        vm.enterPrincess()
        vm.onTileTap(5, 5)
        vm.goHome()
        vm.stepMode(-1)
        vm.startNewGame()
        assertFalse(vm.game.princess)
        assertNull(vm.selected)
        vm.onTileTap(0, 0)
        assertNull(vm.selected)
    }

    @Test
    fun princessUnlimitedUndoOn6x6() {
        val vm = vm()
        vm.stepMode(1)
        vm.startNewGame()
        val start = Board.of(cells(6, 0 to 2, 1 to 4, 2 to 8))
        vm.restoreGame(start, status = GameStatus.PLAYING)
        vm.enterPrincess()
        repeat(6) { i ->
            vm.onTileTap(0, if (i % 2 == 0) 0 else 1)
            vm.onTileTap(0, 2)
        }
        repeat(6) { vm.undo() }
        assertEquals(start, vm.game.board)
        assertFalse(vm.game.canUndo)
    }

    @Test
    fun gameOverOnFull6x6BlocksMovesAndSurvivesRestore() {
        val pattern = listOf(
            listOf(8, 16, 8, 16, 8, 16),
            listOf(16, 8, 16, 8, 16, 8),
            listOf(8, 16, 8, 16, 8, 16),
            listOf(16, 8, 16, 8, 16, 8),
            listOf(8, 16, 8, 16, 8, 16),
            listOf(64, 128, 256, 512, 1024, 0),
        )
        val games = InMemoryGameStore()
        val s = InMemorySettingsStore()
        val vm = AppViewModel(Random(5), clock, SavedStateHandle(), s, games)
        vm.stepMode(1)
        vm.startNewGame()
        vm.restoreGame(Board.of(pattern), status = GameStatus.PLAYING)
        // RIGHT shifts row 5 by one; the spawn (2 or 4) at (5,0) matches no neighbour.
        vm.move(Direction.RIGHT)
        assertEquals(GameStatus.OVER, vm.game.status)
        assertTrue(vm.game.board.toIntArray().none { it == 0 })
        val frozen = vm.game.board
        for (d in Direction.entries) vm.move(d)
        assertEquals(frozen, vm.game.board)
        assertFalse(vm.timerRunning)
        val back = AppViewModel(Random(5), clock, SavedStateHandle(), s, games)
        assertEquals(6, back.game.size)
        assertEquals(GameStatus.OVER, back.game.status)
        assertFalse(back.hasGameInProgress)
        back.requestNewGame()
        assertFalse(back.confirmNewGame)
    }

    @Test
    fun processDeathRestore6x6OnGameScreen() {
        val handle = SavedStateHandle()
        val games = InMemoryGameStore()
        val s = InMemorySettingsStore()
        val a = AppViewModel(Random(5), clock, handle, s, games)
        a.stepMode(1)
        a.startNewGame()
        a.restoreGame(pair6, status = GameStatus.PLAYING)
        a.move(Direction.LEFT)
        val board = a.game.board
        assertEquals(Screen.GAME, a.screen)
        val b = AppViewModel(Random(5), clock, handle, s, games)
        assertEquals(Screen.GAME, b.screen)
        assertEquals(board, b.game.board)
        assertEquals(6, b.game.size)
        assertEquals(4, b.game.best)
        assertTrue(b.game.canUndo)
        b.undo()
        assertEquals(pair6, b.game.board)
    }

    @Test
    fun timerStartsOnFirstMovingMoveIn6x6AndNotOnNoOp() {
        val vm = vm()
        vm.stepMode(1)
        vm.startNewGame()
        vm.restoreGame(Board.of(cells(6, 0 to 2)))
        vm.move(Direction.LEFT) // already at the left edge: no-op
        assertFalse(vm.timerRunning)
        clock.advance(1000)
        assertEquals(0L, vm.elapsedMs())
        vm.move(Direction.RIGHT)
        assertTrue(vm.timerRunning)
        clock.advance(1500)
        assertEquals(1500L, vm.elapsedMs())
        vm.startNewGame()
        assertEquals(0L, vm.elapsedMs())
        assertFalse(vm.timerRunning)
    }

    @Test
    fun timerResetWhenSwitchingSizeViaNewGame() {
        val vm = playing6Home(InMemorySettingsStore(), GameMode.NORMAL)
        vm.resumeGame()
        clock.advance(3000)
        vm.goHome()
        vm.requestNewGame()
        vm.confirmNewGameYes()
        assertEquals(0L, vm.elapsedMs())
        clock.advance(2000)
        assertEquals(0L, vm.elapsedMs())
    }

    @Test
    fun persistedSessionCellCountFollowsSize() {
        val games = InMemoryGameStore()
        val vm = AppViewModel(Random(5), clock, SavedStateHandle(), InMemorySettingsStore(), games)
        vm.stepMode(1)
        vm.startNewGame()
        assertEquals(36, games.load()!!.cells.size)
        vm.stepMode(-1)
        vm.startNewGame()
        assertEquals(16, games.load()!!.cells.size)
        assertNull(games.load()!!.undoCells)
    }
}
