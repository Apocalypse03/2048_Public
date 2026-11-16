package com.apocalypse03.game2048.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GameEngineTest {
    private fun b(vararg rows: List<Int>) = Board.of(rows.toList())

    private fun count(board: Board) = board.toIntArray().count { it != 0 }

    private fun engine(
        board: Board,
        score: Int = 0,
        best: Int = 0,
        undo: UndoSnapshot? = null,
        status: GameStatus = GameStatus.PLAYING,
        seed: Int = 1,
    ) = GameEngine.restore(Random(seed), board, score, best, undo, status)

    // Full, no merges possible except via the single 2-2 pair on row 0.
    private val nearlyDead = b(
        listOf(2, 2, 4, 8),
        listOf(16, 32, 64, 128),
        listOf(4, 8, 16, 32),
        listOf(64, 128, 256, 512),
    )
    private val dead = b(
        listOf(2, 4, 2, 4),
        listOf(4, 2, 4, 2),
        listOf(2, 4, 2, 4),
        listOf(4, 2, 4, 2),
    )

    @Test
    fun initialState() {
        val s = GameEngine(Random(3), initialBest = 40).state
        assertEquals(2, count(s.board))
        assertEquals(0, s.score)
        assertEquals(40, s.best)
        assertFalse(s.canUndo)
        assertEquals(GameStatus.READY, s.status)
    }

    @Test
    fun noOpMoveChangesNothing() {
        val e = engine(b(listOf(2, 0, 0, 0), listOf(0, 0, 0, 0), listOf(0, 0, 0, 0), listOf(0, 0, 0, 0)))
        val before = e.state
        assertFalse(e.move(Direction.LEFT))
        assertEquals(before, e.state)
        assertFalse(e.state.canUndo)
    }

    @Test
    fun movedMoveSpawnsExactlyOneTile() {
        val start = b(listOf(0, 0, 0, 2), listOf(0, 0, 0, 0), listOf(0, 0, 0, 0), listOf(0, 0, 0, 0))
        val e = engine(start)
        assertTrue(e.move(Direction.LEFT))
        assertEquals(2, count(e.state.board))
        assertEquals(2, e.state.board[0, 0])
        assertTrue(e.state.canUndo)
        assertEquals(GameStatus.PLAYING, e.state.status)
    }

    @Test
    fun scoreAndBestUpdateOnMerge() {
        val e = engine(
            b(listOf(2, 2, 0, 0), listOf(0, 0, 0, 0), listOf(0, 0, 0, 0), listOf(0, 0, 0, 0)),
            best = 3,
        )
        e.move(Direction.LEFT)
        assertEquals(4, e.state.score)
        assertEquals(4, e.state.best)
    }

    @Test
    fun bestNotLoweredWhenScoreBelow() {
        val e = engine(
            b(listOf(2, 2, 0, 0), listOf(0, 0, 0, 0), listOf(0, 0, 0, 0), listOf(0, 0, 0, 0)),
            best = 100,
        )
        e.move(Direction.LEFT)
        assertEquals(100, e.state.best)
    }

    @Test
    fun undoRestoresBoardAndScoreKeepsBestClearsSlot() {
        val start = b(listOf(2, 2, 0, 0), listOf(0, 0, 0, 0), listOf(0, 0, 0, 0), listOf(0, 0, 0, 0))
        val e = engine(start, score = 10, best = 10)
        e.move(Direction.LEFT)
        assertEquals(14, e.state.best)
        assertTrue(e.undo())
        assertEquals(start, e.state.board)
        assertEquals(10, e.state.score)
        assertEquals(14, e.state.best)
        assertFalse(e.state.canUndo)
        assertFalse(e.undo())
    }

    @Test
    fun undoWithEmptySlotIsNoOp() {
        val e = GameEngine(Random(5))
        val before = e.state
        assertFalse(e.undo())
        assertEquals(before, e.state)
    }

    @Test
    fun undoToStartStaysPlaying() {
        val e = GameEngine(Random(9))
        val dir = Direction.values().first { Rules.move(e.state.board, it).moved }
        e.move(dir)
        e.undo()
        assertEquals(GameStatus.PLAYING, e.state.status)
    }

    @Test
    fun moveCanEndGame() {
        // Filling the only empty cell leaves a dead board only if spawn lands there; craft: one empty
        // cell, move that keeps board full after spawn and no pairs.
        // LEFT only shifts row 3 to [4,2,4,_]. Spawn 2 at (3,3) is dead; spawn 4 pairs with its neighbours.
        val start = b(
            listOf(2, 4, 2, 4),
            listOf(4, 2, 4, 2),
            listOf(2, 4, 2, 4),
            listOf(4, 2, 0, 4),
        )
        val statuses = (0 until 50).map { seed ->
            val e = engine(start, seed = seed)
            assertTrue(e.move(Direction.LEFT))
            val s = e.state
            assertEquals(Rules.hasLegalMoves(s.board), s.status == GameStatus.PLAYING)
            s.status
        }.toSet()
        assertEquals(setOf(GameStatus.PLAYING, GameStatus.OVER), statuses)
    }

    @Test
    fun inputIgnoredWhenOver() {
        val e = engine(dead, score = 7, best = 9, status = GameStatus.OVER)
        val before = e.state
        Direction.values().forEach { assertFalse(e.move(it)) }
        assertEquals(before, e.state)
    }

    @Test
    fun undoFromOverReturnsToPlaying() {
        val e = engine(
            dead, score = 20, best = 20,
            undo = UndoSnapshot(nearlyDead, 12), status = GameStatus.OVER,
        )
        assertTrue(e.undo())
        assertEquals(GameStatus.PLAYING, e.state.status)
        assertEquals(nearlyDead, e.state.board)
        assertEquals(12, e.state.score)
        assertEquals(20, e.state.best)
        assertTrue(e.move(Direction.LEFT))
    }

    @Test
    fun newGameResetsButKeepsBest() {
        val e = engine(
            dead, score = 20, best = 50,
            undo = UndoSnapshot(nearlyDead, 12), status = GameStatus.OVER,
        )
        e.newGame()
        val s = e.state
        assertEquals(0, s.score)
        assertEquals(50, s.best)
        assertFalse(s.canUndo)
        assertEquals(GameStatus.READY, s.status)
        assertEquals(2, count(s.board))
        assertNull(e.undoSnapshot)
    }

    @Test
    fun noWinStateAt2048() {
        val e = engine(
            b(listOf(1024, 1024, 0, 0), listOf(0, 0, 0, 0), listOf(0, 0, 0, 0), listOf(0, 0, 0, 0)),
        )
        assertTrue(e.move(Direction.LEFT))
        assertEquals(2048, e.state.board[0, 0])
        assertEquals(GameStatus.PLAYING, e.state.status)
        assertEquals(2048, e.state.score)
        assertTrue(e.move(Direction.DOWN) || e.move(Direction.RIGHT) || e.move(Direction.UP))
    }

    @Test
    fun restoreRoundTripsState() {
        val snap = UndoSnapshot(nearlyDead, 4)
        val e = engine(dead, score = 8, best = 9, undo = snap, status = GameStatus.OVER)
        assertEquals(GameState(dead, 8, 9, true, GameStatus.OVER), e.state)
        assertEquals(snap, e.undoSnapshot)
    }

    @Test
    fun boardBasics() {
        val a = Board.of(listOf(listOf(1, 0, 0, 0), listOf(0, 0, 0, 0), listOf(0, 0, 0, 0), listOf(0, 0, 0, 0)))
        val flat = IntArray(16).also { it[0] = 1 }
        assertEquals(a, Board.of(flat))
        assertEquals(a.hashCode(), Board.of(flat).hashCode())
        assertNotEquals(a, Board.empty())
        assertFalse(a.equals("x"))
        assertEquals(15, a.emptyCells().size)
        assertEquals(0 to 1, a.emptyCells().first())
        assertTrue(a.toString().startsWith("Board"))
        flat[1] = 5 // factory copied
        assertEquals(0, a[0, 1])
        assertThrows(IllegalArgumentException::class.java) { a[4, 0] }
        assertThrows(IllegalArgumentException::class.java) { Board.of(IntArray(3)) }
        assertThrows(IllegalArgumentException::class.java) { Board.of(listOf(listOf(0))) }
        assertThrows(IllegalArgumentException::class.java) { Board.of(IntArray(16) { -1 }) }
    }
}
