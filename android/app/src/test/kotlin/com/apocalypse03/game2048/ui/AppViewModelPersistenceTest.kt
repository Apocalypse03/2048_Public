package com.apocalypse03.game2048.ui

import androidx.lifecycle.SavedStateHandle
import com.apocalypse03.game2048.game.Board
import com.apocalypse03.game2048.game.Direction
import com.apocalypse03.game2048.game.GameStatus
import com.apocalypse03.game2048.ui.data.GameStore
import com.apocalypse03.game2048.ui.data.InMemoryGameStore
import com.apocalypse03.game2048.ui.data.InMemorySettingsStore
import com.apocalypse03.game2048.ui.data.SavedSession
import com.apocalypse03.game2048.ui.timer.FakeClock
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A second ViewModel on the same [InMemoryGameStore] and an empty [SavedStateHandle] simulates
 * closing and reopening the app; the same store and the same handle simulate process death.
 */
class AppViewModelPersistenceTest {
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
    private val handle = SavedStateHandle()
    private val gameStore = InMemoryGameStore()

    /** Same store and handle by default (process death); pass an empty handle for an app restart. */
    private fun vm(
        settings: InMemorySettingsStore = InMemorySettingsStore(),
        state: SavedStateHandle = handle,
        games: InMemoryGameStore = gameStore,
        seed: Int = 7,
    ) = AppViewModel(Random(seed), clock, state, settings, games)

    /** A reopened app: durable game kept, SavedStateHandle empty. */
    private fun reopened(settings: InMemorySettingsStore = InMemorySettingsStore(), seed: Int = 7) =
        vm(settings, SavedStateHandle(), seed = seed)

    private val savedCells = intArrayOf(4, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)

    /** Hand-written contents of a valid mid-game save. */
    private fun validSession() = SavedSession(
        cells = savedCells,
        score = 4,
        undoCells = null,
        undoScore = 0,
        status = "PLAYING",
        timerElapsedMs = 3_000L,
        timerStarted = true,
    )

    private fun storeOf(session: SavedSession) = InMemoryGameStore().apply { save(session) }

    /** [InMemoryGameStore] that counts [save] calls. */
    private class CountingGameStore(private val inner: InMemoryGameStore = InMemoryGameStore()) : GameStore by inner {
        var saves = 0

        override fun save(s: SavedSession) {
            saves++
            inner.save(s)
        }
    }

    @Test
    fun freshVmReadsSettings() {
        val vm = vm(InMemorySettingsStore(best = 500, themeId = 3))
        assertEquals(500, vm.game.best)
        assertEquals(3, vm.themeId)
        assertEquals(Screen.HOME, vm.screen)
    }

    @Test
    fun selectThemeWritesStore() {
        val store = InMemorySettingsStore()
        vm(store).selectTheme(5)
        assertEquals(5, store.themeId)
    }

    @Test
    fun higherScoreWritesBest() {
        val store = InMemorySettingsStore(best = 2)
        val vm = vm(store).apply {
            startNewGame()
            restoreGame(pair)
            move(Direction.LEFT)
        }
        assertEquals(4, vm.game.score)
        assertEquals(4, store.best)
    }

    @Test
    fun lowerScoreKeepsStoredBest() {
        val store = InMemorySettingsStore(best = 100)
        vm(store).apply {
            startNewGame()
            restoreGame(pair)
            move(Direction.LEFT)
        }
        assertEquals(100, store.best)
    }

    private fun playSome(vm: AppViewModel) = vm.apply {
        startNewGame()
        stepMode(1)
        repeat(3) { listOf(Direction.LEFT, Direction.UP, Direction.RIGHT, Direction.DOWN).forEach(::move) }
    }

    @Test
    fun gameSurvivesAppRestart() {
        val settings = InMemorySettingsStore()
        val vm1 = playSome(vm(settings, seed = 42))
        assertEquals(GameStatus.PLAYING, vm1.game.status)
        val vm2 = reopened(settings, seed = 1)
        assertEquals(vm1.game.board, vm2.game.board)
        assertEquals(vm1.game.score, vm2.game.score)
        assertEquals(vm1.game.best, vm2.game.best)
        assertEquals(vm1.game.canUndo, vm2.game.canUndo)
        assertEquals(vm1.game.status, vm2.game.status)
        assertEquals(Screen.HOME, vm2.screen)
        // The selector is a setting; the resumable game keeps its own (4x4) size.
        assertEquals(GameMode.PLUS, vm2.mode)
        assertEquals(4, vm2.game.size)
        assertTrue(vm2.hasGameInProgress)
    }

    @Test
    fun gameAndScreenSurviveProcessDeath() {
        val vm1 = playSome(vm(seed = 42))
        val vm2 = vm(seed = 1)
        assertEquals(vm1.game.board, vm2.game.board)
        assertEquals(vm1.game.score, vm2.game.score)
        assertEquals(Screen.GAME, vm2.screen)
    }

    @Test
    fun gameScreenWithoutSessionFallsBackToHome() {
        val vm = vm(state = SavedStateHandle(mapOf("screen" to "GAME")), games = InMemoryGameStore())
        assertEquals(Screen.HOME, vm.screen)
    }

    @Test
    fun modeRestoresFromSettings() {
        val vm = vm(InMemorySettingsStore(mode = GameMode.PLUS), games = InMemoryGameStore())
        assertEquals(GameMode.PLUS, vm.mode)
        assertFalse(handle.contains("difficulty"))
    }

    @Test
    fun resumeAfterRestartRunsTimer() {
        vm().apply {
            startNewGame()
            restoreGame(pair)
            move(Direction.LEFT)
            clock.advance(5_000)
            goHome()
            onAppStop()
        }
        val vm2 = reopened()
        assertEquals(Screen.HOME, vm2.screen)
        assertEquals(5_000L, vm2.elapsedMs())
        vm2.onAppStart()
        assertFalse(vm2.timerRunning)
        vm2.resumeGame()
        assertEquals(Screen.GAME, vm2.screen)
        assertTrue(vm2.timerRunning)
        clock.advance(1_000)
        assertEquals(6_000L, vm2.elapsedMs())
    }

    @Test
    fun undoSlotSurvivesAppRestart() {
        val vm1 = vm().apply {
            startNewGame()
            restoreGame(pair)
            move(Direction.LEFT)
        }
        val vm2 = reopened()
        assertTrue(vm2.game.canUndo)
        vm1.undo()
        vm2.undo()
        assertEquals(pair, vm2.game.board)
        assertEquals(vm1.game.board, vm2.game.board)
        assertEquals(vm1.game.score, vm2.game.score)
        assertFalse(vm2.game.canUndo)
    }

    @Test
    fun usedUndoSlotIsNotRestored() {
        vm().apply {
            startNewGame()
            restoreGame(pair)
            move(Direction.LEFT)
            undo()
        }
        assertFalse(reopened().game.canUndo)
    }

    @Test
    fun timerElapsedSurvivesButWaitsForStart() {
        vm().apply {
            startNewGame()
            restoreGame(pair)
            move(Direction.LEFT)
            clock.advance(5_000)
            onAppStop()
        }
        val vm2 = reopened()
        assertEquals(5_000L, vm2.elapsedMs())
        assertFalse(vm2.timerRunning)
        clock.advance(1_000)
        assertEquals(5_000L, vm2.elapsedMs())
    }

    @Test
    fun overGameRestoresFrozen() {
        vm().apply {
            startNewGame()
            restoreGame(pair)
            move(Direction.LEFT)
            clock.advance(2_000)
            restoreGame(lastMove, status = GameStatus.PLAYING)
            move(Direction.RIGHT)
        }
        val vm2 = reopened()
        assertEquals(GameStatus.OVER, vm2.game.status)
        assertFalse(vm2.hasGameInProgress)
        vm2.onAppStart()
        assertFalse(vm2.timerRunning)
        clock.advance(3_000)
        assertEquals(2_000L, vm2.elapsedMs())
    }

    @Test
    fun restoredStatusFollowsBoard() {
        // A Princess rescue saved with its frozen OVER on a board that has moves again.
        val rescued = vm(games = storeOf(validSession().copy(status = "OVER")))
        assertEquals(GameStatus.PLAYING, rescued.game.status)

        val full = intArrayOf(2, 4, 2, 4, 4, 2, 4, 2, 2, 4, 2, 4, 4, 2, 4, 2)
        val stuck = vm(games = storeOf(validSession().copy(cells = full)))
        assertEquals(GameStatus.OVER, stuck.game.status)
    }

    @Test
    fun princessIsNotRestored() {
        val vm1 = vm().apply {
            startNewGame()
            restoreGame(pair)
            move(Direction.LEFT)
            enterPrincess()
        }
        assertTrue(vm1.game.princess)
        val vm2 = reopened()
        assertFalse(vm2.game.princess)
        assertEquals(vm1.game.board, vm2.game.board)
    }

    @Test
    fun validHandWrittenSessionIsRestored() {
        val vm = vm(games = storeOf(validSession()))
        assertEquals(Board.of(savedCells), vm.game.board)
        assertEquals(4, vm.game.score)
        assertEquals(GameStatus.PLAYING, vm.game.status)
        assertEquals(Screen.HOME, vm.screen)
        assertTrue(vm.hasGameInProgress)
        assertEquals(3_000L, vm.elapsedMs())
    }

    private fun assertFresh(saved: SavedSession) {
        val vm = vm(games = storeOf(saved))
        assertEquals(Screen.HOME, vm.screen)
        assertEquals(GameStatus.READY, vm.game.status)
        assertFalse(vm.hasGameInProgress)
        assertEquals(0, vm.game.score)
        assertFalse(vm.game.canUndo)
        assertEquals(0L, vm.elapsedMs())
        assertNotEquals(saved.cells.toList(), vm.game.board.toIntArray().toList())
    }

    @Test
    fun boardOfWrongLengthIsIgnored() {
        assertFresh(validSession().copy(cells = IntArray(15) { 2 }))
    }

    @Test
    fun nonPowerOfTwoTileIsIgnored() {
        assertFresh(validSession().copy(cells = savedCells.copyOf().also { it[5] = 3 }))
    }

    @Test
    fun badStatusIsIgnored() {
        assertFresh(validSession().copy(status = "WON"))
    }

    @Test
    fun negativeScoreIsIgnored() {
        assertFresh(validSession().copy(score = -1))
    }

    @Test
    fun badUndoBoardIsIgnored() {
        assertFresh(validSession().copy(undoCells = IntArray(16).also { it[2] = 6 }))
    }

    @Test
    fun negativeUndoScoreIsIgnored() {
        assertFresh(validSession().copy(undoCells = savedCells, undoScore = -3))
    }

    @Test
    fun negativeTimerIsClamped() {
        assertEquals(0L, vm(games = storeOf(validSession().copy(timerElapsedMs = -5L))).elapsedMs())
    }

    @Test
    fun bestIsKeptInSettingsStore() {
        val settings = InMemorySettingsStore(best = 10)
        vm(settings).apply {
            startNewGame()
            restoreGame(pair, best = 1_000)
            move(Direction.LEFT)
        }
        assertEquals(1_000, settings.best)
        assertEquals(1_000, reopened(settings).game.best)
    }

    @Test
    fun restoredScoreAboveBestRaisesBest() {
        val settings = InMemorySettingsStore(best = 2)
        val vm = vm(settings, games = storeOf(validSession()))
        assertEquals(4, vm.game.score)
        assertEquals(4, vm.game.best)
        assertEquals(4, settings.best)
    }

    @Test
    fun noOpMoveDoesNotSave() {
        val store = CountingGameStore()
        val vm = AppViewModel(Random(7), clock, handle, InMemorySettingsStore(), store)
        vm.startNewGame()
        vm.restoreGame(pair)
        val before = store.saves
        vm.move(Direction.UP) // the pair already sits on the top row
        assertEquals(pair, vm.game.board)
        assertEquals(before, store.saves)
        vm.move(Direction.LEFT)
        assertEquals(before + 1, store.saves)
    }

    @Test
    fun invalidSessionIsCleared() {
        val store = storeOf(validSession().copy(status = "WON"))
        vm(games = store)
        assertNull(store.load())
    }
}
