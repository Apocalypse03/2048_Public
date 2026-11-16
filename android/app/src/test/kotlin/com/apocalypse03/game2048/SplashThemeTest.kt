package com.apocalypse03.game2048

import android.util.TypedValue
import androidx.compose.ui.graphics.toArgb
import com.apocalypse03.game2048.ui.theme.AppThemes
import com.apocalypse03.game2048.ui.theme.ThemeVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Guards that the static splash styles and colours stay in sync with [AppThemes]. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SplashThemeTest {

    private fun hex(c: Int) = "#%08X".format(c)

    private fun resolved(style: Int, attr: Int): Int {
        val theme = RuntimeEnvironment.getApplication().resources.newTheme()
        theme.applyStyle(style, true)
        val tv = TypedValue()
        assertTrue("attr not resolved", theme.resolveAttribute(attr, tv, true))
        return tv.data
    }

    private fun assertSynced(variant: ThemeVariant) {
        for (spec in AppThemes.all) {
            val expected = AppThemes.resolve(spec.id, variant).background.toArgb()
            val style = SplashThemes.styleFor(spec.id)
            val attrs = mapOf(
                "windowBackground" to android.R.attr.windowBackground,
                "statusBarColor" to android.R.attr.statusBarColor,
                "navigationBarColor" to android.R.attr.navigationBarColor,
            )
            for ((name, attr) in attrs) {
                val actual = resolved(style, attr)
                assertEquals(
                    "theme id ${spec.id} $variant $name: style ${hex(actual)} != AppThemes ${hex(expected)}",
                    expected,
                    actual,
                )
            }
            assertLogoColours(spec.id, variant, style)
        }
        // Base theme must resolve the splash icon attrs to the Default (id 0) values.
        assertLogoColours(0, variant, R.style.Theme_Game2048, "Theme.Game2048")
    }

    // Logo2048.kt: fill = accent, glyph tint = onAccent at alpha 0.94.
    private fun assertLogoColours(id: Int, variant: ThemeVariant, style: Int, label: String = "") {
        val theme = AppThemes.resolve(id, variant)
        val expected = mapOf(
            "splashAccent" to (R.attr.splashAccent to theme.accent.toArgb()),
            "splashOnAccent" to (R.attr.splashOnAccent to theme.onAccent.copy(alpha = 0.94f).toArgb()),
        )
        for ((name, pair) in expected) {
            val (attr, want) = pair
            val actual = resolved(style, attr)
            assertEquals(
                "theme id $id $variant $label $name: style ${hex(actual)} != AppThemes ${hex(want)}",
                want,
                actual,
            )
        }
    }

    @Test
    @Config(qualifiers = "notnight")
    fun lightColoursMatchAppThemes() = assertSynced(ThemeVariant.LIGHT)

    @Test
    @Config(qualifiers = "night")
    fun darkColoursMatchAppThemes() = assertSynced(ThemeVariant.DARK)

    @Test
    fun stylesAreDistinctAndUnknownFallsBackToDefault() {
        val styles = AppThemes.all.map { SplashThemes.styleFor(it.id) }
        assertEquals(AppThemes.all.size, styles.toSet().size)
        assertEquals(SplashThemes.styleFor(0), SplashThemes.styleFor(-1))
        assertEquals(SplashThemes.styleFor(0), SplashThemes.styleFor(99))
    }

    @Test
    fun styleNamesAreStable() {
        val res = RuntimeEnvironment.getApplication().resources
        for (spec in AppThemes.all) {
            assertEquals(
                "Theme.Game2048.Splash${spec.id}",
                res.getResourceEntryName(SplashThemes.styleFor(spec.id)),
            )
        }
    }
}
