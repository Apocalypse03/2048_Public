package com.apocalypse03.game2048.game

import org.junit.Assert.assertEquals
import org.junit.Test

class GameConfigTest {
    @Test
    fun sizeIsFour() {
        assertEquals(4, GameConfig.SIZE)
    }
}
