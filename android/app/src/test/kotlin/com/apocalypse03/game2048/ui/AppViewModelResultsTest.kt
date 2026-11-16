package com.apocalypse03.game2048.ui

import androidx.lifecycle.SavedStateHandle
import com.apocalypse03.game2048.game.Board
import com.apocalypse03.game2048.game.Direction
import com.apocalypse03.game2048.game.GameStatus
import com.apocalypse03.game2048.ui.data.GameResult
import com.apocalypse03.game2048.ui.data.InMemoryGameStore
import com.apocalypse03.game2048.ui.data.InMemorySettingsStore
import com.apocalypse03.game2048.ui.data.SavedSession
import com.apocalypse03.game2048.ui.timer.FakeClock
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Result recording, ids and per-mode lists in the ViewModel (DESIGN_SPEC section 16). */
class AppViewModelResultsTest {
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
    private val settings = InMemorySettingsStore()
    private val games = InMemoryGameStore()

    private fun vm(store: InMemorySettingsStore = settings) =
        AppViewModel(Random(7), clock, SavedStateHandle(), store, games)

    private fun AppViewModel.normal() = results(GameMode.NORMAL)

    @Test
    fun overRecordsOnce() {
        val vm = vm()
        vm.startNewGame()
        vm.restoreGame(lastMove, score = 300, status = GameStatus.PLAYING, moves = 4)
        vm.move(Direction.RIGHT)
        clock.advance(5_000)
        assertEquals(GameStatus.OVER, vm.game.status)
        val r = vm.normal().single()
        assertEquals(300, r.score)
        assertEquals(128, r.tile)
        assertEquals(5, r.moves)
        assertEquals(vm.elapsedMs(), r.timeMs)
        assertEquals(settings.results(GameMode.NORMAL), vm.normal())
        vm.startNewGame() // replacing the OVER game keeps its single entry
        assertEquals(1, vm.normal().size)
        assertEquals(r, vm.normal().single())
    }

    @Test
    fun undoOutOfOverThenOverAgainUpsertsSameId() {
        val vm = vm()
        vm.startNewGame()
        vm.restoreGame(lastMove, score = 300, status = GameStatus.PLAYING, moves = 4)
        vm.move(Direction.RIGHT)
        val first = vm.normal().single()
        vm.undo()
        assertEquals(GameStatus.PLAYING, vm.game.status)
        clock.advance(2_000)
        vm.move(Direction.RIGHT)
        assertEquals(GameStatus.OVER, vm.game.status)
        val second = vm.normal().single()
        assertEquals(first.id, second.id)
        assertEquals(vm.elapsedMs(), second.timeMs)
    }

    @Test
    fun newGameOverZeroMoveGameRecordsNothing() {
        val vm = vm()
        vm.startNewGame()
        vm.startNewGame()
        vm.goHome()
        vm.requestNewGame()
        assertTrue(vm.normal().isEmpty())
        assertTrue(settings.results(GameMode.NORMAL).isEmpty())
    }

    @Test
    fun newGameOverMovedGameRecordsOnce() {
        val vm = vm()
        vm.startNewGame()
        vm.restoreGame(pair)
        vm.move(Direction.LEFT)
        clock.advance(1_500)
        vm.startNewGame()
        val r = vm.normal().single()
        assertEquals(4, r.score)
        assertEquals(1, r.moves)
        assertEquals(4, r.tile)
        vm.startNewGame() // the new game has 0 moves
        assertEquals(1, vm.normal().size)
    }

    @Test
    fun confirmedNewGameFromHomeRecordsTheGameInProgress() {
        val vm = vm()
        vm.startNewGame()
        vm.restoreGame(pair)
        vm.move(Direction.LEFT)
        vm.goHome()
        vm.requestNewGame()
        vm.confirmNewGameYes()
        assertEquals(1, vm.normal().size)
    }

    @Test
    fun eachGameGetsANewId() {
        val vm = vm()
        vm.startNewGame()
        vm.restoreGame(pair)
        vm.move(Direction.LEFT)
        vm.startNewGame()
        vm.restoreGame(pair)
        vm.move(Direction.LEFT)
        vm.startNewGame()
        val ids = vm.normal().map { it.id }
        assertEquals(2, ids.toSet().size)
    }

    @Test
    fun keepsTop20AndNeverReusesAStoredId() {
        val stored = (1..20).joinToString(";") { "$it,${1_000 + it},64,10,1000" }
        val store = InMemorySettingsStore(results = stored) // counter lost: still 1
        val vm = vm(store)
        vm.startNewGame()
        vm.restoreGame(pair)
        vm.move(Direction.LEFT)
        vm.startNewGame() // score 4: below the 20th
        assertEquals((1L..20L).toSet(), vm.normal().map { it.id }.toSet())
        vm.restoreGame(pair, score = 5_000)
        vm.move(Direction.LEFT)
        vm.startNewGame()
        val list = vm.normal()
        assertEquals(20, list.size)
        assertEquals(5_004, list.first().score)
        assertTrue(list.first().id > 20)
        assertTrue(list.none { it.id == 1L }) // lowest score dropped
        assertEquals(list.sortedByDescending { it.score }, list)
    }

    @Test
    fun corruptStoredEntriesAreSkipped() {
        val store = InMemorySettingsStore(results = "1,10,8,3,100;garbage;2,-1,8,3,5;3,10,6,3,5;5,20,16,2,7")
        assertEquals(listOf(5L, 1L), vm(store).normal().map { it.id })
    }

    @Test
    fun modesKeepSeparateLists() {
        val vm = vm()
        vm.stepMode(1)
        vm.startNewGame()
        assertEquals(6, vm.game.size)
        val row = listOf(2, 2, 0, 0, 0, 0)
        val zero = List(6) { 0 }
        vm.restoreGame(Board.of(listOf(row, zero, zero, zero, zero, zero)))
        vm.move(Direction.LEFT)
        vm.startNewGame()
        assertEquals(1, vm.results(GameMode.PLUS).size)
        assertTrue(vm.normal().isEmpty())
        assertEquals(1, settings.results(GameMode.PLUS).size)
        assertTrue(settings.results(GameMode.NORMAL).isEmpty())
    }

    @Test
    fun restoreWithoutGameIdAssignsAndPersistsOne() {
        games.save(SavedSession(pair.toIntArray(), 0, null, 0, "PLAYING", 1_000L, true, moves = 2))
        val vm = vm()
        val id = games.load()?.gameId
        assertNotNull(id)
        assertEquals(id!! + 1, settings.resultNextId)
        vm.startNewGame()
        assertEquals(id, vm.normal().single().id)
    }

    @Test
    fun restoredGameIdIsKept() {
        settings.resultNextId = 100
        games.save(SavedSession(pair.toIntArray(), 8, null, 0, "PLAYING", 1_000L, true, moves = 3, gameId = 42L))
        val vm = vm()
        vm.startNewGame()
        val r: GameResult = vm.normal().single()
        assertEquals(42L, r.id)
        assertEquals(8, r.score)
        assertEquals(3, r.moves)
        assertEquals(2, r.tile)
        assertTrue(games.load()!!.gameId!! >= 100)
    }
}
