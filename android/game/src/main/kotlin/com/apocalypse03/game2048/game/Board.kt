package com.apocalypse03.game2048.game

/**
 * Immutable [size] x [size] board of tile values (0 = empty). Row 0 is the top row.
 * [size] is one of [GameConfig.SIZES]. Has value equality (boards of different sizes are never
 * equal). Replaces the `tiles` dict of 2048_vertical.py.
 */
class Board private constructor(private val cells: IntArray) {

    /** Side length, derived from the cell count. */
    val size: Int = SIZE_BY_CELLS.getValue(cells.size)

    /** Tile value at ([row], [col]); 0 if empty. */
    operator fun get(row: Int, col: Int): Int {
        require(row in 0 until size && col in 0 until size) { "Cell out of range: ($row, $col)" }
        return cells[row * size + col]
    }

    /** Coordinates of all empty cells as (row, col), in row-major order. */
    fun emptyCells(): List<Pair<Int, Int>> =
        cells.indices.filter { cells[it] == 0 }.map { it / size to it % size }

    /** Row-major copy of the board. */
    fun toRows(): List<List<Int>> = List(size) { r -> List(size) { c -> cells[r * size + c] } }

    /** Row-major flat copy of the board. */
    fun toIntArray(): IntArray = cells.copyOf()

    override fun equals(other: Any?): Boolean = other is Board && cells.contentEquals(other.cells)

    override fun hashCode(): Int = cells.contentHashCode()

    override fun toString(): String = toRows().joinToString(prefix = "Board", separator = "/")

    companion object {
        private val SIZE_BY_CELLS: Map<Int, Int> = GameConfig.SIZES.associateBy { it * it }

        /** Board of side [size] (one of [GameConfig.SIZES]) with no tiles. */
        fun empty(size: Int = GameConfig.SIZE): Board {
            require(size in GameConfig.SIZES) { "Unsupported board size: $size" }
            return Board(IntArray(size * size))
        }

        /** Builds a board from a square list of rows; its side must be in [GameConfig.SIZES]. Values must be >= 0. */
        fun of(rows: List<List<Int>>): Board {
            val n = rows.size
            require(n in GameConfig.SIZES && rows.all { it.size == n }) {
                "Board must be NxN with N in ${GameConfig.SIZES}"
            }
            return of(rows.flatten().toIntArray())
        }

        /**
         * Builds a board from a row-major array of N*N values, N in [GameConfig.SIZES]; the size is
         * inferred from the length. The array is copied.
         */
        fun of(values: IntArray): Board {
            require(values.size in SIZE_BY_CELLS) { "Board needs N*N values with N in ${GameConfig.SIZES}" }
            require(values.all { it >= 0 }) { "Tile values must be >= 0" }
            return Board(values.copyOf())
        }
    }
}
