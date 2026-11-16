package com.apocalypse03.game2048.ui

/**
 * Game mode chosen on Home (DESIGN_SPEC section 6): the board side [size] and the selector [label].
 * Declaration order is the selector order. Each mode keeps its own Best.
 */
enum class GameMode(val size: Int, val label: String) {
    NORMAL(4, "Normal"),
    PLUS(6, "2048⁺"),
    ;

    companion object {
        /** The mode whose board side is [size]; [size] must be one of the mode sizes. */
        fun forSize(size: Int): GameMode = entries.first { it.size == size }
    }
}
