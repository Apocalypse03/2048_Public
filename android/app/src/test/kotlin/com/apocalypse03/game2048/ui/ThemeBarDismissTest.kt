package com.apocalypse03.game2048.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w800dp-h1280dp")
class ThemeBarDismissTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val swatches = hasContentDescription("Theme ", substring = true)

    private fun start(vm: AppViewModel) {
        rule.setContent { AppRoot(vm) }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Palette").performClick()
        rule.waitForIdle()
        rule.onAllNodes(swatches).assertCountEquals(17)
    }

    @Test
    fun tapOutsideFrameClosesBar() {
        val vm = AppViewModel()
        start(vm)
        // Frame is ~618dp wide centred in 800dp, so x = 20 is background outside the frame.
        rule.onRoot().performTouchInput { click(Offset(20f, 640f)) }
        rule.waitForIdle()
        rule.onAllNodes(swatches).assertCountEquals(0)
        assertEquals(Screen.HOME, vm.screen)
    }

    @Test
    fun backClosesBarThenGoesHome() {
        val vm = AppViewModel().apply { startNewGame() }
        start(vm)
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.onAllNodes(swatches).assertCountEquals(0)
        assertEquals(Screen.GAME, vm.screen)
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        assertEquals(Screen.HOME, vm.screen)
    }
}
