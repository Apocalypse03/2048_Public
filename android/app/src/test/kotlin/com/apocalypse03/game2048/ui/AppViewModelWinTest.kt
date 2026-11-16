package com.apocalypse03.game2048.ui

import androidx.lifecycle.SavedStateHandle
import com.apocalypse03.game2048.game.Board
import com.apocalypse03.game2048.game.Direction
import com.apocalypse03.game2048.game.GameStatus
import com.apocalypse03.game2048.ui.data.InMemoryGameStore
import com.apocalypse03.game2048.ui.data.InMemorySettingsStore
import com.apocalypse03.game2048.ui.data.SavedSession
import com.apocalypse03.game2048.ui.data.WinRecord
import com.apocalypse03.game2048.ui.timer.FakeClock
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Win detection, win screen state, move counter persistence and the best-win record (DESIGN_SPEC section 13). */
class AppViewModelWinTest {
    private val z4 = listOf(0, 0, 0, 0)

    /** UP moves the lone 2 (starts the timer, move 1); LEFT then merges 1024 + 1024 (move 2). */
    private val pair4 = Board.of(listOf(listOf(1024, 1024, 0, 0), z4, z4, listOf(0, 0, 0, 2)))

    private val z6 = List(6) { 0 }
    private val pair6 = Board.of(
        listOf(listOf(1024, 1024, 0, 0, 0, 0), z6, z6, z6, z6, listOf(0, 0, 0, 0, 0, 2)),
    )

    /** LEFT merges 1024 + 1024; the spawn fills the only gap and no merges remain: win and game over. */
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

    private fun vm(
        settings: InMemorySettingsStore = InMemorySettingsStore(),
        state: SavedStateHandle = handle,
    ) = AppViewModel(Random(7), clock, state, settings, store)

    /** Game screen on [board]; UP then 5 s, then LEFT: won with 2 moves in 5 s. */
    private fun won(
        settings: InMemorySettingsStore = InMemorySettingsStore(),
        board: Board = pair4,
        plus: Boolean = false,
    ): AppViewModel = vm(settings).apply {
        if (plus) stepMode(1)
        startNewGame()
        restoreGame(board)
        move(Direction.UP)
        clock.advance(5_000)
        move(Direction.LEFT)
    }

    private fun max(vm: AppViewModel) = vm.game.board.toIntArray().max()

    @Test
    fun mergeTo2048Wins4x4AndFreezesTimer() {
        val s = InMemorySettingsStore()
        val vm = won(s)
        assertTrue(vm.won)
        assertTrue(vm.winPending)
        assertEquals(2048, max(vm))
        assertEquals(2, vm.game.moves)
        assertFalse(vm.timerRunning)
        clock.advance(10_000)
        assertEquals(5_000L, vm.elapsedMs())
        assertEquals(WinRecord(2, 5_000), s.win)
        assertNull(s.winPlus)
        assertTrue(vm.hasGameInProgress)
    }

    @Test
    fun mergeTo2048Wins6x6AndRecordsPlus() {
        val s = InMemorySettingsStore()
        val vm = won(s, pair6, plus = true)
        assertEquals(6, vm.game.size)
        assertTrue(vm.winPending)
        assertEquals(WinRecord(2, 5_000), s.winPlus)
        assertNull(s.win)
    }

    @Test
    fun noWinBelow2048() {
        val vm = vm().apply {
            startNewGame()
            restoreGame(Board.of(listOf(listOf(512, 512, 0, 0), z4, z4, z4)))
            move(Direction.LEFT)
        }
        assertFalse(vm.won)
        assertFalse(vm.winPending)
    }

    @Test
    fun inputUndoAndPrincessIgnoredWhilePending() {
        val vm = won()
        val before = vm.game
        assertTrue(before.canUndo)
        Direction.entries.forEach(vm::move)
        vm.undo()
        repeat(AppViewModel.PRINCESS_TAPS) {
            vm.onTimerTap()
            clock.advance(100)
        }
        vm.enterPrincess()
        assertEquals(before, vm.game)
        assertFalse(vm.game.princess)
        assertTrue(vm.winPending)
    }

    @Test
    fun endlessResumesTimerAndNeverRetriggers() {
        val s = InMemorySettingsStore()
        val vm = won(s)
        vm.continueEndless()
        assertFalse(vm.winPending)
        assertTrue(vm.won)
        assertTrue(vm.timerRunning)
        clock.advance(1_000)
        assertEquals(6_000L, vm.elapsedMs())
        // Undo below 2048 and merge again: no second win screen, no second record.
        vm.undo()
        assertTrue(max(vm) < 2048)
        vm.move(Direction.LEFT)
        assertEquals(2048, max(vm))
        assertFalse(vm.winPending)
        assertTrue(vm.won)
        assertEquals(WinRecord(2, 5_000), s.win)
        vm.continueEndless() // no-op when nothing is pending
        assertTrue(vm.timerRunning)
    }

    @Test
    fun winningMoveThatEndsTheGameShowsWinFirst() {
        val vm = vm().apply {
            startNewGame()
            restoreGame(winAndOver, status = GameStatus.PLAYING)
            move(Direction.LEFT)
        }
        assertEquals(GameStatus.OVER, vm.game.status)
        assertTrue(vm.winPending)
        assertTrue(vm.hasGameInProgress)
        vm.continueEndless()
        assertEquals(GameStatus.OVER, vm.game.status)
        assertFalse(vm.timerRunning)
        assertFalse(vm.hasGameInProgress)
    }

    @Test
    fun newGameResetsWinSoTheNextGameCanWin() {
        val vm = won()
        vm.goHome()
        vm.requestNewGame()
        vm.confirmNewGameYes()
        assertFalse(vm.won)
        assertFalse(vm.winPending)
        assertEquals(0, vm.game.moves)
        vm.restoreGame(pair4)
        vm.move(Direction.LEFT)
        assertTrue(vm.winPending)
    }

    @Test
    fun backKeepsWinPendingAndResumeShowsIt() {
        val vm = won()
        vm.goHome()
        assertEquals(Screen.HOME, vm.screen)
        assertTrue(vm.winPending)
        assertTrue(vm.hasGameInProgress)
        vm.resumeGame()
        assertEquals(Screen.GAME, vm.screen)
        assertTrue(vm.winPending)
        assertFalse(vm.timerRunning)
    }

    @Test
    fun newGameFromWinKeepsCurrentGameSizeNotSelector() {
        val s = InMemorySettingsStore()
        val vm = won(s)
        vm.stepMode(1) // selector now 2048+, current game is 4x4
        vm.requestNewGame()
        assertTrue(vm.confirmNewGame)
        vm.confirmNewGameYes()
        assertFalse(vm.confirmNewGame)
        assertEquals(Screen.GAME, vm.screen)
        assertEquals(4, vm.game.size)
        assertEquals(GameStatus.READY, vm.game.status)
        assertEquals(0, vm.game.moves)
        assertEquals(0L, vm.elapsedMs())
        assertFalse(vm.won)
        assertFalse(vm.winPending)
        assertEquals(GameMode.PLUS, vm.mode)
        assertEquals(GameMode.PLUS, s.mode)
    }

    @Test
    fun newGameFromWinNoKeepsWinPending() {
        val vm = won()
        val before = vm.game
        vm.requestNewGame()
        vm.confirmNewGameNo()
        assertFalse(vm.confirmNewGame)
        assertTrue(vm.winPending)
        assertEquals(before, vm.game)
        assertEquals(Screen.GAME, vm.screen)
    }

    @Test
    fun recordKeepsFewestMovesThenShortestTime() {
        val fewer = InMemorySettingsStore(win = WinRecord(1, 60_000))
        won(fewer)
        assertEquals(WinRecord(1, 60_000), fewer.win)

        val slower = InMemorySettingsStore(win = WinRecord(2, 9_000))
        clock.now = 1_000
        won(slower)
        assertEquals(WinRecord(2, 5_000), slower.win)

        val faster = InMemorySettingsStore(win = WinRecord(2, 1_000))
        clock.now = 1_000
        won(faster)
        assertEquals(WinRecord(2, 1_000), faster.win)

        val more = InMemorySettingsStore(win = WinRecord(3, 1_000_000))
        clock.now = 1_000
        won(more)
        assertEquals(WinRecord(2, 5_000), more.win)
    }

    @Test
    fun winStatePersistsAcrossAppRestart() {
        won()
        val again = vm(state = SavedStateHandle())
        assertEquals(Screen.HOME, again.screen)
        assertTrue(again.won)
        assertTrue(again.winPending)
        assertEquals(2, again.game.moves)
        assertTrue(again.hasGameInProgress)
        again.onAppStart()
        again.resumeGame()
        assertEquals(Screen.GAME, again.screen)
        assertFalse(again.timerRunning)
        assertEquals(5_000L, again.elapsedMs())
        again.continueEndless()
        assertTrue(again.timerRunning)
        // Endless is saved too: a further restart has no pending win.
        val third = vm(state = SavedStateHandle())
        assertTrue(third.won)
        assertFalse(third.winPending)
    }

    @Test
    fun winStateSurvivesProcessDeathOnGameScreen() {
        won()
        val again = vm()
        assertEquals(Screen.GAME, again.screen)
        assertTrue(again.winPending)
        assertEquals(2, again.game.moves)
    }

    @Test
    fun movesAndUndoMovesPersist() {
        vm().apply {
            startNewGame()
            restoreGame(pair4, moves = 7)
            move(Direction.UP)
        }
        val saved = store.load()!!
        assertEquals(8, saved.moves)
        assertEquals(7, saved.undoMoves)
        val again = vm(state = SavedStateHandle())
        assertEquals(8, again.game.moves)
        again.resumeGame()
        again.undo()
        assertEquals(7, again.game.moves)
    }

    private fun session(
        moves: Int = 0,
        undoMoves: Int = 0,
        won: Boolean = false,
        winPending: Boolean = false,
    ) = SavedSession(
        cells = intArrayOf(2048, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
        score = 20_000,
        undoCells = intArrayOf(1024, 1024, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
        undoScore = 18_000,
        status = "PLAYING",
        timerElapsedMs = 3_000L,
        timerStarted = true,
        moves = moves,
        undoMoves = undoMoves,
        won = won,
        winPending = winPending,
    )

    @Test
    fun oldSessionWithoutNewFieldsRestores() {
        store.save(
            SavedSession(
                cells = intArrayOf(4, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
                score = 4,
                undoCells = null,
                undoScore = 0,
                status = "PLAYING",
                timerElapsedMs = 3_000L,
                timerStarted = true,
            ),
        )
        val vm = vm()
        assertEquals(4, vm.game.score)
        assertEquals(0, vm.game.moves)
        assertFalse(vm.won)
        assertFalse(vm.winPending)
    }

    @Test
    fun invalidWinFieldsDiscardTheSession() {
        listOf(
            session(moves = -1),
            session(moves = 3, undoMoves = -1),
            session(moves = 3, undoMoves = 2, won = false, winPending = true),
        ).forEach { bad ->
            store.save(bad)
            val vm = vm(state = SavedStateHandle())
            assertEquals(0, vm.game.score)
            assertNull(store.load())
        }
    }

    @Test
    fun wonSessionWithout2048Pending() {
        // A restored won game (2048 on the board) whose win was already dismissed does not win again.
        store.save(session(moves = 40, undoMoves = 39, won = true))
        val vm = vm(state = SavedStateHandle())
        assertTrue(vm.won)
        assertFalse(vm.winPending)
        vm.resumeGame()
        vm.undo()
        assertEquals(39, vm.game.moves)
        vm.move(Direction.LEFT)
        assertFalse(vm.winPending)
    }

    @Test
    fun winRecordOrdering() {
        assertTrue(WinRecord(5, 100).isBetterThan(null))
        assertTrue(WinRecord(4, 900).isBetterThan(WinRecord(5, 100)))
        assertTrue(WinRecord(5, 99).isBetterThan(WinRecord(5, 100)))
        assertFalse(WinRecord(5, 100).isBetterThan(WinRecord(5, 100)))
        assertFalse(WinRecord(6, 1).isBetterThan(WinRecord(5, 100)))
    }
}
