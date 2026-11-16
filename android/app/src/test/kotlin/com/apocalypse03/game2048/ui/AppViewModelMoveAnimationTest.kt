package com.apocalypse03.game2048.ui

import com.apocalypse03.game2048.game.Board
import com.apocalypse03.game2048.game.Direction
import com.apocalypse03.game2048.ui.timer.FakeClock
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** [AppViewModel.moveAnimation] (DESIGN_SPEC section 17): set by successful moves only. */
class AppViewModelMoveAnimationTest {
    private val z = listOf(0, 0, 0, 0)

    /** (0,0)=2, (0,1)=4, (0,2)=2. */
    private val mixed = Board.of(listOf(listOf(2, 4, 2, 0), z, z, z))

    private fun vm(board: Board = mixed) = AppViewModel(Random(7), FakeClock()).apply {
        startNewGame()
        restoreGame(board)
    }

    @Test
    fun nullUntilFirstMove() {
        assertNull(vm().moveAnimation)
    }

    @Test
    fun successfulMoveSetsTraceAndSeqGrows() {
        val vm = vm()
        vm.move(Direction.RIGHT)
        val first = assertNotNullAnim(vm)
        assertEquals(Direction.RIGHT, first.trace.direction)
        vm.move(Direction.LEFT)
        val second = assertNotNullAnim(vm)
        assertEquals(first.seq + 1, second.seq)
        assertEquals(Direction.LEFT, second.trace.direction)
    }

    @Test
    fun undoClears() {
        val vm = vm().apply { move(Direction.RIGHT) }
        vm.undo()
        assertNull(vm.moveAnimation)
    }

    @Test
    fun newGameClears() {
        val vm = vm().apply { move(Direction.RIGHT) }
        vm.startNewGame()
        assertNull(vm.moveAnimation)
    }

    @Test
    fun restoreClears() {
        val vm = vm().apply { move(Direction.RIGHT) }
        vm.restoreGame(mixed)
        assertNull(vm.moveAnimation)
    }

    @Test
    fun enterPrincessClears() {
        val vm = vm().apply { move(Direction.RIGHT) }
        vm.enterPrincess()
        assertNull(vm.moveAnimation)
        vm.move(Direction.LEFT) // ignored in Princess Mode
        assertNull(vm.moveAnimation)
    }

    @Test
    fun exitPrincessClears() {
        val vm = vm().apply { enterPrincess() }
        vm.exitPrincess()
        vm.move(Direction.RIGHT)
        assertNotNullAnim(vm)
        vm.exitPrincess() // not in Princess Mode: still a board redraw, cleared
        assertNull(vm.moveAnimation)
    }

    @Test
    fun princessSwapClears() {
        val vm = vm()
        vm.enterPrincess()
        vm.onTileTap(0, 0) // 2
        vm.onTileTap(0, 1) // 4: swap
        assertEquals(4, vm.game.board[0, 0])
        assertNull(vm.moveAnimation)
    }

    @Test
    fun goHomeAndResumeClear() {
        val vm = vm().apply { move(Direction.RIGHT) }
        vm.goHome()
        assertNull(vm.moveAnimation)
        vm.resumeGame()
        assertNull(vm.moveAnimation)
        vm.move(Direction.LEFT)
        assertNotNullAnim(vm)
        vm.goHome()
        vm.resumeGame()
        assertNull(vm.moveAnimation)
    }

    @Test
    fun gameStateStaysSynchronous() {
        val vm = vm()
        vm.move(Direction.LEFT)
        // (0,0)=2, (0,1)=4, (0,2)=2: no merge, already packed left -> LEFT moves nothing.
        assertNull(vm.moveAnimation)
        vm.move(Direction.RIGHT)
        val b = vm.game.board
        assertEquals(listOf(2, 4, 2), listOf(b[0, 1], b[0, 2], b[0, 3]))
        assertNotNullAnim(vm)
    }

    private fun assertNotNullAnim(vm: AppViewModel): MoveAnimation {
        val anim = vm.moveAnimation
        assertNotNull(anim)
        return anim!!
    }
}
