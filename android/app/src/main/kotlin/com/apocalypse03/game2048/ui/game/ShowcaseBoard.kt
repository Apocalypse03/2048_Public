package com.apocalypse03.game2048.ui.game

import com.apocalypse03.game2048.game.Board

/** Preview/screenshot board covering 2..2048 and 4096. */
val ShowcaseBoard: Board = Board.of(
    listOf(
        listOf(2, 4, 8, 16),
        listOf(32, 64, 128, 256),
        listOf(512, 1024, 2048, 4096),
        listOf(0, 0, 2, 0),
    ),
)

/** 6x6 (2048+ mode) preview/screenshot board covering 2..16384, incl. 4- and 5-digit tiles. */
val ShowcaseBoard6: Board = Board.of(
    listOf(
        listOf(2, 4, 8, 16, 32, 64),
        listOf(128, 256, 512, 1024, 2048, 4096),
        listOf(8192, 16384, 2, 4, 8, 16),
        listOf(32, 64, 128, 256, 512, 1024),
        listOf(2048, 4096, 8192, 0, 2, 0),
        listOf(0, 0, 4, 0, 0, 2),
    ),
)
