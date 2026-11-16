package com.apocalypse03.game2048.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Home nav press highlight timing (section 14) and Settings / Results navigation (sections 15-16). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class NavUiTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun back() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    /** Shows a fresh app with the clock under manual control. */
    private fun manualClockApp(): AppViewModel {
        val vm = AppViewModel()
        rule.setApp(vm)
        rule.mainClock.autoAdvance = false
        return vm
    }

    private fun advance(ms: Long) {
        rule.mainClock.advanceTimeBy(ms)
        rule.waitForIdle()
    }

    @Test
    fun settingsActsAfterTheReleaseDelay() {
        val vm = manualClockApp()
        rule.onNodeWithContentDescription("Settings").assertHasClickAction().performClick()
        advance(60)
        assertEquals(Screen.HOME, vm.screen)
        advance(100)
        assertEquals(Screen.SETTINGS, vm.screen)
    }

    @Test
    fun leaderboardOpensResults() {
        val vm = manualClockApp()
        rule.onNodeWithContentDescription("Leaderboard").performClick()
        advance(200)
        assertEquals(Screen.RESULTS, vm.screen)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        rule.onNodeWithText("Results").assertExists()
    }

    @Test
    fun shopChangesNothing() {
        val vm = manualClockApp()
        rule.onNodeWithContentDescription("Shop").performClick()
        advance(500)
        assertEquals(Screen.HOME, vm.screen)
        assertFalse(vm.infoOpen)
        rule.onNodeWithText("New Game").assertExists()
    }

    @Test
    fun infoOpensThePanelAfterTheReleaseDelay() {
        val vm = manualClockApp()
        rule.onNodeWithContentDescription("Info").performClick()
        advance(60)
        assertFalse(vm.infoOpen)
        advance(100)
        assertTrue(vm.infoOpen)
        assertEquals(Screen.HOME, vm.screen)
    }

    @Test
    fun accessibilityClickFlashesThenActs() {
        val vm = manualClockApp()
        rule.onNodeWithContentDescription("Settings").performSemanticsAction(SemanticsActions.OnClick)
        advance(100)
        assertEquals(Screen.HOME, vm.screen)
        advance(100)
        assertEquals(Screen.SETTINGS, vm.screen)
    }

    @Test
    fun backArrowAndSystemBackReturnHome() {
        val vm = AppViewModel()
        rule.setApp(vm)
        rule.runOnUiThread { vm.openSettings() }
        rule.waitForIdle()
        rule.onNodeWithText("Animation speeds").assertExists()
        rule.onNodeWithContentDescription("Back").performClick()
        rule.waitForIdle()
        assertEquals(Screen.HOME, vm.screen)

        rule.runOnUiThread { vm.openResults() }
        rule.waitForIdle()
        back()
        assertEquals(Screen.HOME, vm.screen)

        rule.runOnUiThread { vm.openSettings() }
        rule.waitForIdle()
        back()
        assertEquals(Screen.HOME, vm.screen)
        rule.onNodeWithText("New Game").assertExists()
    }

    @Test
    fun backClosesThePaletteFirstOnSubScreens() {
        val vm = AppViewModel()
        rule.setApp(vm)
        rule.runOnUiThread { vm.openResults() }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Palette").performClick()
        rule.waitForIdle()
        back()
        assertEquals(Screen.RESULTS, vm.screen)
        back()
        assertEquals(Screen.HOME, vm.screen)
    }
}
