package com.apocalypse03.game2048.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.apocalypse03.game2048.ui.theme.AppTheme
import com.apocalypse03.game2048.ui.theme.AppThemes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Info panel over Home (DESIGN_SPEC section 19): content, the three ways to close, theme variant. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class InfoPanelUiTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val inPanel = hasAnyAncestor(hasTestTag("info_panel"))

    private fun openInfo(vm: AppViewModel = AppViewModel()): AppViewModel {
        rule.setApp(vm)
        rule.onNodeWithContentDescription("Info").performClick()
        // The nav action runs after the press highlight delay (DESIGN_SPEC section 14).
        rule.mainClock.advanceTimeBy(500)
        rule.waitForIdle()
        return vm
    }

    private fun assertClosed(vm: AppViewModel) {
        rule.onNodeWithTag("info_panel").assertDoesNotExist()
        assertFalse(vm.infoOpen)
        assertEquals(Screen.HOME, vm.screen)
        rule.onNodeWithText("New Game").assertExists()
    }

    @Test
    fun infoShowsLogoTitleVersionAndAuthor() {
        val vm = openInfo()
        assertTrue(vm.infoOpen)
        rule.onNodeWithTag("info_panel").assertExists()
        rule.onNode(hasContentDescription("2048 logo") and inPanel, useUnmergedTree = true).assertExists()
        rule.onNode(hasText("2048") and inPanel, useUnmergedTree = true).assertExists()
        rule.onNode(hasText("Version 0.1.1") and inPanel, useUnmergedTree = true).assertExists()
        rule.onNode(hasText("Author: Apocalypse03") and inPanel, useUnmergedTree = true).assertExists()
        rule.onNode(hasContentDescription("Close") and inPanel, useUnmergedTree = true).assertExists()
        // Home beneath the modal is hidden from accessibility.
        rule.onNodeWithText("New Game").assertDoesNotExist()
    }

    @Test
    fun closeDismisses() {
        val vm = openInfo()
        rule.onNodeWithContentDescription("Close").performClick()
        rule.waitForIdle()
        assertClosed(vm)
    }

    @Test
    fun tapOnPanelKeepsItOpen() {
        val vm = openInfo()
        rule.onNodeWithTag("info_panel").performTouchInput { click(Offset(width / 2f, height * 0.08f)) }
        rule.waitForIdle()
        assertTrue(vm.infoOpen)
        rule.onNodeWithTag("info_panel").assertExists()
    }

    @Test
    fun tapOutsideDismisses() {
        val vm = openInfo()
        rule.onRoot().performTouchInput { click(Offset(width / 2f, height * 0.1f)) }
        rule.waitForIdle()
        assertClosed(vm)
    }

    @Test
    fun backDismissesAndStaysHome() {
        val vm = openInfo()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        assertClosed(vm)
    }

    /** Panel fill just inside its top edge, and the logo's accent square near its top-left corner. */
    private fun assertVariant(theme: AppTheme) {
        val panel = rule.onNodeWithTag("info_panel").captureToImage().asAndroidBitmap()
        assertEquals(theme.background.toArgb(), panel.getPixel(panel.width / 2, (panel.height * 0.08f).toInt()))
        val logo = rule.onNode(hasContentDescription("2048 logo") and inPanel, useUnmergedTree = true).captureToImage().asAndroidBitmap()
        assertEquals(theme.accent.toArgb(), logo.getPixel((logo.width * 0.15f).toInt(), (logo.height * 0.15f).toInt()))
    }

    @Test
    @Config(qualifiers = "+night")
    fun nightUsesTheDarkVariant() {
        openInfo()
        assertVariant(AppThemes.Default.dark)
    }

    @Test
    @Config(qualifiers = "+notnight")
    fun notNightUsesTheLightVariant() {
        openInfo()
        assertVariant(AppThemes.Default.light)
    }
}
