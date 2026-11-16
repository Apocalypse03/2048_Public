package com.apocalypse03.game2048.game

import kotlin.random.Random

/** Result of [Rules.move]: new [board], points [gain]ed (sum of merged values), and whether anything [moved]. */
data class MoveResult(val board: Board, val gain: Int, val moved: Boolean)

/** Stateless 2048 rules. Oracle: 2048_vertical.py. */
object Rules {
    private const val FOUR_PROBABILITY = 0.1

    /**
     * Slides and merges tiles toward [dir]. Each tile merges at most once per move;
     * gain is the sum of merged values. Never spawns. Ported from move_tiles.
     * `moved` is true iff the board changed.
     */
    fun move(board: Board, dir: Direction): MoveResult {
        val size = board.size
        val out = IntArray(size * size)
        var gain = 0
        for (line in 0 until size) {
            // Read line ordered so index 0 is the wall the tiles slide toward.
            val packed = (0 until size).map { board[row(dir, line, it, size), col(dir, line, it, size)] }.filter { it != 0 }
            val merged = ArrayList<Int>(size)
            var i = 0
            while (i < packed.size) {
                if (i + 1 < packed.size && packed[i] == packed[i + 1]) {
                    merged.add(packed[i] * 2)
                    gain += packed[i] * 2
                    i += 2
                } else {
                    merged.add(packed[i])
                    i++
                }
            }
            for (k in 0 until size) {
                out[row(dir, line, k, size) * size + col(dir, line, k, size)] = merged.getOrElse(k) { 0 }
            }
        }
        val newBoard = Board.of(out)
        return MoveResult(newBoard, gain, newBoard != board)
    }

    private fun row(dir: Direction, line: Int, k: Int, size: Int) = when (dir) {
        Direction.LEFT, Direction.RIGHT -> line
        Direction.UP -> k
        Direction.DOWN -> size - 1 - k
    }

    private fun col(dir: Direction, line: Int, k: Int, size: Int) = when (dir) {
        Direction.UP, Direction.DOWN -> line
        Direction.LEFT -> k
        Direction.RIGHT -> size - 1 - k
    }

    /**
     * True if any cell is empty, else true iff two orthogonally adjacent tiles are equal.
     * Ported from has_legal_moves (:145-164).
     */
    fun hasLegalMoves(board: Board): Boolean {
        if (board.emptyCells().isNotEmpty()) return true
        val size = board.size
        for (r in 0 until size) for (c in 0 until size) {
            if (c + 1 < size && board[r, c] == board[r, c + 1]) return true
            if (r + 1 < size && board[r, c] == board[r + 1, c]) return true
        }
        return false
    }

    /**
     * Places a 2 (p=0.9) or 4 (p=0.1) on a uniformly random empty cell.
     * Ported from end_tiles (:325-331). Throws if the board is full.
     */
    fun spawn(board: Board, rng: Random): Board {
        val empty = board.emptyCells()
        require(empty.isNotEmpty()) { "Cannot spawn on a full board" }
        val (r, c) = empty[rng.nextInt(empty.size)]
        val value = if (rng.nextDouble() < FOUR_PROBABILITY) 4 else 2
        val cells = board.toIntArray()
        cells[r * board.size + c] = value
        return Board.of(cells)
    }

    /**
     * Fresh [size] x [size] board: two distinct uniform random cells, both value 2.
     * Ported from generate_tiles (:334-339). [size] must be in [GameConfig.SIZES].
     */
    fun newGameBoard(rng: Random, size: Int = GameConfig.SIZE): Board {
        require(size in GameConfig.SIZES) { "Unsupported board size: $size" }
        val first = rng.nextInt(size * size)
        var second = rng.nextInt(size * size - 1)
        if (second >= first) second++
        val cells = IntArray(size * size)
        cells[first] = 2
        cells[second] = 2
        return Board.of(cells)
    }
}
