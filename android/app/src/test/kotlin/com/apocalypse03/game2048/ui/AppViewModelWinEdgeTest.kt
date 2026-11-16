package com.apocalypse03.game2048.ui

import androidx.lifecycle.SavedStateHandle
import com.apocalypse03.game2048.game.Board
import com.apocalypse03.game2048.game.Direction
import com.apocalypse03.game2048.game.GameStatus
import com.apocalypse03.game2048.ui.data.InMemoryGameStore
import com.apocalypse03.game2048.ui.data.InMemorySettingsStore
import com.apocalypse03.game2048.ui.data.WinRecord
import com.apocalypse03.game2048.ui.timer.FakeClock
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Edge cases of the win screen (DESIGN_SPEC section 13), independent second pass. */
class AppViewModelWinEdgeTest {
    private val z4 = listOf(0, 0, 0, 0)
    private val z6 = List(6) { 0 }
    private val pair4 = Board.of(listOf(listOf(1024, 1024, 0, 0), z4, z4, listOf(0, 0, 0, 2)))
    private val pair6 = Board.of(
        listOf(listOf(1024, 1024, 0, 0, 0, 0), z6, z6, z6, z6, listOf(0, 0, 0, 0, 0, 2)),
    )
    private val winAndOver = Board.of(
        listOf(
            listOf(1024, 1024, 8, 16),
            listOf(4, 16, 32, 64),
            listOf(8, 32, 64, 128),
            listOf(16, 64, 128, 256),
        ),
    )

    private val clock = FakeClock()
    private val handle = SavedStateHandle()
    private val store = InMemoryGameStore()
    private val settings = InMemorySettingsStore()

    private fun vm(state: SavedStateHandle = handle) = AppViewModel(Random(7), clock, state, settings, store)

    private fun started(board: Board = pair4): AppViewModel = vm().apply {
        startNewGame()
        restoreGame(board)
    }

    /** Win with 2 moves in 5 s. */
    private fun won(): AppViewModel = started().apply {
        move(Direction.UP)
        clock.advance(5_000)
        move(Direction.LEFT)
    }

    @Test
    fun firstMoveWinSaysOneMoveAndBestUpdates() {
        val vm = started(Board.of(listOf(listOf(1024, 1024, 0, 0), z4, z4, z4)))
        vm.move(Direction.LEFT)
        assertTrue(vm.winPending)
        assertEquals(1, vm.game.moves)
        assertEquals(WinRecord(1, 0), settings.win)
        assertEquals(2048, vm.game.best)
        assertEquals(2048, settings.best)
        assertEquals(2048, store.load()!!.score)
    }

    @Test
    fun undoBeforeWinThenReplayCountsOnlyNetMoves() {
        val vm = started()
        vm.move(Direction.UP)
        vm.undo()
        assertEquals(0, vm.game.moves)
        vm.move(Direction.UP)
        vm.move(Direction.LEFT)
        assertTrue(vm.winPending)
        assertEquals(WinRecord(2, 0), settings.win)
    }

    @Test
    fun princessSwapsDoNotCountAndPrincessUndoRestoresCount() {
        val vm = started()
        vm.move(Direction.UP)
        clock.advance(1_000)
        vm.enterPrincess()
        vm.onTileTap(0, 0)
        vm.onTileTap(0, 3)
        assertEquals(1, vm.game.moves)
        vm.undo()
        assertEquals(1, vm.game.moves)
        vm.undo()
        assertEquals(0, vm.game.moves)
        vm.exitPrincess()
        clock.advance(2_000)
        vm.move(Direction.UP)
        vm.move(Direction.LEFT)
        assertTrue(vm.winPending)
        assertEquals(2, vm.game.moves)
        assertEquals(WinRecord(2, 3_000), settings.win)
    }

    @Test
    fun princessUndoPastWinAfterEndlessNeverRewins() {
        val vm = won()
        vm.continueEndless()
        vm.enterPrincess()
        vm.undo()
        vm.undo()
        vm.exitPrincess()
        vm.move(Direction.UP)
        vm.move(Direction.LEFT)
        assertEquals(2048, vm.game.board.toIntArray().max())
        assertFalse(vm.winPending)
        assertTrue(vm.won)
        assertEquals(WinRecord(2, 5_000), settings.win)
    }

    @Test
    fun appStopStartWhilePendingKeepsTimerPaused() {
        val vm = won()
        vm.onAppStop()
        clock.advance(30_000)
        vm.onAppStart()
        assertFalse(vm.timerRunning)
        clock.advance(30_000)
        assertEquals(5_000L, vm.elapsedMs())
        vm.continueEndless()
        assertTrue(vm.timerRunning)
        assertEquals(5_000L, vm.elapsedMs())
    }

    @Test
    fun noTimerJumpWhenEndlessAfterLongPending() {
        val vm = won()
        clock.advance(120_000)
        vm.continueEndless()
        assertEquals(5_000L, vm.elapsedMs())
        clock.advance(1_000)
        assertEquals(6_000L, vm.elapsedMs())
    }

    @Test
    fun processDeathOnGameScreenKeepsTimerPaused() {
        won()
        val again = vm()
        assertEquals(Screen.GAME, again.screen)
        again.onAppStart()
        assertFalse(again.timerRunning)
        clock.advance(10_000)
        assertEquals(5_000L, again.elapsedMs())
        assertTrue(again.winPending)
        again.move(Direction.DOWN)
        assertEquals(2, again.game.moves)
    }

    @Test
    fun winThatEndedGameRestoresPendingThenOverAfterEndless() {
        val vm = started(winAndOver)
        vm.move(Direction.LEFT)
        assertEquals(GameStatus.OVER, vm.game.status)
        val again = vm(SavedStateHandle())
        assertTrue(again.winPending)
        assertTrue(again.hasGameInProgress)
        assertEquals(GameStatus.OVER, again.game.status)
        again.resumeGame()
        again.continueEndless()
        assertFalse(again.timerRunning)
        assertEquals(GameStatus.OVER, again.game.status)
        assertFalse(again.hasGameInProgress)
    }

    @Test
    fun confirmYesOnGameScreenUsesGameModeAndPersistsFreshGame() {
        val vm = won()
        vm.stepMode(1)
        vm.requestNewGame()
        vm.confirmNewGameYes()
        val saved = store.load()!!
        assertEquals(16, saved.cells.size)
        assertFalse(saved.won)
        assertFalse(saved.winPending)
        assertEquals(0, saved.moves)
        assertEquals(0L, saved.timerElapsedMs)
        assertFalse(saved.timerStarted)
        assertNull(saved.undoCells)
    }

    @Test
    fun confirmYesFromHomeAfterWinUsesSelectorMode() {
        val vm = won()
        vm.goHome()
        vm.stepMode(1)
        vm.requestNewGame()
        assertTrue(vm.confirmNewGame)
        vm.confirmNewGameYes()
        assertEquals(6, vm.game.size)
        assertFalse(vm.won)
        assertFalse(vm.winPending)
        assertEquals(Screen.GAME, vm.screen)
    }

    @Test
    fun recordsAreIsolatedPerModeAndWorseDoesNotOverwrite() {
        val vm = won()
        vm.goHome()
        vm.stepMode(1)
        vm.startNewGame()
        vm.restoreGame(pair6)
        vm.move(Direction.UP)
        clock.advance(9_000)
        vm.move(Direction.LEFT)
        assertEquals(WinRecord(2, 5_000), settings.win)
        assertEquals(WinRecord(2, 9_000), settings.winPlus)
        vm.goHome()
        vm.stepMode(-1)
        vm.startNewGame()
        vm.restoreGame(pair4)
        vm.move(Direction.UP)
        clock.advance(8_000)
        vm.move(Direction.LEFT)
        assertEquals(WinRecord(2, 5_000), settings.win)
        assertEquals(WinRecord(2, 9_000), settings.winPlus)
    }

    @Test
    fun undoSlotMovesSurviveRestartAndPendingWinBlocksUndoAfterRestore() {
        won()
        val saved = store.load()!!
        assertEquals(2, saved.moves)
        assertEquals(1, saved.undoMoves)
        val again = vm(SavedStateHandle())
        again.undo()
        assertEquals(2, again.game.moves)
        again.resumeGame()
        again.continueEndless()
        again.undo()
        assertEquals(1, again.game.moves)
    }

    @Test
    fun endlessIsPersistedImmediately() {
        val vm = won()
        vm.continueEndless()
        val saved = store.load()!!
        assertTrue(saved.won)
        assertFalse(saved.winPending)
    }
}
