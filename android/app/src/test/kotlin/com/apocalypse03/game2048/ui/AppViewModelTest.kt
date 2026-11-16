package com.apocalypse03.game2048.ui

import com.apocalypse03.game2048.game.Board
import com.apocalypse03.game2048.game.Direction
import com.apocalypse03.game2048.game.GameStatus
import com.apocalypse03.game2048.ui.timer.FakeClock
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AppViewModelTest {
    private val z = listOf(0, 0, 0, 0)
    private val pair = Board.of(listOf(listOf(2, 2, 0, 0), z, z, z))
    private val corner = Board.of(listOf(listOf(2, 0, 0, 0), z, z, z))

    /** Fake clock: the Android SystemClock is not available in plain JVM tests. */
    private fun vm(board: Board) = AppViewModel(Random(7), FakeClock()).apply {
        startNewGame()
        restoreGame(board)
    }

    @Test
    fun startsOnHomeWithReadyGame() {
        val vm = AppViewModel(Random(1))
        assertEquals(Screen.HOME, vm.screen)
        assertEquals(GameStatus.READY, vm.game.status)
        assertEquals(0, vm.game.score)
        assertFalse(vm.game.canUndo)
    }

    @Test
    fun seededNewGamesAreReproducible() {
        assertEquals(AppViewModel(Random(5)).game.board, AppViewModel(Random(5)).game.board)
    }

    @Test
    fun movingUpdatesBoardScoreAndStatus() {
        val vm = vm(pair)
        vm.move(Direction.LEFT)
        assertEquals(4, vm.game.board[0, 0])
        assertNotEquals(pair, vm.game.board)
        assertEquals(4, vm.game.score)
        assertEquals(4, vm.game.best)
        assertEquals(GameStatus.PLAYING, vm.game.status)
        assertTrue(vm.game.canUndo)
    }

    @Test
    fun noOpMoveLeavesState() {
        val vm = vm(corner)
        val before = vm.game
        vm.move(Direction.LEFT)
        vm.move(Direction.UP)
        assertEquals(before, vm.game)
    }

    @Test
    fun undoRestores() {
        val vm = vm(pair)
        vm.move(Direction.LEFT)
        vm.undo()
        assertEquals(pair, vm.game.board)
        assertEquals(0, vm.game.score)
        assertFalse(vm.game.canUndo)
        assertEquals(4, vm.game.best)
    }

    @Test
    fun newGameResetsScoreKeepsBest() {
        val vm = vm(pair)
        vm.move(Direction.LEFT)
        vm.goHome()
        assertEquals(Screen.HOME, vm.screen)
        assertEquals(4, vm.game.score)
        vm.startNewGame()
        assertEquals(Screen.GAME, vm.screen)
        assertEquals(0, vm.game.score)
        assertEquals(4, vm.game.best)
        assertEquals(GameStatus.READY, vm.game.status)
        assertFalse(vm.game.canUndo)
        assertEquals(14, vm.game.board.emptyCells().size)
    }

    @Test
    fun themeChangeKeepsGame() {
        val vm = vm(pair)
        vm.move(Direction.LEFT)
        val before = vm.game
        vm.selectTheme(3)
        vm.stepMode(1)
        assertSame(before, vm.game)
    }

    @Test
    fun movesIgnoredWhenOver() {
        val full = Board.of(
            listOf(listOf(2, 4, 2, 4), listOf(4, 2, 4, 2), listOf(2, 4, 2, 4), listOf(4, 2, 4, 2)),
        )
        val vm = AppViewModel(Random(7))
        vm.restoreGame(full, score = 10, best = 10, status = GameStatus.OVER)
        val before = vm.game
        Direction.entries.forEach { vm.move(it) }
        assertEquals(before, vm.game)
    }
}
