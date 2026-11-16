package com.apocalypse03.game2048.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tile text keeps WCAG large-text contrast (>= 3:1) on every tile of both variants of every theme. */
class TileTextContrastTest {
    private fun contrast(a: Color, b: Color): Float {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05f) / (lo + 0.05f)
    }

    @Test
    fun everyTileHasReadableText() {
        for (theme in AppThemes.all.flatMap { listOf(it.light, it.dark) }) {
            var v = 2
            while (v <= 8192) {
                val ratio = contrast(theme.tileColor(v), theme.tileTextColor(v))
                assertTrue("${theme.name} $v contrast $ratio", ratio >= 3f)
                v *= 2
            }
        }
    }
}
