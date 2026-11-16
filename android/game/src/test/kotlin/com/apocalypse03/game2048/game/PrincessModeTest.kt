package com.apocalypse03.game2048.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Princess Mode (DESIGN_SPEC §11): swaps, unlimited undo, exit status rules. */
class PrincessModeTest {
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

    private val twoTiles = b(listOf(2, 0, 0, 0), listOf(0, 0, 0, 0), listOf(0, 0, 0, 0), listOf(0, 0, 0, 4))
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

    private fun firstMovingDir(e: GameEngine) = Direction.values().first { Rules.move(e.state.board, it).moved }

    @Test
    fun enterSetsPrincessKeepsStatusAndBlocksMoves() {
        val e = engine(twoTiles, score = 6, best = 9)
        assertFalse(e.state.princess)
        e.enterPrincess()
        val s = e.state
        assertTrue(s.princess)
        assertEquals(GameStatus.PLAYING, s.status)
        Direction.values().forEach { assertFalse(e.move(it)) }
        assertEquals(s, e.state)
    }

    @Test
    fun enterTwiceIsNoOp() {
        val e = engine(twoTiles, status = GameStatus.READY)
        e.enterPrincess()
        assertTrue(e.swap(0, 0, 3, 3))
        e.enterPrincess() // must not reset entry board
        e.exitPrincess()
        assertEquals(GameStatus.PLAYING, e.state.status)
    }

    @Test
    fun swapSwapsValuesWithoutScoreBestOrSpawn() {
        val e = engine(twoTiles, score = 6, best = 9)
        e.enterPrincess()
        assertTrue(e.swap(0, 0, 3, 3))
        val s = e.state
        assertEquals(4, s.board[0, 0])
        assertEquals(2, s.board[3, 3])
        assertEquals(2, count(s.board))
        assertEquals(6, s.score)
        assertEquals(9, s.best)
        assertTrue(s.canUndo)
        assertTrue(s.princess)
    }

    @Test
    fun invalidSwapsReturnFalseAndChangeNothing() {
        val board = b(listOf(2, 2, 0, 0), listOf(0, 0, 0, 0), listOf(0, 0, 0, 0), listOf(0, 0, 0, 4))
        val e = engine(board)
        assertFalse(e.swap(0, 0, 3, 3)) // outside princess
        e.enterPrincess()
        val before = e.state
        assertFalse(before.canUndo)
        assertFalse(e.swap(0, 0, 0, 1)) // equal values
        assertFalse(e.swap(0, 0, 0, 2)) // empty cell
        assertFalse(e.swap(0, 2, 0, 0)) // empty cell first
        assertFalse(e.swap(0, 0, 0, 0)) // same cell
        assertFalse(e.swap(-1, 0, 3, 3)) // out of range
        assertFalse(e.swap(0, 0, 3, 4))
        assertFalse(e.swap(4, 0, 3, 3))
        assertFalse(e.swap(0, -1, 3, 3))
        assertEquals(before, e.state)
        assertFalse(e.undo())
    }

    @Test
    fun unlimitedUndoWalksBackWholeGame() {
        val e = GameEngine(Random(42), initialBest = 0)
        val history = mutableListOf<Pair<Board, Int>>()
        repeat(6) {
            history += e.state.board to e.state.score
            assertTrue(e.move(firstMovingDir(e)))
        }
        val bestAfter = e.state.best
        e.enterPrincess()
        assertTrue(e.state.canUndo)
        history.asReversed().forEach { (board, score) ->
            assertTrue(e.undo())
            assertEquals(board, e.state.board)
            assertEquals(score, e.state.score)
            assertEquals(bestAfter, e.state.best)
            assertTrue(e.state.princess)
        }
        assertEquals(0, e.state.score)
        assertFalse(e.state.canUndo)
        assertFalse(e.undo())
        assertEquals(history.first().first, e.state.board)
    }

    @Test
    fun undoMixesSwapsAndMovesInLifoOrder() {
        val start = b(listOf(2, 2, 0, 0), listOf(0, 0, 0, 0), listOf(0, 0, 0, 0), listOf(0, 0, 0, 0))
        val e = engine(start)
        assertTrue(e.move(Direction.LEFT))
        val afterMove = e.state.board
        val scoreAfterMove = e.state.score
        e.enterPrincess()
        val tiles = afterMove.toRows().flatMapIndexed { r, row -> row.mapIndexedNotNull { c, v -> if (v != 0) Triple(r, c, v) else null } }
        val (a, bb) = tiles[0] to tiles[1]
        val swapped = a.third != bb.third
        if (swapped) assertTrue(e.swap(a.first, a.second, bb.first, bb.second))
        val afterSwap = e.state.board
        if (swapped) {
            assertTrue(e.undo())
            assertEquals(afterMove, e.state.board)
            assertEquals(scoreAfterMove, e.state.score)
        } else {
            assertEquals(afterMove, afterSwap)
        }
        assertTrue(e.undo())
        assertEquals(start, e.state.board)
        assertEquals(0, e.state.score)
        assertFalse(e.undo())
    }

    @Test
    fun swapsStackLifo() {
        val board = b(listOf(2, 4, 8, 0), listOf(0, 0, 0, 0), listOf(0, 0, 0, 0), listOf(0, 0, 0, 0))
        val e = engine(board)
        e.enterPrincess()
        assertTrue(e.swap(0, 0, 0, 1)) // 4,2,8
        val first = e.state.board
        assertTrue(e.swap(0, 1, 0, 2)) // 4,8,2
        assertEquals(listOf(4, 8, 2, 0), e.state.board.toRows()[0])
        assertTrue(e.undo())
        assertEquals(first, e.state.board)
        assertTrue(e.undo())
        assertEquals(board, e.state.board)
        assertFalse(e.state.canUndo)
    }

    @Test
    fun exitWithoutChangesFromOverStaysOver() {
        val e = engine(dead, status = GameStatus.OVER)
        e.enterPrincess()
        assertEquals(GameStatus.OVER, e.state.status)
        e.exitPrincess()
        assertFalse(e.state.princess)
        assertEquals(GameStatus.OVER, e.state.status)
    }

    @Test
    fun rescueFromOverBecomesPlaying() {
        val e = engine(dead, score = 30, best = 30, status = GameStatus.OVER)
        e.enterPrincess()
        assertTrue(e.swap(0, 0, 0, 1)) // row 0 -> 4,2,2,4
        assertEquals(GameStatus.OVER, e.state.status) // not recomputed while in princess
        e.exitPrincess()
        assertEquals(GameStatus.PLAYING, e.state.status)
        assertTrue(e.move(Direction.LEFT))
    }

    @Test
    fun swapThatKillsBoardExitsToOver() {
        val e = engine(nearlyDead)
        e.enterPrincess()
        assertTrue(e.swap(0, 1, 0, 2)) // row 0 -> 2,4,2,8: no pairs anywhere
        assertEquals(GameStatus.PLAYING, e.state.status)
        e.exitPrincess()
        assertEquals(GameStatus.OVER, e.state.status)
    }

    @Test
    fun readyUnchangedExitsToReady() {
        val e = engine(twoTiles, status = GameStatus.READY)
        e.enterPrincess()
        assertEquals(GameStatus.READY, e.state.status)
        e.exitPrincess()
        assertEquals(GameStatus.READY, e.state.status)
        assertFalse(e.state.canUndo)
    }

    @Test
    fun readySwappedExitsToPlaying() {
        val e = engine(twoTiles, status = GameStatus.READY)
        e.enterPrincess()
        assertTrue(e.swap(0, 0, 3, 3))
        e.exitPrincess()
        assertEquals(GameStatus.PLAYING, e.state.status)
    }

    @Test
    fun readySwappedThenUndoneExitsToReady() {
        val e = engine(twoTiles, status = GameStatus.READY)
        e.enterPrincess()
        assertTrue(e.swap(0, 0, 3, 3))
        assertTrue(e.undo())
        e.exitPrincess()
        assertEquals(GameStatus.READY, e.state.status)
        assertEquals(twoTiles, e.state.board)
    }

    @Test
    fun afterExitSingleLevelUndo() {
        val board = b(listOf(2, 4, 8, 0), listOf(0, 0, 0, 0), listOf(0, 0, 0, 0), listOf(0, 0, 0, 0))
        val e = engine(board)
        e.enterPrincess()
        assertTrue(e.swap(0, 0, 0, 1))
        val first = e.state.board
        assertTrue(e.swap(0, 1, 0, 2))
        e.exitPrincess()
        assertTrue(e.state.canUndo)
        assertEquals(first, e.undoSnapshot?.board)
        assertTrue(e.undo())
        assertEquals(first, e.state.board)
        assertEquals(GameStatus.PLAYING, e.state.status)
        assertFalse(e.state.canUndo)
        assertNull(e.undoSnapshot)
        assertFalse(e.undo())
        // Re-entering exposes the remaining history again.
        e.enterPrincess()
        assertTrue(e.state.canUndo)
        assertTrue(e.undo())
        assertEquals(board, e.state.board)
    }

    @Test
    fun exitWithEmptyHistoryHasNoUndo() {
        val e = engine(twoTiles)
        e.enterPrincess()
        e.exitPrincess()
        assertFalse(e.state.canUndo)
        assertFalse(e.undo())
    }

    @Test
    fun exitWhenNotInPrincessIsNoOp() {
        val e = engine(dead, status = GameStatus.PLAYING)
        val before = e.state
        e.exitPrincess()
        assertEquals(before, e.state)
    }

    @Test
    fun normalModeUndoStaysSingleLevel() {
        val e = GameEngine(Random(7))
        repeat(3) { assertTrue(e.move(firstMovingDir(e))) }
        assertTrue(e.undo())
        assertFalse(e.state.canUndo)
        assertFalse(e.undo())
        assertTrue(e.move(firstMovingDir(e)))
        assertTrue(e.state.canUndo)
    }

    @Test
    fun newGameExitsPrincessAndClearsHistory() {
        val e = GameEngine(Random(11))
        repeat(3) { assertTrue(e.move(firstMovingDir(e))) }
        e.enterPrincess()
        e.newGame()
        assertFalse(e.state.princess)
        assertEquals(GameStatus.READY, e.state.status)
        assertFalse(e.state.canUndo)
        e.enterPrincess()
        assertFalse(e.state.canUndo)
        assertFalse(e.undo())
    }

    @Test
    fun restoreSeedsSingleUndo() {
        val snap = UndoSnapshot(nearlyDead, 4)
        val e = engine(dead, score = 8, best = 9, undo = snap, status = GameStatus.OVER)
        assertFalse(e.state.princess)
        e.enterPrincess()
        assertTrue(e.state.canUndo)
        assertTrue(e.undo())
        assertEquals(nearlyDead, e.state.board)
        assertEquals(4, e.state.score)
        assertFalse(e.state.canUndo)
        e.exitPrincess()
        assertEquals(GameStatus.PLAYING, e.state.status)
    }
}
