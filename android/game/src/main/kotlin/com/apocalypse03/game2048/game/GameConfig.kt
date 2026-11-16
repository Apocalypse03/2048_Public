package com.apocalypse03.game2048.game

/**
 * Board configuration.
 *
 * Ported from 2048_vertical.py ROWS/COLUMNS (:12-13). [SIZE] is the default (Normal) size;
 * [SIZES] lists every supported N for an N x N board.
 */
object GameConfig {
    const val SIZE = 4

    /** Supported board sizes (side length). */
    val SIZES: Set<Int> = setOf(4, 6)
}
