package com.apocalypse03.game2048.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.toSize
import com.apocalypse03.game2048.ui.game.BOARD_TAG
import com.apocalypse03.game2048.ui.theme.AppThemes
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Key Home/Game nodes are displayed inside the window on landscape and squat windows (DESIGN_SPEC section 1). */
@RunWith(RobolectricTestRunner::class)
abstract class WindowFitBase {
    @get:Rule
    val rule = createComposeRule()

    private fun root(): Rect = rule.onRoot().fetchSemanticsNode().boundsInRoot

    private fun SemanticsNodeInteraction.assertInsideWindow(name: String) {
        assertIsDisplayed()
        val b = fetchSemanticsNode().boundsInRoot
        val r = root()
        assertTrue(
            "$name $b outside window $r",
            b.left >= r.left && b.top >= r.top && b.right <= r.right && b.bottom <= r.bottom,
        )
    }

    @Test
    fun homeKeyNodesInsideWindow() {
        rule.setApp(AppViewModel())
        rule.onNodeWithText("New Game").assertInsideWindow("New Game")
        rule.onNodeWithContentDescription("Palette").assertInsideWindow("Palette")
        rule.onNodeWithContentDescription("Next mode").assertInsideWindow("Next mode")
        rule.onNodeWithContentDescription("Info").assertInsideWindow("Info")
    }

    @Test
    fun gameKeyNodesInsideWindowAndBoardSquare() {
        rule.setApp(AppViewModel().apply { startNewGame() })
        rule.onNodeWithTag(BOARD_TAG).assertInsideWindow("board")
        rule.onNodeWithContentDescription("Undo").assertInsideWindow("Undo")
        rule.onNodeWithContentDescription("Palette").assertInsideWindow("Palette")
        rule.onNodeWithContentDescription("Back").assertInsideWindow("Back")
        val b = rule.onNodeWithTag(BOARD_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue("not square: ${b.width} x ${b.height}", abs(b.width - b.height) <= 1f)
    }
}

@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land")
class WindowFitLandscapeTablet : WindowFitBase()

@Config(sdk = [34], qualifiers = "w411dp-h600dp")
class WindowFitSquatPhone : WindowFitBase()

/** Swatch hit areas never overlap and stay at least 48 dp high (DESIGN_SPEC section 8). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp")
class SwatchHitAreaTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun adjacentSwatchHitAreasDoNotOverlap() {
        lateinit var density: Density
        rule.setContent {
            density = LocalDensity.current
            AppRoot(AppViewModel())
        }
        rule.onNodeWithContentDescription("Palette").performClick()
        rule.waitForIdle()
        val nodes = rule.onAllNodesWithContentDescription("Theme ", substring = true).fetchSemanticsNodes()
        assertEquals(AppThemes.all.size, nodes.size)
        // Unclipped node rects: boundsInRoot is clipped to the 139 rp bar, but hit testing is not.
        val bounds = nodes.map { Rect(it.positionInRoot, it.size.toSize()) }.sortedBy { it.left }
        val minPx = with(density) { 48f * this.density }
        bounds.forEach { assertTrue("swatch $it shorter than 48 dp", it.height >= minPx - 0.5f) }
        bounds.zipWithNext().forEach { (a, b) ->
            assertFalse("swatch hit areas overlap: $a and $b", a.right > b.left + 0.5f)
        }
        // Each hit area stays centred on its visual swatch: centres keep the spec pitch order.
        assertTrue(bounds.zipWithNext().all { (a, b) -> a.center.x < b.center.x })
        rule.onNode(hasContentDescription("Theme Default")).assertIsDisplayed()
    }
}
