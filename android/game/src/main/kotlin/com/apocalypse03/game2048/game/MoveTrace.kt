package com.apocalypse03.game2048.game

/**
 * Motion of one tile during a move (DESIGN_SPEC §17). [value] is the tile's value before the move.
 * [merged] is true for both source tiles that combine into one cell; each gets its own motion
 * ending on the same target. A tile that does not move has from == to.
 */
data class TileMotion(
    val fromRow: Int,
    val fromCol: Int,
    val toRow: Int,
    val toCol: Int,
    val value: Int,
    val merged: Boolean,
)

/** Tile spawned after a move: cell ([row], [col]) and its [value] (2 or 4). */
data class Spawn(val row: Int, val col: Int, val value: Int)

/**
 * Everything the UI needs to animate one successful move: the [direction], one [motions] entry per
 * tile that was on the board before the move, and the [spawn]ed tile (null if none).
 */
data class MoveTrace(val direction: Direction, val motions: List<TileMotion>, val spawn: Spawn?) {
    /** Target cells, as (row, col), that hold a merged (doubled) tile after the move. */
    val mergedCells: Set<Pair<Int, Int>>
        get() = motions.filter { it.merged }.mapTo(HashSet()) { it.toRow to it.toCol }
}

/**
 * Where every non-zero tile of [board] goes when moved toward [dir], with the same merge semantics
 * as [Rules.move]: each line is packed toward the wall, then equal neighbours merge pairwise from
 * the wall outward, each tile at most once. Pure; does not consult [Rules.move].
 * Returns one [TileMotion] per non-zero cell, line by line.
 */
fun traceMove(board: Board, dir: Direction): List<TileMotion> {
    val size = board.size
    val motions = ArrayList<TileMotion>()
    for (line in 0 until size) {
        // Positions k along the line, index 0 at the wall the tiles slide toward.
        val packed = (0 until size).filter { board[traceRow(dir, line, it, size), traceCol(dir, line, it, size)] != 0 }
        var i = 0
        var target = 0
        while (i < packed.size) {
            val toR = traceRow(dir, line, target, size)
            val toC = traceCol(dir, line, target, size)
            val k = packed[i]
            val v = board[traceRow(dir, line, k, size), traceCol(dir, line, k, size)]
            val next = packed.getOrNull(i + 1)
            if (next != null && board[traceRow(dir, line, next, size), traceCol(dir, line, next, size)] == v) {
                for (src in listOf(k, next)) {
                    motions.add(TileMotion(traceRow(dir, line, src, size), traceCol(dir, line, src, size), toR, toC, v, true))
                }
                i += 2
            } else {
                motions.add(TileMotion(traceRow(dir, line, k, size), traceCol(dir, line, k, size), toR, toC, v, false))
                i++
            }
            target++
        }
    }
    return motions
}

// Same line/position mapping as the private helpers in Rules (kept separate so Rules is untouched).
private fun traceRow(dir: Direction, line: Int, k: Int, size: Int) = when (dir) {
    Direction.LEFT, Direction.RIGHT -> line
    Direction.UP -> k
    Direction.DOWN -> size - 1 - k
}

private fun traceCol(dir: Direction, line: Int, k: Int, size: Int) = when (dir) {
    Direction.UP, Direction.DOWN -> line
    Direction.LEFT -> k
    Direction.RIGHT -> size - 1 - k
}
