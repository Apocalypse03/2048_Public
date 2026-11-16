package com.apocalypse03.game2048.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Hidden move counter (DESIGN_SPEC §13): moving moves only, undo restores it, swaps never count. */
class MoveCounterTest {
    private val z = listOf(0, 0, 0, 0)
    private val twoTiles = Board.of(listOf(listOf(2, 0, 0, 0), z, z, listOf(0, 0, 0, 4)))
    private val leftAligned = Board.of(listOf(listOf(2, 0, 0, 0), z, z, z))

    private fun engine(board: Board, moves: Int = 0, undo: UndoSnapshot? = null) =
        GameEngine.restore(Random(1), board, 0, 0, undo, GameStatus.PLAYING, moves)

    private fun firstMovingDir(e: GameEngine) = Direction.entries.first { Rules.move(e.state.board, it).moved }

    @Test
    fun freshEngineStartsAtZero() {
        assertEquals(0, GameEngine(Random(3)).state.moves)
        assertEquals(0, GameEngine(Random(3), size = 6).state.moves)
    }

    @Test
    fun movingMovesCountNoOpDoesNot() {
        val e = engine(leftAligned)
        assertFalse(e.move(Direction.LEFT))
        assertEquals(0, e.state.moves)
        assertTrue(e.move(Direction.RIGHT))
        assertEquals(1, e.state.moves)
        assertTrue(e.move(firstMovingDir(e)))
        assertEquals(2, e.state.moves)
        assertEquals(1, e.undoSnapshot!!.moves)
    }

    @Test
    fun undoRestoresSnapshotCount() {
        val e = engine(leftAligned, moves = 5)
        e.move(Direction.RIGHT)
        assertEquals(6, e.state.moves)
        assertTrue(e.undo())
        assertEquals(5, e.state.moves)
    }

    @Test
    fun princessUnlimitedUndoRestoresEachCountAndSwapDoesNotCount() {
        val e = engine(twoTiles)
        e.move(firstMovingDir(e))
        e.move(firstMovingDir(e))
        assertEquals(2, e.state.moves)
        e.enterPrincess()
        val tiles = e.state.board.toIntArray().withIndex().filter { it.value != 0 }
        val a = tiles.first()
        val b = tiles.first { it.value != a.value }
        assertTrue(e.swap(a.index / 4, a.index % 4, b.index / 4, b.index % 4))
        assertEquals(2, e.state.moves)
        assertTrue(e.undo()) // the swap
        assertEquals(2, e.state.moves)
        assertTrue(e.undo())
        assertEquals(1, e.state.moves)
        assertTrue(e.undo())
        assertEquals(0, e.state.moves)
        assertFalse(e.undo())
    }

    @Test
    fun newGameResets() {
        val e = engine(leftAligned, moves = 9)
        e.move(Direction.RIGHT)
        e.newGame()
        assertEquals(0, e.state.moves)
    }

    @Test
    fun restoreKeepsCountsAndDefaultsToZero() {
        val e = engine(leftAligned, moves = 12, undo = UndoSnapshot(twoTiles, 0, 11))
        assertEquals(12, e.state.moves)
        assertEquals(11, e.undoSnapshot!!.moves)
        e.undo()
        assertEquals(11, e.state.moves)
        assertEquals(0, GameEngine.restore(Random(1), leftAligned, 0, 0, null, GameStatus.READY).state.moves)
        assertEquals(0, UndoSnapshot(leftAligned, 0).moves)
    }
}
