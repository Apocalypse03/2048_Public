package com.apocalypse03.game2048.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Theme specs, palette themes (ids 7..16), built-in ramps and the display order (DESIGN_SPEC section 3). */
class PaletteThemeTest {
    private fun c(rgb: Int) = Color(0xFF000000.toInt() or rgb)

    /** id to palette colours in printed order (Reference Pic/skin_palette_N). */
    private val palettes: Map<Int, List<Int>> = mapOf(
        7 to listOf(0xF6F3FF, 0xE5DEF5, 0xD4C9EC, 0xC3B5E3, 0xB2A0DA, 0xA18CD1),
        8 to listOf(0x877BA3, 0xD1BAD4, 0xEADEEA, 0x8576A1),
        9 to listOf(0xF0D0FF, 0xFFCCEB, 0xF1B5D9, 0xBA91CD, 0x957FBF),
        10 to listOf(0xDCCDF2, 0xE7DDEA, 0xF2D6E2, 0xF7F3ED),
        11 to listOf(0xD5D5D8, 0xAAAAC4, 0x717388, 0x767BA5),
        12 to listOf(0xD0D0DA, 0x9C9BA7, 0xAAAAC4, 0x717388),
        13 to listOf(0xFFDDE1, 0xFBD0D5, 0xF8C3C9, 0xF4B6BE, 0xF1A9B2, 0xEE9CA7),
        14 to listOf(0xDFF4FF, 0xBBDCFB, 0x98C5F7, 0x75AEF4, 0x5297F0, 0x2F80ED),
        15 to listOf(0x6DD5ED, 0x5DC7E0, 0x4EBAD4, 0x3FADC8, 0x30A0BC, 0x2193B0),
        16 to listOf(0xFBF1F1, 0xEFCACB, 0xE4A3A5, 0xD87C80, 0xCD555A, 0xC22E35),
    )

    /** id to (name, background, accent): lightest and darkest by relative luminance. */
    private val expected: Map<Int, Triple<String, Int, Int>> = mapOf(
        7 to Triple("Lavender", 0xF6F3FF, 0xA18CD1),
        8 to Triple("Lilac", 0xEADEEA, 0x8576A1),
        9 to Triple("Orchid", 0xF0D0FF, 0x957FBF),
        10 to Triple("Cream", 0xF7F3ED, 0xDCCDF2),
        11 to Triple("Periwinkle", 0xD5D5D8, 0x717388),
        12 to Triple("Slate", 0xD0D0DA, 0x717388),
        13 to Triple("Rose", 0xFFDDE1, 0xEE9CA7),
        14 to Triple("Sky", 0xDFF4FF, 0x2F80ED),
        15 to Triple("Cyan", 0x6DD5ED, 0x2193B0),
        16 to Triple("Red", 0xFBF1F1, 0xC22E35),
    )

    @Test
    fun displayOrderIsDefaultThenPalettesThenBuiltIns() {
        assertEquals(listOf(0) + (7..16).toList() + (1..6).toList(), AppThemes.all.map { it.id })
        val names = listOf("Lavender", "Lilac", "Orchid", "Cream", "Periwinkle", "Slate", "Rose", "Sky", "Cyan", "Red")
        val builtIns = listOf("Olive", "Orange", "Blue", "Light blue", "Green", "Pink")
        assertEquals(listOf("Default") + names + builtIns, AppThemes.all.map { it.name })
        assertEquals(17, AppThemes.all.size)
        assertEquals(17, AppThemes.all.map { it.id }.toSet().size)
    }

    @Test
    fun everySpecHasALightAndADarkVariant() {
        for (spec in AppThemes.all) {
            assertTrue("${spec.name} light", spec.light.isLight)
            assertFalse("${spec.name} dark", spec.dark.isLight)
            for (v in listOf(spec.light, spec.dark)) {
                assertEquals(spec.id, v.id)
                assertEquals(spec.name, v.name)
            }
            assertEquals(spec.light, spec.variant(ThemeVariant.LIGHT))
            assertEquals(spec.dark, spec.variant(ThemeVariant.DARK))
            assertEquals(spec.light, AppThemes.resolve(spec.id, ThemeVariant.LIGHT))
            assertEquals(spec.dark, AppThemes.resolve(spec.id, ThemeVariant.DARK))
        }
    }

    @Test
    fun specRejectsSwappedVariants() {
        val spec = AppThemes.byId(7)
        val swapped = runCatching { ThemeSpec(7, spec.name, spec.dark, spec.light) }
        assertTrue(swapped.isFailure)
        val wrongId = runCatching { ThemeSpec(8, spec.name, spec.light, spec.dark) }
        assertTrue(wrongId.isFailure)
    }

    @Test
    fun byIdAndDefaultUnchanged() {
        assertEquals(0, AppThemes.Default.id)
        assertEquals("Default", AppThemes.Default.name)
        assertEquals(AppThemes.Default, AppThemes.all.first())
        for (t in AppThemes.all) assertEquals(t, AppThemes.byId(t.id))
        assertEquals(AppThemes.Default, AppThemes.byId(27))
        assertEquals(AppThemes.Default, AppThemes.byId(-1))
    }

    @Test
    fun oldDarkIdsMigrateToTheirSpec() {
        for (id in 17..26) {
            assertEquals(id - 10, AppThemes.migrateId(id))
            assertEquals(AppThemes.byId(id - 10), AppThemes.byId(id))
        }
        for (id in listOf(-1, 0, 6, 7, 16, 27, 99)) assertEquals(id, AppThemes.migrateId(id))
    }

    @Test
    fun builtInsKeepTheirOriginalVariant() {
        // Original built-in colours (DESIGN_SPEC section 3), each on its own side.
        val dark = mapOf(0 to (0x22191C to 0xE095B6), 1 to (0x191919 to 0x988C62), 2 to (0x333333 to 0xD48037),
            3 to (0x0D161D to 0x459FD1), 5 to (0x111111 to 0x359734))
        val light = mapOf(4 to (0xF9F9F9 to 0x7FABC4), 6 to (0xFFFFFF to 0xDA96A1))
        for ((id, bgAccent) in dark) {
            val t = AppThemes.byId(id).dark
            assertEquals("$id bg", c(bgAccent.first).toArgb(), t.background.toArgb())
            assertEquals("$id accent", c(bgAccent.second).toArgb(), t.accent.toArgb())
        }
        for ((id, bgAccent) in light) {
            val t = AppThemes.byId(id).light
            assertEquals("$id bg", c(bgAccent.first).toArgb(), t.background.toArgb())
            assertEquals("$id accent", c(bgAccent.second).toArgb(), t.accent.toArgb())
        }
    }

    @Test
    fun generatedCounterparts() {
        val expected = mapOf(
            0 to Triple(0xFCF4F8, 0xD46598, 0x1D1D1D),
            1 to Triple(0xF5F4EF, 0x988C62, 0x1D1D1D),
            2 to Triple(0xFBF2EB, 0xCB762C, 0x1D1D1D),
            3 to Triple(0xECF5FA, 0x3192C7, 0x1D1D1D),
            4 to Triple(0x171F23, 0x7FABC4, 0xFBFBFB),
            5 to Triple(0xEBF5EB, 0x359734, 0x1D1D1D),
            6 to Triple(0x271B1D, 0xDA96A1, 0xFBFBFB),
        )
        for ((id, e) in expected) {
            val spec = AppThemes.byId(id)
            val t = if (id == 4 || id == 6) spec.dark else spec.light
            assertEquals("$id bg", c(e.first).toArgb(), t.background.toArgb())
            assertEquals("$id accent", c(e.second).toArgb(), t.accent.toArgb())
            assertEquals("$id on", c(e.third).toArgb(), t.onBackground.toArgb())
            assertEquals("$id ramp", AppTheme.classicRamp(t.background, t.accent, t.isLight), t.ramp)
            // Counterpart targets (STYLE_ARCHITECTURE.md): text >= 4.5, accent >= 3.0 on the background.
            assertTrue("$id text", AppTheme.contrastRatio(t.onBackground, t.background) >= 4.5f)
            assertTrue("$id accent", AppTheme.contrastRatio(t.accent, t.background) >= 3f)
        }
    }

    @Test
    fun paletteBaseColours() {
        for ((id, e) in expected) {
            val t = AppThemes.byId(id).light
            assertEquals(e.first, t.name)
            assertEquals("$id background", c(e.second).toArgb(), t.background.toArgb())
            assertEquals("$id accent", c(e.third).toArgb(), t.accent.toArgb())
            assertEquals("$id onBackground", Color(0xFF1D1D1D).toArgb(), t.onBackground.toArgb())
            assertTrue("$id light", t.isLight)
        }
    }

    @Test
    fun paletteRampHoldsEveryNonBackgroundColour() {
        for ((id, colours) in palettes) {
            val t = AppThemes.byId(id).light
            assertEquals(12, t.ramp.size)
            val ramp = t.ramp.map { it.toArgb() }
            colours.map { c(it) }.filter { it.toArgb() != t.background.toArgb() }.forEach {
                assertTrue("$id ramp lacks ${Integer.toHexString(it.toArgb())}", it.toArgb() in ramp)
            }
            assertFalse("$id ramp holds the background", t.background.toArgb() in ramp)
            // The darkest colour sits at the end of the interpolated part, before the darkened tail.
            assertEquals(t.accent.toArgb(), t.tileColor(2 shl (ramp.indexOf(t.accent.toArgb()))).toArgb())
        }
    }

    @Test
    fun monochromeRampsGetDarker() {
        for (id in listOf(7, 13, 14, 15, 16)) {
            val lum = AppThemes.byId(id).light.ramp.map { it.luminance() }
            lum.zipWithNext().forEachIndexed { i, (a, b) ->
                assertTrue("$id tile $i -> ${i + 1}: $a < $b", b <= a + 1e-6f)
            }
        }
    }

    @Test
    fun paletteRampLayout() {
        // Lavender: 5 anchors, k = 2 -> 9 interpolated slots, then 3 darkened by 0.12 each.
        val lav = AppThemes.byId(7).light
        assertEquals(c(0xE5DEF5).toArgb(), lav.ramp[0].toArgb())
        assertEquals(mix(c(0xE5DEF5), c(0xD4C9EC), 0.5f).toArgb(), lav.ramp[1].toArgb())
        assertEquals(c(0xA18CD1).toArgb(), lav.ramp[8].toArgb())
        for (r in 1..3) {
            assertEquals(mix(c(0xA18CD1), Color.Black, r * 0.12f).toArgb(), lav.ramp[8 + r].toArgb())
        }
        // Orchid: 4 anchors, k = 2 -> 7 slots, 5 darkened by 0.12 each (0.6 / 5 = 0.12).
        val orchid = AppThemes.byId(9).light
        assertEquals(c(0x957FBF).toArgb(), orchid.ramp[6].toArgb())
        assertEquals(mix(c(0x957FBF), Color.Black, 0.60f).toArgb(), orchid.ramp[11].toArgb())
    }

    @Test
    fun fromPaletteDarkBackground() {
        // Lightest = background (#808080, luminance 0.22 < 0.5: dark theme), darkest = accent.
        val t = AppThemes.fromPalette(99, "Night", listOf(c(0x101010), c(0x808080)))
        assertFalse(t.isLight)
        assertEquals(c(0x808080).toArgb(), t.background.toArgb())
        assertEquals(c(0x101010).toArgb(), t.accent.toArgb())
        assertEquals(Color(0xFFFBFBFB).toArgb(), t.onBackground.toArgb())
        assertEquals(12, t.ramp.size)
        assertEquals(c(0x101010).toArgb(), t.ramp[0].toArgb())
        // 1 anchor: 11 slots moving toward white by min(0.12, 0.6 / 11) each.
        assertEquals(mix(c(0x101010), Color.White, 11 * (0.6f / 11)).toArgb(), t.ramp[11].toArgb())
    }

    /** The tile ramp formula before palette themes, with the id-based light rule (ids 4 and 6), for the original variants. */
    private fun oldTileColor(t: AppTheme, value: Int): Color {
        val i = AppTheme.tileIndex(value)
        val light = t.id == 4 || t.id == 6
        return when {
            i <= 6 -> mix(t.background, t.accent, 0.30f + i * (0.70f / 6f))
            light -> mix(t.accent, Color.Black, (i - 6) * 0.12f)
            else -> mix(t.accent, Color.White, (i - 6) * 0.16f)
        }
    }

    @Test
    fun builtInRampsUnchanged() {
        for (id in 0..6) {
            val spec = AppThemes.byId(id)
            val t = if (id == 4 || id == 6) spec.light else spec.dark
            assertEquals("$id isLight", id == 4 || id == 6, t.isLight)
            var v = 2
            while (v <= 8192) {
                assertEquals("$id $v", oldTileColor(t, v), t.tileColor(v))
                v *= 2
            }
        }
    }
}
