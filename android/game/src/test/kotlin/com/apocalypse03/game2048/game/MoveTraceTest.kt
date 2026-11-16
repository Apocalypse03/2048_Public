package com.apocalypse03.game2048.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Per-tile move trace (DESIGN_SPEC §17): hand cases, a property test against Rules.move, engine lastMove. */
class MoveTraceTest {

    /** Board of side [size] whose line 0 (toward the wall of [dir]) reads [line], from the wall outward. */
    private fun lineBoard(size: Int, dir: Direction, line: List<Int>): Board {
        val cells = IntArray(size * size)
        for (k in line.indices) {
            val (r, c) = cellAt(size, dir, k)
            cells[r * size + c] = line[k]
        }
        return Board.of(cells)
    }

    /** Cell at position [k] from the wall of [dir] on line 0. */
    private fun cellAt(size: Int, dir: Direction, k: Int): Pair<Int, Int> = when (dir) {
        Direction.LEFT -> 0 to k
        Direction.RIGHT -> 0 to size - 1 - k
        Direction.UP -> k to 0
        Direction.DOWN -> size - 1 - k to 0
    }

    /** Applies [motions] to an empty board: each value lands on its target, merged targets doubled. */
    private fun apply(size: Int, motions: List<TileMotion>): Pair<Board, Int> {
        val cells = IntArray(size * size)
        var gain = 0
        for (m in motions) {
            val i = m.toRow * size + m.toCol
            if (m.merged) {
                if (cells[i] == 0) {
                    cells[i] = m.value * 2
                    gain += m.value * 2
                }
            } else {
                assertEquals("non-merged motion onto occupied cell", 0, cells[i])
                cells[i] = m.value
            }
        }
        return Board.of(cells) to gain
    }

    /**
     * Expected motions for a single line: (fromK, toK, value, merged) per tile, positions counted
     * from the wall of the direction.
     */
    private fun checkLine(line: List<Int>, expected: List<List<Any>>) {
        for (size in GameConfig.SIZES) for (dir in Direction.entries) {
            val padded = line + List(size - line.size) { 0 }
            val board = lineBoard(size, dir, padded)
            val want = expected.map { (fk, tk, v, m) ->
                val (fr, fc) = cellAt(size, dir, fk as Int)
                val (tr, tc) = cellAt(size, dir, tk as Int)
                TileMotion(fr, fc, tr, tc, v as Int, m as Boolean)
            }
            assertEquals("size $size $dir $line", want.toSet(), traceMove(board, dir).toSet())
            assertEquals(Rules.move(board, dir).board, apply(size, want).first)
        }
    }

    @Test
    fun fourEqualTilesMergeInTwoPairs() = checkLine(
        listOf(2, 2, 2, 2),
        listOf(listOf(0, 0, 2, true), listOf(1, 0, 2, true), listOf(2, 1, 2, true), listOf(3, 1, 2, true)),
    )

    @Test
    fun mergeBehindNonMergingLeader() = checkLine(
        listOf(4, 2, 2, 0),
        listOf(listOf(0, 0, 4, false), listOf(1, 1, 2, true), listOf(2, 1, 2, true)),
    )

    @Test
    fun twoDifferentPairs() = checkLine(
        listOf(2, 2, 4, 4),
        listOf(listOf(0, 0, 2, true), listOf(1, 0, 2, true), listOf(2, 1, 4, true), listOf(3, 1, 4, true)),
    )

    @Test
    fun threeEqualMergeLeadingPairOnly() = checkLine(
        listOf(8, 8, 8, 0),
        listOf(listOf(0, 0, 8, true), listOf(1, 0, 8, true), listOf(2, 1, 8, false)),
    )

    @Test
    fun gapsSlideAndMergeAcrossEmptyCells() = checkLine(
        listOf(0, 2, 0, 2),
        listOf(listOf(1, 0, 2, true), listOf(3, 0, 2, true)),
    )

    @Test
    fun stationaryTilesHaveFromEqualTo() = checkLine(
        listOf(2, 4, 8, 16),
        listOf(listOf(0, 0, 2, false), listOf(1, 1, 4, false), listOf(2, 2, 8, false), listOf(3, 3, 16, false)),
    )

    @Test
    fun emptyBoardHasNoMotions() {
        for (size in GameConfig.SIZES) for (dir in Direction.entries) {
            assertTrue(traceMove(Board.empty(size), dir).isEmpty())
        }
    }

    @Test
    fun mergedCellsAreTargetsOfMergedPairs() {
        val board = Board.of(listOf(listOf(2, 2, 4, 0), listOf(8, 0, 8, 0), listOf(2, 4, 8, 16), List(4) { 0 }))
        val trace = MoveTrace(Direction.LEFT, traceMove(board, Direction.LEFT), null)
        assertEquals(setOf(0 to 0, 1 to 0), trace.mergedCells)
    }

    @Test
    fun randomBoardsMatchRulesMove() {
        val values = intArrayOf(2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048)
        for ((size, seed) in listOf(4 to 17L, 6 to 29L)) {
            val rng = Random(seed)
            repeat(BOARDS_PER_SIZE) { n ->
                // Fill density varies from sparse to full; the low value pool makes merges common.
                val fill = if (n % 3 == 0) 0.9 + rng.nextDouble() * 0.1 else rng.nextDouble()
                val pool = if (n % 2 == 0) 3 else values.size
                val cells = IntArray(size * size) { if (rng.nextDouble() < fill) values[rng.nextInt(pool)] else 0 }
                val board = Board.of(cells)
                for (dir in Direction.entries) {
                    val motions = traceMove(board, dir)
                    val expected = Rules.move(board, dir)
                    val (applied, gain) = apply(size, motions)
                    assertEquals("$board $dir", expected.board, applied)
                    assertEquals("$board $dir gain", expected.gain, gain)
                    val from = motions.map { it.fromRow * size + it.fromCol }
                    assertEquals(from.size, from.toSet().size)
                    assertEquals(cells.indices.filter { cells[it] != 0 }.toSet(), from.toSet())
                    for (m in motions) assertEquals(cells[m.fromRow * size + m.fromCol], m.value)
                    assertEquals(expected.moved, motions.any { it.fromRow != it.toRow || it.fromCol != it.toCol || it.merged })
                }
            }
        }
    }

    // --- GameEngine.lastMove ---

    private val z = listOf(0, 0, 0, 0)
    private val start = Board.of(listOf(listOf(2, 2, 0, 0), z, z, listOf(0, 0, 0, 4)))

    private fun engine(board: Board = start, seed: Int = 1) =
        GameEngine.restore(Random(seed), board, 0, 0, null, GameStatus.PLAYING)

    private fun assertSpawnMatches(e: GameEngine, before: Board, dir: Direction) {
        val trace = e.lastMove!!
        assertEquals(dir, trace.direction)
        assertEquals(traceMove(before, dir), trace.motions)
        val moved = Rules.move(before, dir).board
        val after = e.state.board
        assertNotNull(trace.spawn)
        val spawn = trace.spawn!!
        assertEquals(0, moved[spawn.row, spawn.col])
        assertEquals(after[spawn.row, spawn.col], spawn.value)
        val diff = (0 until after.size * after.size).filter { moved.toIntArray()[it] != after.toIntArray()[it] }
        assertEquals(listOf(spawn.row * after.size + spawn.col), diff)
    }

    @Test
    fun freshAndRestoredEnginesHaveNoLastMove() {
        assertNull(GameEngine(Random(3)).lastMove)
        assertNull(GameEngine(Random(3), size = 6).lastMove)
        assertNull(engine().lastMove)
    }

    @Test
    fun movingMoveSetsTraceAndSpawn() {
        val e = engine()
        assertTrue(e.move(Direction.LEFT))
        assertSpawnMatches(e, start, Direction.LEFT)
        assertEquals(setOf(0 to 0), e.lastMove!!.mergedCells)
        val before = e.state.board
        val dir = Direction.entries.first { Rules.move(before, it).moved }
        assertTrue(e.move(dir))
        assertSpawnMatches(e, before, dir)
    }

    @Test
    fun sixBySixMoveSetsTrace() {
        val e = GameEngine(Random(11), size = 6)
        val before = e.state.board
        val dir = Direction.entries.first { Rules.move(before, it).moved }
        assertTrue(e.move(dir))
        assertSpawnMatches(e, before, dir)
    }

    @Test
    fun noOpMoveLeavesTraceUnchanged() {
        // Find a seed whose spawn leaves some direction without effect.
        val e = (1..100).map { engine(seed = it).apply { move(Direction.LEFT) } }
            .first { eng -> Direction.entries.any { !Rules.move(eng.state.board, it).moved } }
        val trace = e.lastMove
        assertNotNull(trace)
        val noOp = Direction.entries.first { !Rules.move(e.state.board, it).moved }
        assertFalse(e.move(noOp))
        assertSame(trace, e.lastMove)
    }

    @Test
    fun undoNewGameAndPrincessClearTrace() {
        val e = engine()
        e.move(Direction.LEFT)
        assertTrue(e.undo())
        assertNull(e.lastMove)

        e.move(Direction.LEFT)
        e.newGame()
        assertNull(e.lastMove)

        e.move(Direction.entries.first { Rules.move(e.state.board, it).moved })
        e.enterPrincess()
        assertNull(e.lastMove)
        e.exitPrincess()
        assertNull(e.lastMove)
    }

    @Test
    fun swapClearsTrace() {
        val e = engine()
        e.move(Direction.LEFT)
        e.enterPrincess()
        val tiles = e.state.board.toIntArray().withIndex().filter { it.value != 0 }
        val a = tiles.first()
        val b = tiles.first { it.value != a.value }
        assertTrue(e.swap(a.index / 4, a.index % 4, b.index / 4, b.index % 4))
        assertNull(e.lastMove)
    }

    @Test
    fun traceDoesNotChangeRngSequence() {
        // Same seed and same moves must give the same boards as a plain Rules replay.
        val rng = Random(42)
        var board = Rules.newGameBoard(rng, 6)
        val e = GameEngine(Random(42), size = 6)
        assertEquals(board, e.state.board)
        repeat(50) {
            val dir = Direction.entries.firstOrNull { Rules.move(board, it).moved } ?: return
            board = Rules.spawn(Rules.move(board, dir).board, rng)
            assertTrue(e.move(dir))
            assertEquals(board, e.state.board)
        }
    }

    private companion object {
        const val BOARDS_PER_SIZE = 5_000
    }
}
