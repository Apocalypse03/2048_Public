package com.apocalypse03.game2048.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.apocalypse03.game2048.ui.game.ShowcaseBoard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class AppFlowTest {

    @get:Rule
    val rule = createComposeRule()

    private val swatches = hasContentDescription("Theme ", substring = true)
    private val navLabels = listOf("Settings", "Shop", "Leaderboard", "Info")

    @Test
    fun homeShowsContent() {
        rule.setApp(AppViewModel())
        rule.onNodeWithContentDescription("2048 logo").assertExists()
        rule.onNodeWithText("Normal").assertExists()
        rule.onNodeWithText("New Game").assertExists()
        navLabels.forEach { rule.onNodeWithContentDescription(it).assertExists() }
    }

    @Test
    fun shopIsInertAndInfoOpensThePanel() {
        val vm = AppViewModel()
        rule.setApp(vm)
        // Settings and Leaderboard navigate since DESIGN_SPEC sections 15-16 (see NavUiTest).
        rule.onNodeWithContentDescription("Shop").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("New Game").assertExists()
        assertEquals(Screen.HOME, vm.screen)
        assertFalse(vm.infoOpen)
        // Info opens the panel over Home since DESIGN_SPEC section 19 (see InfoPanelUiTest).
        rule.onNodeWithContentDescription("Info").performClick()
        // The nav action runs after the press highlight delay (DESIGN_SPEC section 14).
        rule.mainClock.advanceTimeBy(500)
        rule.waitForIdle()
        rule.onNodeWithTag("info_panel").assertExists()
        assertEquals(Screen.HOME, vm.screen)
        assertTrue(vm.infoOpen)
    }

    @Test
    fun chevronsCycleMode() {
        rule.setApp(AppViewModel())
        rule.onNodeWithText("Normal").assertExists()
        rule.onNodeWithContentDescription("Previous mode").assertDoesNotExist()
        rule.onNodeWithContentDescription("Next mode").performClick()
        rule.onNodeWithText("2048⁺").assertExists()
        rule.onNodeWithContentDescription("Previous mode").assertExists()
        rule.onNodeWithContentDescription("Next mode").assertDoesNotExist()
        rule.onNodeWithContentDescription("Previous mode").performClick()
        rule.onNodeWithText("Normal").assertExists()
        rule.onNodeWithContentDescription("Previous mode").assertDoesNotExist()
        rule.onNodeWithContentDescription("Next mode").assertExists()
    }

    private fun checkPaletteBar() {
        rule.onAllNodes(swatches).assertCountEquals(0)
        rule.onNodeWithContentDescription("Palette").performClick()
        rule.waitForIdle()
        rule.onAllNodes(swatches).assertCountEquals(17)
        rule.onNodeWithContentDescription("Theme Default").assertIsDisplayed().assertIsSelected()
        // Olive sits right of the 7 visible swatches when Default is selected: scroll to it.
        rule.onNodeWithContentDescription("Theme Olive").tapSwatch()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Theme Olive").assertIsSelected()
        rule.onAllNodes(swatches).assertCountEquals(17)
        rule.onNodeWithContentDescription("Palette").performClick()
        rule.waitForIdle()
        rule.onAllNodes(swatches).assertCountEquals(0)
    }

    @Test
    fun paletteBarOnHome() {
        val vm = AppViewModel()
        rule.setApp(vm)
        checkPaletteBar()
        assertEquals(1, vm.themeId)
    }

    @Test
    fun paletteBarOnGame() {
        val vm = AppViewModel().apply { startNewGame() }
        rule.setApp(vm)
        checkPaletteBar()
    }

    @Test
    fun themeChangeKeepsBoardAndScore() {
        val vm = AppViewModel().apply { startNewGame() }
        rule.setApp(vm, ShowcaseBoard)
        val probes = listOf("2", "4096", "0", "Best 0")
        val before = probes.map { rule.onAllNodesWithText(it).fetchSemanticsNodes().size }
        rule.onNodeWithContentDescription("Palette").performClick()
        rule.onNodeWithContentDescription("Theme Blue").tapSwatch()
        rule.waitForIdle()
        val after = probes.map { rule.onAllNodesWithText(it).fetchSemanticsNodes().size }
        assertEquals(before, after)
        assertEquals(2, before[0])
        assertEquals(Screen.GAME, vm.screen)
        assertEquals(3, vm.themeId)
    }

    @Test
    fun newGameThenBack() {
        val vm = AppViewModel()
        rule.setApp(vm)
        rule.onNodeWithText("New Game").performClick()
        rule.waitForIdle()
        assertEquals(Screen.GAME, vm.screen)
        rule.onNodeWithText("Swipe to begin.").assertExists()
        rule.onNodeWithText("0S").assertExists()
        rule.onNodeWithContentDescription("Back").performClick()
        rule.waitForIdle()
        assertEquals(Screen.HOME, vm.screen)
        rule.onNodeWithText("New Game").assertExists()
    }
}
