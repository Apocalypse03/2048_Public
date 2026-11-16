package com.apocalypse03.game2048.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** 6x6 (Big) board: same rules as 4x4, only the side length changes. */
class SixBySixTest {
    private val z = listOf(0, 0, 0, 0, 0, 0)

    private fun b(vararg rows: List<Int>) = Board.of(rows.toList())

    /** 6x6 board whose row 0 is [first] and the rest empty. */
    private fun row0(first: List<Int>) = b(first, z, z, z, z, z)

    /** 6x6 board whose column 0 is [first] and the rest empty. */
    private fun col0(first: List<Int>) = Board.of(List(6) { r -> List(6) { c -> if (c == 0) first[r] else 0 } })

    private fun column0(board: Board) = List(6) { board[it, 0] }

    // Full 6x6 checkerboard of 2/4: no empty cell, no equal neighbours.
    private val checker = Board.of(List(6) { r -> List(6) { c -> if ((r + c) % 2 == 0) 2 else 4 } })

    @Test
    fun boardSizeIsInferred() {
        assertEquals(6, Board.empty(6).size)
        assertEquals(6, Board.of(IntArray(36)).size)
        assertEquals(6, checker.size)
        assertEquals(4, Board.empty().size)
        assertEquals(36, Board.empty(6).emptyCells().size)
        assertEquals(2, checker[5, 5])
        assertEquals(6, checker.toRows().size)
    }

    @Test
    fun invalidSizesRejected() {
        assertThrows(IllegalArgumentException::class.java) { Board.of(IntArray(25)) }
        assertThrows(IllegalArgumentException::class.java) { Board.empty(5) }
        assertThrows(IllegalArgumentException::class.java) { Board.of(List(5) { List(5) { 0 } }) }
        assertThrows(IllegalArgumentException::class.java) { Board.of(listOf(z, z, z, z, z, listOf(0, 0, 0, 0))) }
        assertThrows(IllegalArgumentException::class.java) { Board.empty(6)[6, 0] }
        assertThrows(IllegalArgumentException::class.java) { Rules.newGameBoard(Random(1), 5) }
        assertThrows(IllegalArgumentException::class.java) { GameEngine(Random(1), size = 3) }
    }

    @Test
    fun differentSizesAreNotEqual() {
        assertFalse(Board.empty(4) == Board.empty(6))
    }

    @Test
    fun rowSlideLeftWithMerges() {
        val r = Rules.move(row0(listOf(2, 2, 2, 2, 4, 4)), Direction.LEFT)
        assertEquals(row0(listOf(4, 4, 8, 0, 0, 0)), r.board)
        assertEquals(16, r.gain)
        assertTrue(r.moved)
    }

    @Test
    fun rowSlideRightWithMerges() {
        val r = Rules.move(row0(listOf(2, 2, 2, 2, 4, 4)), Direction.RIGHT)
        assertEquals(row0(listOf(0, 0, 0, 4, 4, 8)), r.board)
        assertEquals(16, r.gain)
        assertTrue(r.moved)
    }

    @Test
    fun columnSlideUpWithMerges() {
        val r = Rules.move(col0(listOf(2, 2, 2, 2, 4, 4)), Direction.UP)
        assertEquals(listOf(4, 4, 8, 0, 0, 0), column0(r.board))
        assertEquals(16, r.gain)
    }

    @Test
    fun columnSlideDownWithMerges() {
        val r = Rules.move(col0(listOf(2, 2, 2, 2, 4, 4)), Direction.DOWN)
        assertEquals(listOf(0, 0, 0, 4, 4, 8), column0(r.board))
        assertEquals(16, r.gain)
    }

    @Test
    fun noDoubleMergeAtSixWide() {
        // 4,4,8 must give 8,8 (not 16): a merged tile does not merge again in the same move.
        val r = Rules.move(row0(listOf(4, 4, 8, 0, 0, 16)), Direction.LEFT)
        assertEquals(row0(listOf(8, 8, 16, 0, 0, 0)), r.board)
        assertEquals(8, r.gain)
        val far = Rules.move(row0(listOf(0, 2, 0, 0, 0, 2)), Direction.LEFT)
        assertEquals(row0(listOf(4, 0, 0, 0, 0, 0)), far.board)
    }

    @Test
    fun noOpMoveAtSix() {
        val r = Rules.move(row0(listOf(2, 4, 0, 0, 0, 0)), Direction.LEFT)
        assertFalse(r.moved)
        assertEquals(0, r.gain)
    }

    @Test
    fun legalMovesOnFullBoard() {
        assertFalse(Rules.hasLegalMoves(checker))
        val pairRight = checker.toIntArray().also { it[5 * 6 + 4] = 2 } // (5,4) == (5,5)
        assertTrue(Rules.hasLegalMoves(Board.of(pairRight)))
        val pairDown = checker.toIntArray().also { it[4 * 6 + 5] = 2 } // (4,5) == (5,5)
        assertTrue(Rules.hasLegalMoves(Board.of(pairDown)))
        assertTrue(Rules.hasLegalMoves(Board.empty(6)))
    }

    @Test
    fun initialBoardHasTwoTilesWithin36Cells() {
        repeat(200) { seed ->
            val board = Rules.newGameBoard(Random(seed), 6)
            assertEquals(6, board.size)
            val tiles = board.toIntArray().filter { it != 0 }
            assertEquals(2, tiles.size)
            assertTrue(tiles.all { it == 2 || it == 4 })
        }
    }

    @Test
    fun spawnFillsOnlyEmptyCells() {
        val start = checker.toIntArray().also { it[7] = 0; it[30] = 0 }
        val board = Board.of(start)
        repeat(100) { seed ->
            val out = Rules.spawn(board, Random(seed)).toIntArray()
            val changed = out.indices.filter { out[it] != start[it] }
            assertEquals(1, changed.size)
            assertTrue(changed.single() == 7 || changed.single() == 30)
            assertTrue(out[changed.single()] == 2 || out[changed.single()] == 4)
        }
    }

    @Test
    fun engineNewGameUsesSize() {
        val e = GameEngine(Random(3), size = 6)
        assertEquals(6, e.size)
        assertEquals(6, e.state.size)
        assertEquals(2, e.state.board.toIntArray().count { it != 0 })
        e.newGame()
        assertEquals(6, e.state.board.size)
    }

    @Test
    fun engineRestoreWith36Cells() {
        val start = row0(listOf(2, 2, 0, 0, 0, 0))
        val e = GameEngine.restore(Random(1), start, 0, 0, null, GameStatus.PLAYING)
        assertEquals(6, e.size)
        assertEquals(start, e.state.board)
        assertTrue(e.move(Direction.LEFT))
        assertEquals(4, e.state.score)
        assertEquals(4, e.state.board[0, 0])
        assertEquals(6, e.state.board.size)
        assertTrue(e.undo())
        assertEquals(start, e.state.board)
    }

    @Test
    fun mismatchedUndoSizeRejected() {
        val snap = UndoSnapshot(Board.empty(4), 0)
        assertThrows(IllegalArgumentException::class.java) {
            GameEngine.restore(Random(1), Board.empty(6), 0, 0, snap, GameStatus.PLAYING)
        }
    }

    @Test
    fun swapRangeAtSix() {
        val start = row0(listOf(2, 0, 0, 0, 0, 4))
        val e = GameEngine.restore(Random(1), start, 0, 0, null, GameStatus.PLAYING)
        e.enterPrincess()
        assertFalse(e.swap(0, 0, 0, 6))
        assertFalse(e.swap(6, 0, 0, 5))
        assertFalse(e.swap(-1, 0, 0, 5))
        assertTrue(e.swap(0, 0, 0, 5))
        assertEquals(row0(listOf(4, 0, 0, 0, 0, 2)), e.state.board)
        assertTrue(e.undo())
        assertEquals(start, e.state.board)
    }
}
