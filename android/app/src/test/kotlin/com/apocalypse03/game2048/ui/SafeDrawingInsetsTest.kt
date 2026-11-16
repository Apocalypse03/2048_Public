package com.apocalypse03.game2048.ui

import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.apocalypse03.game2048.ui.game.BOARD_TAG
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Content stays inside WindowInsets.safeDrawing (DESIGN_SPEC section 1): fake status-bar,
 * cutout and navigation-bar insets are dispatched to the Compose view, then key nodes must
 * lie between them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class SafeDrawingInsetsTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val top = 160
    private val cutoutLeft = 90
    private val bottom = 130

    private fun applyInsets() {
        rule.runOnUiThread { WindowCompat.setDecorFitsSystemWindows(rule.activity.window, false) }
        rule.waitForIdle()
        rule.runOnUiThread {
            val composeView = rule.activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, top, 0, 0))
                .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(cutoutLeft, 0, 0, 0))
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, bottom))
                .build()
            ViewCompat.dispatchApplyWindowInsets(composeView, insets)
        }
        rule.waitForIdle()
    }

    private fun assertInsideSafe(name: String, b: androidx.compose.ui.geometry.Rect) {
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue(
            "$name $b not inside safe area (left $cutoutLeft, top $top, bottom ${root.bottom - bottom}) of $root",
            b.left >= cutoutLeft - 0.5f && b.top >= top - 0.5f && b.bottom <= root.bottom - bottom + 0.5f,
        )
    }

    @Test
    fun homeAndGameStayInsideSafeDrawing() {
        val vm = AppViewModel()
        rule.setApp(vm)
        val paletteBefore = rule.onNodeWithContentDescription("Palette").fetchSemanticsNode().boundsInRoot
        applyInsets()
        val palette = rule.onNodeWithContentDescription("Palette").fetchSemanticsNode().boundsInRoot
        assertTrue("insets had no effect: $paletteBefore -> $palette", palette != paletteBefore)
        assertInsideSafe("Palette", palette)
        assertInsideSafe("New Game", rule.onNodeWithText("New Game").fetchSemanticsNode().boundsInRoot)
        assertInsideSafe("Info", rule.onNodeWithContentDescription("Info").fetchSemanticsNode().boundsInRoot)

        rule.runOnUiThread { vm.startNewGame() }
        rule.waitForIdle()
        assertInsideSafe("Back", rule.onNodeWithContentDescription("Back").fetchSemanticsNode().boundsInRoot)
        assertInsideSafe("Undo", rule.onNodeWithContentDescription("Undo").fetchSemanticsNode().boundsInRoot)
        assertInsideSafe("board", rule.onNodeWithTag(BOARD_TAG).fetchSemanticsNode().boundsInRoot)
    }
}
