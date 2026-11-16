package com.apocalypse03.game2048.ui

import com.apocalypse03.game2048.game.Board
import com.apocalypse03.game2048.game.Direction
import com.apocalypse03.game2048.game.GameStatus
import com.apocalypse03.game2048.ui.timer.FakeClock
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Princess Mode in the ViewModel (DESIGN_SPEC section 11): tap burst, selection, timer. */
class AppViewModelPrincessTest {
    private val z = listOf(0, 0, 0, 0)

    /** (0,0)=2, (0,1)=4, (0,2)=2; the rest empty. */
    private val mixed = Board.of(listOf(listOf(2, 4, 2, 0), z, z, z))

    /** Full, no merges: game over. Swapping (0,0) and (0,1) puts 4,4 side by side. */
    private val full = Board.of(
        listOf(listOf(2, 4, 2, 4), listOf(4, 2, 4, 2), listOf(2, 4, 2, 4), listOf(4, 2, 4, 2)),
    )

    private val clock = FakeClock()

    private fun vm(board: Board = mixed, status: GameStatus = GameStatus.READY) =
        AppViewModel(Random(7), clock).apply {
            startNewGame()
            restoreGame(board, status = status)
        }

    private fun AppViewModel.burst(taps: Int = 5, gapMs: Long = 400) {
        repeat(taps) {
            onTimerTap()
            clock.advance(gapMs)
        }
    }

    private fun princess(board: Board = mixed, status: GameStatus = GameStatus.READY) =
        vm(board, status).apply { burst() }

    @Test
    fun fiveTapsWithin400MsEnter() {
        val vm = vm()
        vm.burst(4)
        assertFalse(vm.game.princess)
        vm.onTimerTap()
        assertTrue(vm.game.princess)
    }

    @Test
    fun gapOf401MsResetsBurst() {
        val vm = vm()
        vm.burst(4)
        clock.advance(1) // the fourth gap is now 401 ms
        vm.onTimerTap() // count restarts at 1
        assertFalse(vm.game.princess)
        vm.burst(3)
        assertFalse(vm.game.princess)
        vm.onTimerTap()
        assertTrue(vm.game.princess)
    }

    @Test
    fun tapsOnHomeIgnored() {
        val vm = vm()
        vm.goHome()
        vm.burst(10)
        assertFalse(vm.game.princess)
    }

    @Test
    fun selectDeselectAndEmptyCell() {
        val vm = princess()
        vm.onTileTap(1, 1)
        assertNull(vm.selected)
        vm.onTileTap(0, 1)
        assertEquals(1, vm.selected)
        vm.onTileTap(1, 1)
        assertEquals("empty cell is a no-op", 1, vm.selected)
        vm.onTileTap(0, 1)
        assertNull(vm.selected)
        assertEquals(mixed, vm.game.board)
    }

    @Test
    fun tileTapIgnoredOutsidePrincess() {
        val vm = vm()
        vm.onTileTap(0, 0)
        assertNull(vm.selected)
    }

    @Test
    fun swapThenUndoRestoresAndClearsSelection() {
        val vm = princess()
        vm.onTileTap(0, 0)
        vm.onTileTap(0, 1)
        assertNull(vm.selected)
        val b = vm.game.board
        assertEquals(listOf(4, 2, 2), listOf(b[0, 0], b[0, 1], b[0, 2]))
        assertTrue(vm.game.canUndo)
        vm.onTileTap(0, 2)
        assertEquals(2, vm.selected)
        vm.undo()
        assertNull(vm.selected)
        assertEquals(mixed, vm.game.board)
        assertFalse(vm.game.canUndo)
    }

    @Test
    fun equalValuesOnlyDeselect() {
        val vm = princess()
        vm.onTileTap(0, 0)
        vm.onTileTap(0, 2)
        assertNull(vm.selected)
        assertEquals(mixed, vm.game.board)
        assertFalse(vm.game.canUndo)
    }

    @Test
    fun moveIgnoredInPrincess() {
        val vm = princess()
        vm.move(Direction.RIGHT)
        assertEquals(mixed, vm.game.board)
        assertFalse(vm.timerRunning)
    }

    @Test
    fun timerFrozenInPrincessAndResumesOnExit() {
        val vm = vm()
        vm.move(Direction.RIGHT)
        assertTrue(vm.timerRunning)
        vm.burst() // enters on the fifth tap, 1 600 ms later
        assertTrue(vm.game.princess)
        assertFalse(vm.timerRunning)
        val frozen = vm.elapsedMs()
        clock.advance(5_000)
        assertEquals(frozen, vm.elapsedMs())
        vm.exitPrincess()
        assertEquals(GameStatus.PLAYING, vm.game.status)
        assertTrue(vm.timerRunning)
        clock.advance(1_000)
        assertEquals(frozen + 1_000, vm.elapsedMs())
    }

    @Test
    fun exitFromReadyBeforeFirstMoveKeepsTimerIdle() {
        val vm = princess()
        vm.exitPrincess()
        assertEquals(GameStatus.READY, vm.game.status)
        assertFalse(vm.timerRunning)
        assertEquals(0L, vm.elapsedMs())
    }

    @Test
    fun rescueFromOver() {
        val vm = princess(full, GameStatus.OVER)
        assertTrue(vm.game.princess)
        vm.onTileTap(0, 0)
        vm.onTileTap(0, 1)
        vm.exitPrincess()
        assertFalse(vm.game.princess)
        assertEquals(GameStatus.PLAYING, vm.game.status)
        vm.move(Direction.LEFT) // row 0 is 4,2,2,4 after the swap: the 2s merge
        assertEquals(4, vm.game.score)
        assertEquals(4, vm.game.board[0, 1])
    }

    @Test
    fun goHomeExitsPrincess() {
        val vm = princess()
        vm.onTileTap(0, 0)
        vm.goHome()
        assertFalse(vm.game.princess)
        assertNull(vm.selected)
    }

    @Test
    fun startNewGameExitsPrincess() {
        val vm = princess()
        vm.onTileTap(0, 0)
        vm.startNewGame()
        assertFalse(vm.game.princess)
        assertNull(vm.selected)
    }

    @Test
    fun selectThemeKeepsPrincess() {
        val vm = princess()
        vm.onTileTap(0, 0)
        vm.selectTheme(3)
        assertTrue(vm.game.princess)
        assertEquals(0, vm.selected)
    }
}
