package com.apocalypse03.game2048.ui

import android.content.res.Configuration
import android.view.View
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.apocalypse03.game2048.MainActivity
import com.apocalypse03.game2048.ui.theme.AppThemes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.RobolectricTestRunner

/**
 * The system dark mode picks the variant of the selected theme (STYLE_ARCHITECTURE.md): the frame
 * background (drawn by RefFrame) is the active variant's background, and it follows a live switch.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class ThemeVariantUiTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var view: View

    private fun show(vm: AppViewModel) {
        rule.setContent {
            view = LocalView.current
            AppRoot(vm)
        }
        rule.waitForIdle()
    }

    /** Top-left pixel of the window: plain frame background on Home. */
    private fun backgroundPixel(): Int = rule.onRoot().captureToImage().asAndroidBitmap().getPixel(2, 2)

    private fun swatchCount(): Int {
        rule.onNodeWithContentDescription("Palette").performClick()
        rule.waitForIdle()
        return rule.onAllNodesWithContentDescription("Theme ", substring = true).fetchSemanticsNodes().size
    }

    @Test
    @Config(qualifiers = "+night")
    fun nightRendersTheDarkVariant() {
        show(AppViewModel().apply { selectTheme(7) })
        assertEquals(AppThemes.byId(7).dark.background.toArgb(), backgroundPixel())
    }

    @Test
    @Config(qualifiers = "+notnight")
    fun notNightRendersTheLightVariant() {
        show(AppViewModel().apply { selectTheme(7) })
        assertEquals(AppThemes.byId(7).light.background.toArgb(), backgroundPixel())
    }

    @Test
    @Config(qualifiers = "+notnight")
    fun firstLaunchShowsDefaultInTheSystemVariant() {
        show(AppViewModel())
        assertEquals(AppThemes.Default.light.background.toArgb(), backgroundPixel())
    }

    @Test
    @Config(qualifiers = "+notnight")
    fun liveSwitchFollowsTheSystemWithoutRecreating() {
        val vm = AppViewModel().apply { selectTheme(3) }
        show(vm)
        assertEquals(AppThemes.byId(3).light.background.toArgb(), backgroundPixel())
        // What the system does for an activity declaring configChanges="uiMode": the new
        // configuration reaches the existing view tree; nothing is recreated. (RuntimeEnvironment
        // .setQualifiers would recreate the test activity, so the change is dispatched directly.)
        rule.runOnUiThread {
            val night = Configuration(view.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
            }
            view.rootView.dispatchConfigurationChanged(night)
        }
        rule.waitForIdle()
        assertEquals(AppThemes.byId(3).dark.background.toArgb(), backgroundPixel())
        assertEquals(3, vm.themeId)
    }

    @Test
    @Config(qualifiers = "+night")
    fun themeBarShowsAllSpecsInNightMode() {
        show(AppViewModel())
        assertEquals(17, swatchCount())
    }

    @Test
    @Config(qualifiers = "+notnight")
    fun themeBarShowsAllSpecsInDayMode() {
        show(AppViewModel())
        assertEquals(17, swatchCount())
    }
}

/** MainActivity handles uiMode itself (manifest configChanges), so a dark-mode toggle keeps the activity. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "notnight")
class MainActivityUiModeTest {
    @Test
    fun uiModeChangeDoesNotRecreate() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val before = controller.get()
        val night = Configuration(before.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
        }
        controller.configurationChange(night)
        assertSame(before, controller.get())
        assertFalse(before.isFinishing)
        controller.pause().stop().destroy()
    }
}
