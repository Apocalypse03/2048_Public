package com.apocalypse03.game2048.ui

import com.apocalypse03.game2048.game.Board
import com.apocalypse03.game2048.game.Direction
import com.apocalypse03.game2048.game.GameStatus
import com.apocalypse03.game2048.ui.timer.FakeClock
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppViewModelTimerTest {
    private val z = listOf(0, 0, 0, 0)
    private val pair = Board.of(listOf(listOf(2, 2, 0, 0), z, z, z))
    private val corner = Board.of(listOf(listOf(2, 0, 0, 0), z, z, z))

    /** One RIGHT fills the only gap and leaves no merges (the spawn lands at (3,0)): game over. */
    private val lastMove = Board.of(
        listOf(
            listOf(8, 16, 8, 16),
            listOf(16, 8, 16, 8),
            listOf(8, 16, 8, 16),
            listOf(32, 64, 128, 0),
        ),
    )

    private val clock = FakeClock()

    private fun vm(board: Board = pair) = AppViewModel(Random(7), clock).apply {
        startNewGame()
        restoreGame(board)
    }

    /** Timer started by one moving LEFT on [pair]. */
    private fun running() = vm().apply { move(Direction.LEFT) }

    @Test
    fun newGameIsIdleAtZero() {
        val vm = vm()
        clock.advance(3_000)
        assertFalse(vm.timerRunning)
        assertEquals(0L, vm.elapsedMs())
    }

    @Test
    fun noOpMoveDoesNotStart() {
        val vm = vm(corner)
        vm.move(Direction.LEFT)
        vm.move(Direction.UP)
        clock.advance(2_000)
        assertFalse(vm.timerRunning)
        assertEquals(0L, vm.elapsedMs())
    }

    @Test
    fun firstMovingMoveStarts() {
        val vm = running()
        assertTrue(vm.timerRunning)
        clock.advance(2_500)
        assertEquals(2_500L, vm.elapsedMs())
        vm.move(Direction.RIGHT)
        clock.advance(500)
        assertEquals(3_000L, vm.elapsedMs())
    }

    @Test
    fun overFreezesAndUndoOutOfOverResumes() {
        val vm = running()
        clock.advance(1_000)
        vm.restoreGame(lastMove, status = GameStatus.PLAYING)
        assertTrue("restore keeps the running timer", vm.timerRunning)
        vm.move(Direction.RIGHT)
        assertEquals(GameStatus.OVER, vm.game.status)
        assertFalse(vm.timerRunning)
        assertEquals(1_000L, vm.elapsedMs())
        clock.advance(5_000)
        assertEquals(1_000L, vm.elapsedMs())
        vm.undo()
        assertEquals(GameStatus.PLAYING, vm.game.status)
        assertTrue(vm.timerRunning)
        clock.advance(1_000)
        assertEquals(2_000L, vm.elapsedMs())
    }

    @Test
    fun undoWhilePlayingKeepsRunning() {
        val vm = running()
        clock.advance(1_000)
        vm.undo()
        assertTrue(vm.timerRunning)
        clock.advance(1_000)
        assertEquals(2_000L, vm.elapsedMs())
    }

    @Test
    fun goHomePauses() {
        val vm = running()
        clock.advance(1_000)
        vm.goHome()
        assertFalse(vm.timerRunning)
        clock.advance(10_000)
        assertEquals(1_000L, vm.elapsedMs())
    }

    @Test
    fun appStopPausesAndAppStartResumes() {
        val vm = running()
        clock.advance(1_000)
        vm.onAppStop()
        assertFalse(vm.timerRunning)
        clock.advance(10_000)
        assertEquals(1_000L, vm.elapsedMs())
        vm.onAppStart()
        assertTrue(vm.timerRunning)
        clock.advance(500)
        assertEquals(1_500L, vm.elapsedMs())
    }

    @Test
    fun appStartWhileHomeDoesNotResume() {
        val vm = running()
        clock.advance(1_000)
        vm.goHome()
        vm.onAppStop()
        vm.onAppStart()
        assertFalse(vm.timerRunning)
        clock.advance(5_000)
        assertEquals(1_000L, vm.elapsedMs())
    }

    @Test
    fun appStartBeforeFirstMoveStaysIdle() {
        val vm = vm()
        vm.onAppStop()
        vm.onAppStart()
        assertFalse(vm.timerRunning)
        assertEquals(0L, vm.elapsedMs())
    }

    @Test
    fun startNewGameResetsToIdleZero() {
        val vm = running()
        clock.advance(4_000)
        vm.startNewGame()
        assertFalse(vm.timerRunning)
        assertEquals(0L, vm.elapsedMs())
        clock.advance(1_000)
        assertEquals(0L, vm.elapsedMs())
    }

    @Test
    fun themeSelectionDoesNotAffectTimer() {
        val vm = running()
        clock.advance(1_000)
        vm.selectTheme(3)
        assertTrue(vm.timerRunning)
        clock.advance(1_000)
        assertEquals(2_000L, vm.elapsedMs())
    }

    @Test
    fun defaultConstructorsStillWork() {
        assertEquals(0L, AppViewModel().elapsedMs())
        assertEquals(0L, AppViewModel(Random(1)).elapsedMs())
    }
}
