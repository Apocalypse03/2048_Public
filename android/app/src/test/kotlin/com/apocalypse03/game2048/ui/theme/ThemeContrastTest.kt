package com.apocalypse03.game2048.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Light-board contrast, onAccent and the dark palette variants, over both variants of every spec (DESIGN_SPEC section 3). */
class ThemeContrastTest {
    /** All 34 variants: light and dark of each of the 17 specs. */
    private val variants: List<AppTheme> = AppThemes.all.flatMap { listOf(it.light, it.dark) }

    private fun ratio(a: Color, b: Color) = AppTheme.contrastRatio(a, b)

    private fun hue(c: Color): Float {
        val mx = max(c.red, max(c.green, c.blue))
        val mn = min(c.red, min(c.green, c.blue))
        val d = mx - mn
        if (d == 0f) return 0f
        val h = when (mx) {
            c.red -> ((c.green - c.blue) / d) % 6f
            c.green -> (c.blue - c.red) / d + 2f
            else -> (c.red - c.green) / d + 4f
        }
        return (h * 60f + 360f) % 360f
    }

    private fun saturation(c: Color): Float {
        val mx = max(c.red, max(c.green, c.blue))
        val mn = min(c.red, min(c.green, c.blue))
        return if (mx == 0f) 0f else (mx - mn) / mx
    }

    @Test
    fun lightThemesKeepEmptyCellsApartFromTilesAndBoard() {
        var minCellTile = Float.MAX_VALUE
        var minBoardCell = Float.MAX_VALUE
        val failures = ArrayList<String>()
        for (t in variants.filter { it.isLight }) {
            val tile = t.tileColor(2)
            val cellTile = ratio(t.emptyCell, tile)
            val boardCell = ratio(t.boardBg, t.emptyCell)
            val hueOk = saturation(t.emptyCell) >= 0.1f && saturation(tile) >= 0.1f &&
                hueDiff(t.emptyCell, tile) >= 25f
            println(
                "LIGHT ${t.name}: empty/tile2 %.3f board/empty %.3f hueDiff %.1f (sat %.2f/%.2f)"
                    .format(cellTile, boardCell, hueDiff(t.emptyCell, tile), saturation(t.emptyCell), saturation(tile)),
            )
            minCellTile = min(minCellTile, cellTile)
            minBoardCell = min(minBoardCell, boardCell)
            if (cellTile < 1.08f && !hueOk) failures += "${t.name} emptyCell vs tile 2 = $cellTile"
            if (boardCell < 1.15f) failures += "${t.name} boardBg vs emptyCell = $boardCell"
        }
        println("LIGHT MIN empty/tile2 %.3f board/empty %.3f".format(minCellTile, minBoardCell))
        assertTrue(failures.joinToString("; "), failures.isEmpty())
    }

    private fun hueDiff(a: Color, b: Color): Float = abs(hue(a) - hue(b)).let { min(it, 360f - it) }

    @Test
    fun boardColourRules() {
        for (t in variants) {
            val (b, e) = if (t.isLight) 0.50f to 0f else 0.16f to 0.26f
            assertEquals(t.name, mix(t.background, t.accent, b).toArgb(), t.boardBg.toArgb())
            assertEquals(t.name, mix(t.background, t.accent, e).toArgb(), t.emptyCell.toArgb())
        }
    }

    @Test
    fun onAccentContrast() {
        val dark = Color(0xFF1D1D1D)
        var min = Float.MAX_VALUE
        for (t in variants) {
            val r = ratio(t.accent, t.onAccent)
            println("ONACCENT ${t.name}: %.3f".format(r))
            min = min(min, r)
            if (ratio(t.accent, t.background) >= 3f) {
                assertEquals(t.name, t.background.toArgb(), t.onAccent.toArgb())
            } else {
                assertTrue(t.name, t.onAccent.toArgb() in listOf(dark.toArgb(), Color.White.toArgb()))
            }
            val best = max(ratio(t.accent, dark), ratio(t.accent, Color.White))
            assertTrue("${t.name} onAccent $r (best candidate $best)", r >= 3f || abs(r - best) < 1e-4f)
        }
        println("ONACCENT MIN %.3f".format(min))
    }

    /** Dark variants of the palette specs (ids 7..16). */
    private val darkThemes get() = AppThemes.all.filter { it.id in 7..16 }.map { it.dark }

    @Test
    fun darkVariantsAreDarkSameHue() {
        assertEquals(10, darkThemes.size)
        for (d in darkThemes) {
            val light = AppThemes.byId(d.id).light
            assertEquals(light.name, d.name)
            assertFalse(d.isLight)
            assertTrue("${d.name} luminance ${d.background.luminance()}", d.background.luminance() < 0.1f)
            assertEquals(Color(0xFFFBFBFB).toArgb(), d.onBackground.toArgb())
            val darkest = d.accent
            println("DARK ${d.name} id=${d.id} bg=#%06X accent=#%06X".format(d.background.toArgb() and 0xFFFFFF, darkest.toArgb() and 0xFFFFFF))
            if (saturation(darkest) > 0.1f && saturation(d.background) > 0.05f) {
                val diff = abs(hue(d.background) - hue(darkest)).let { min(it, 360f - it) }
                assertTrue("${d.name} hue diff $diff", diff <= 20f)
            }
        }
    }

    @Test
    fun darkRampsGetLighter() {
        for (id in listOf(7, 13, 14, 15, 16)) { // dark variants of palettes 1, 7, 8, 9, 10
            val t = AppThemes.byId(id).dark
            val lum = t.ramp.map { it.luminance() }
            lum.zipWithNext().forEachIndexed { i, (a, b) ->
                assertTrue("${t.name} slot $i -> ${i + 1}: $a > $b", b >= a - 1e-6f)
            }
        }
    }
}
