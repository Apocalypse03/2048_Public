package com.apocalypse03.game2048.ui.timer

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatElapsedTest {
    @Test
    fun formats() {
        val cases = listOf(
            0L to "0S",
            999L to "0S",
            1_000L to "1S",
            59_999L to "59S",
            60_000L to "1M 00S",
            65_000L to "1M 05S",
            3_599_000L to "59M 59S",
            3_600_000L to "1H 00M 00S",
            3_725_000L to "1H 02M 05S",
        )
        for ((ms, text) in cases) assertEquals("$ms ms", text, formatElapsed(ms))
    }
}
