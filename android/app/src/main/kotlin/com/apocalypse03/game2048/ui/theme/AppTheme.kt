package com.apocalypse03.game2048.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * One variant (light or dark) of a [ThemeSpec] (DESIGN_SPEC section 3). [ramp] holds the 12 tile
 * colours (index = [tileIndex]); built-in themes default to [classicRamp], palette themes are made
 * by [AppThemes.fromPalette] and [AppThemes.fromPaletteDark].
 */
@Immutable
data class AppTheme(
    val id: Int,
    val name: String,
    val background: Color,
    val accent: Color,
    val onBackground: Color,
    val ramp: List<Color> = classicRamp(background, accent, isLightBackground(background)),
) {
    init {
        require(ramp.size == RAMP_SIZE) { "ramp must have $RAMP_SIZE colours, got ${ramp.size}" }
    }

    val isLight: Boolean get() = isLightBackground(background)

    val outline: Color get() = onBackground.copy(alpha = 0.20f)
    val boardBg: Color get() = mix(background, accent, if (isLight) 0.50f else 0.16f)
    val emptyCell: Color get() = mix(background, accent, if (isLight) 0f else 0.26f)

    /**
     * Content drawn on an accent fill: [background] when it contrasts >= 3:1 with [accent],
     * else #1D1D1D or white, whichever contrasts more.
     */
    val onAccent: Color
        get() {
            if (contrastRatio(accent, background) >= 3f) return background
            return if (contrastRatio(accent, DarkText) >= contrastRatio(accent, Color.White)) DarkText else Color.White
        }

    /** Tile ramp: index i = log2(value) - 1, clamped to 0..11 (11 = "2048+"). */
    fun tileColor(value: Int): Color = ramp[tileIndex(value)]

    /** Dark text (background on dark themes, #1D1D1D on light ones) or white, whichever contrasts more with the tile. */
    fun tileTextColor(value: Int): Color {
        val tile = tileColor(value).luminance()
        val dark = if (isLight) DarkText else background
        return if (contrast(tile, dark.luminance()) >= contrast(tile, 1f)) dark else Color.White
    }

    private fun contrast(a: Float, b: Float): Float = (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)

    companion object {
        private val DarkText = Color(0xFF1D1D1D)

        /** WCAG contrast ratio of two colours (1..21). */
        fun contrastRatio(a: Color, b: Color): Float {
            val la = a.luminance()
            val lb = b.luminance()
            return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
        }

        /** Number of tile colours: values 2..2048 plus "2048+". */
        const val RAMP_SIZE = 12

        /** Light theme: background relative luminance above 0.5. */
        fun isLightBackground(background: Color): Boolean = background.luminance() > 0.5f

        /**
         * Ramp of the 7 built-in themes: i 0..6 = mix(background, accent, 0.30 + i * 0.70 / 6),
         * then toward black (light) by 0.12 per step or white (dark) by 0.16 per step.
         */
        fun classicRamp(background: Color, accent: Color, isLight: Boolean): List<Color> =
            List(RAMP_SIZE) { i ->
                when {
                    i <= 6 -> mix(background, accent, 0.30f + i * (0.70f / 6f))
                    isLight -> mix(accent, Color.Black, (i - 6) * 0.12f)
                    else -> mix(accent, Color.White, (i - 6) * 0.16f)
                }
            }

        fun tileIndex(value: Int): Int {
            val log2 = 31 - Integer.numberOfLeadingZeros(value.coerceAtLeast(2))
            return (log2 - 1).coerceIn(0, 11)
        }
    }
}

/** Per-component sRGB mix: t = 0 gives [a], t = 1 gives [b]. */
fun mix(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = 1f,
)

/** Which variant of a [ThemeSpec] is shown; decided only by [getActiveVariant]. */
enum class ThemeVariant { LIGHT, DARK }

/**
 * One selectable theme (STYLE_ARCHITECTURE.md): a persisted [id] and [name] with a [light] and a
 * [dark] variant. Both variants carry the spec's id and name.
 */
@Immutable
data class ThemeSpec(val id: Int, val name: String, val light: AppTheme, val dark: AppTheme) {
    init {
        require(light.isLight && !dark.isLight) { "$name: light variant must be light, dark variant dark" }
        require(light.id == id && dark.id == id) { "$name: variant ids must be $id" }
        require(light.name == name && dark.name == name) { "$name: variant names must be $name" }
    }

    fun variant(v: ThemeVariant): AppTheme = when (v) {
        ThemeVariant.LIGHT -> light
        ThemeVariant.DARK -> dark
    }
}

/**
 * The active variant: [ThemeVariant.DARK] while the system dark mode is on. The only place that
 * decides the variant; recomposes when the system setting changes.
 */
@Composable
fun getActiveVariant(): ThemeVariant = if (isSystemInDarkTheme()) ThemeVariant.DARK else ThemeVariant.LIGHT

object AppThemes {
    private val light = Color(0xFFFBFBFB)
    private val dark = Color(0xFF1D1D1D)

    /** Built-in themes, ids 0..6, as originally defined (5 dark, 2 light). */
    private val builtIn: List<AppTheme> = listOf(
        AppTheme(0, "Default", Color(0xFF22191C), Color(0xFFE095B6), light),
        AppTheme(1, "Olive", Color(0xFF191919), Color(0xFF988C62), light),
        AppTheme(2, "Orange", Color(0xFF333333), Color(0xFFD48037), light),
        AppTheme(3, "Blue", Color(0xFF0D161D), Color(0xFF459FD1), light),
        AppTheme(4, "Light blue", Color(0xFFF9F9F9), Color(0xFF7FABC4), dark),
        AppTheme(5, "Green", Color(0xFF111111), Color(0xFF359734), light),
        AppTheme(6, "Pink", Color(0xFFFFFFFF), Color(0xFFDA96A1), dark),
    )

    /**
     * Generated counterparts of the built-ins, one per id (light for 0, 1, 2, 3, 5; dark for 4, 6).
     * Values from STYLE_ARCHITECTURE.md section "Counterpart generation".
     */
    private val builtInCounterparts: List<AppTheme> = listOf(
        AppTheme(0, "Default", Color(0xFFFCF4F8), Color(0xFFD46598), dark),
        AppTheme(1, "Olive", Color(0xFFF5F4EF), Color(0xFF988C62), dark),
        AppTheme(2, "Orange", Color(0xFFFBF2EB), Color(0xFFCB762C), dark),
        AppTheme(3, "Blue", Color(0xFFECF5FA), Color(0xFF3192C7), dark),
        AppTheme(4, "Light blue", Color(0xFF171F23), Color(0xFF7FABC4), light),
        AppTheme(5, "Green", Color(0xFFEBF5EB), Color(0xFF359734), dark),
        AppTheme(6, "Pink", Color(0xFF271B1D), Color(0xFFDA96A1), light),
    )

    /** Palette themes, ids 7..16, from Reference Pic/skin_palette_1..10 (colours in printed order). */
    private val paletteColours: List<List<Color>> = listOf(
        rgbList(0xF6F3FF, 0xE5DEF5, 0xD4C9EC, 0xC3B5E3, 0xB2A0DA, 0xA18CD1),
        rgbList(0x877BA3, 0xD1BAD4, 0xEADEEA, 0x8576A1),
        rgbList(0xF0D0FF, 0xFFCCEB, 0xF1B5D9, 0xBA91CD, 0x957FBF),
        rgbList(0xDCCDF2, 0xE7DDEA, 0xF2D6E2, 0xF7F3ED),
        rgbList(0xD5D5D8, 0xAAAAC4, 0x717388, 0x767BA5),
        rgbList(0xD0D0DA, 0x9C9BA7, 0xAAAAC4, 0x717388),
        rgbList(0xFFDDE1, 0xFBD0D5, 0xF8C3C9, 0xF4B6BE, 0xF1A9B2, 0xEE9CA7),
        rgbList(0xDFF4FF, 0xBBDCFB, 0x98C5F7, 0x75AEF4, 0x5297F0, 0x2F80ED),
        rgbList(0x6DD5ED, 0x5DC7E0, 0x4EBAD4, 0x3FADC8, 0x30A0BC, 0x2193B0),
        rgbList(0xFBF1F1, 0xEFCACB, 0xE4A3A5, 0xD87C80, 0xCD555A, 0xC22E35),
    )

    private val paletteNames = listOf("Lavender", "Lilac", "Orchid", "Cream", "Periwinkle", "Slate", "Rose", "Sky", "Cyan", "Red")

    /** Palette specs, ids 7..16: light = [fromPalette], dark = [fromPaletteDark] of the same colours. */
    private val palettes: List<ThemeSpec> = paletteColours.mapIndexed { i, c ->
        val id = 7 + i
        ThemeSpec(id, paletteNames[i], fromPalette(id, paletteNames[i], c), fromPaletteDark(id, paletteNames[i], c))
    }

    private val builtInSpecs: List<ThemeSpec> = builtIn.mapIndexed { i, t ->
        val other = builtInCounterparts[i]
        val (l, d) = if (t.isLight) t to other else other to t
        ThemeSpec(t.id, t.name, l, d)
    }

    /** Display order in the theme bar (both modes): Default, palettes 7..16, then built-ins 1..6. */
    val all: List<ThemeSpec> = listOf(builtInSpecs[0]) + palettes + builtInSpecs.drop(1)

    val Default: ThemeSpec = builtInSpecs[0]

    /** Ids 17..26 were the separate dark palette themes before 0.1.1; they map to their spec, id - 10. */
    fun migrateId(id: Int): Int = if (id in 17..26) id - 10 else id

    /** Spec of [id] after [migrateId]; [Default] for an unknown id. */
    fun byId(id: Int): ThemeSpec = migrateId(id).let { m -> all.firstOrNull { it.id == m } } ?: Default

    /** The [variant] of theme [id] (see [byId]). */
    fun resolve(id: Int, variant: ThemeVariant): AppTheme = byId(id).variant(variant)

    private fun rgbList(vararg rgb: Int): List<Color> = rgb.map { Color(0xFF000000.toInt() or it) }

    /**
     * Dark variant of a palette (DESIGN_SPEC section 3): background = mix(darkest, Black, 0.82),
     * accent = darkest, onBackground #FBFBFB. Ramp: anchors D = [mix(background, darkest, 0.55)] +
     * the palette sorted dark to light (m = n + 1); slot j in 0..11 sits at u = j * (m - 1) / 11
     * along D, piecewise linear; the last slot is the lightest colour.
     */
    fun fromPaletteDark(id: Int, name: String, colours: List<Color>): AppTheme {
        require(colours.size >= 2) { "a palette needs at least 2 colours" }
        val sorted = colours.sortedBy { it.luminance() }
        val darkest = sorted.first()
        val background = mix(darkest, Color.Black, 0.82f)
        val d = listOf(mix(background, darkest, 0.55f)) + sorted
        val m = d.size
        val ramp = List(AppTheme.RAMP_SIZE) { j ->
            if (j == AppTheme.RAMP_SIZE - 1) {
                d.last()
            } else {
                val u = j * (m - 1) / (AppTheme.RAMP_SIZE - 1).toFloat()
                val q = u.toInt().coerceAtMost(m - 2)
                mix(d[q], d[q + 1], u - q)
            }
        }
        return AppTheme(id, name, background, darkest, light, ramp)
    }

    /**
     * Theme from a palette (DESIGN_SPEC section 3). Colours are sorted by relative luminance,
     * lightest first: background = lightest, accent = darkest, onBackground = #1D1D1D on a light
     * background, else #FBFBFB. Tile ramp: anchors A = the other colours, light to dark
     * (m = A.size), k = max(1, 8 / (m - 1)); slot j in 0..(m - 1) * k = mix(A[j / k], A[j / k + 1],
     * (j % k) / k), the last slot exactly A[m - 1]. The e remaining slots up to 11 move the
     * darkest colour toward black (light theme) or white (dark theme) by r * min(0.12, 0.6 / e).
     */
    fun fromPalette(id: Int, name: String, colours: List<Color>): AppTheme {
        require(colours.size >= 2) { "a palette needs at least 2 colours" }
        val sorted = colours.sortedByDescending { it.luminance() }
        val background = sorted.first()
        val anchors = sorted.drop(1)
        val darkest = anchors.last()
        val isLight = AppTheme.isLightBackground(background)
        val m = anchors.size
        val k = if (m <= 1) 1 else maxOf(1, 8 / (m - 1))
        val ramp = ArrayList<Color>(AppTheme.RAMP_SIZE)
        for (j in 0..minOf((m - 1) * k, AppTheme.RAMP_SIZE - 1)) {
            val q = j / k
            ramp += if (q >= m - 1) darkest else mix(anchors[q], anchors[q + 1], (j % k).toFloat() / k)
        }
        val e = AppTheme.RAMP_SIZE - ramp.size
        val toward = if (isLight) Color.Black else Color.White
        for (r in 1..e) ramp += mix(darkest, toward, r * minOf(0.12f, 0.6f / e))
        return AppTheme(id, name, background, darkest, if (isLight) dark else light, ramp)
    }
}

/** Theme of the composition; the default (Default, dark variant) only applies outside AppRoot. */
val LocalAppTheme = compositionLocalOf { AppThemes.Default.dark }

/**
 * [target] with its three base colours and its 12 tile colours animated over 200 ms
 * (DESIGN_SPEC section 9). Always 3 + 12 animations, in a fixed order.
 */
@Composable
fun animatedAppTheme(target: AppTheme): AppTheme {
    val spec = tween<Color>(durationMillis = 200)
    val bg by animateColorAsState(target.background, spec, label = "themeBackground")
    val accent by animateColorAsState(target.accent, spec, label = "themeAccent")
    val on by animateColorAsState(target.onBackground, spec, label = "themeOnBackground")
    val ramp = List(AppTheme.RAMP_SIZE) { i ->
        animateColorAsState(target.ramp[i], spec, label = "themeTile$i").value
    }
    return AppTheme(target.id, target.name, bg, accent, on, ramp)
}
