package com.apocalypse03.game2048.ui

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import com.apocalypse03.game2048.game.GameStatus
import com.apocalypse03.game2048.ui.game.ShowcaseBoard
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** UI labels follow the system font scale clamped to 1.0..1.3; tile numbers ignore it (DESIGN_SPEC section 1). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class FontScaleTest {

    @get:Rule
    val rule = createComposeRule()

    private val fontScale = mutableFloatStateOf(1.3f)

    private fun text(s: String) = rule.onNodeWithText(s, useUnmergedTree = true)

    private fun SemanticsNodeInteraction.layout(): TextLayoutResult {
        val out = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(out)
        return out.single()
    }

    /**
     * One line, and the box gave the text its full natural size: glyph extent and line height
     * fit the laid-out node, so nothing is clipped. (hasVisualOverflow is not used: with
     * softWrap = false the paragraph width is not the glyph width.)
     */
    private fun assertFits(s: String) {
        val node = text(s)
        node.assertExists()
        val l = node.layout()
        assertEquals("'$s' lines", 1, l.lineCount)
        val glyphW = l.getLineRight(0) - l.getLineLeft(0)
        val textH = l.multiParagraph.height
        println("fit '$s': glyphs $glyphW x $textH px, node ${l.size}")
        assertTrue(
            "'$s' clipped: glyphs $glyphW x $textH in node ${l.size}",
            glyphW <= l.size.width + 0.5f && textH <= l.size.height + 0.5f,
        )
    }

    /** Laid-out text height (not the node, whose height is capped by its box). */
    private fun height(s: String) = text(s).layout().multiParagraph.height

    private fun setFontScale(v: Float) {
        fontScale.floatValue = v
        rule.waitForIdle()
    }

    @Test
    fun homeLabelsFitAt130() {
        val vm = AppViewModel()
        rule.setAppWithFontScale(vm, fontScale)
        listOf("Normal", "New Game").forEach(::assertFits)
        vm.stepMode(1)
        rule.waitForIdle()
        assertFits("2048⁺")
    }

    /** Glyph extent (px) of the mode label [s]. */
    private fun glyphs(s: String): Pair<Float, Float> {
        val label = text(s)
        val bounds = label.fetchSemanticsNode().boundsInRoot
        val l = label.layout()
        return (bounds.left + l.getLineLeft(0)) to (bounds.left + l.getLineRight(0))
    }

    private fun chevronX(desc: String) =
        rule.onNodeWithContentDescription(desc).fetchSemanticsNode().boundsInRoot.center.x

    @Test
    fun modeLabelsStayBetweenChevrons() {
        val vm = AppViewModel()
        rule.setAppWithFontScale(vm, fontScale)
        val (nLeft, nRight) = glyphs("Normal")
        val next = chevronX("Next mode")
        vm.stepMode(1)
        rule.waitForIdle()
        val (pLeft, pRight) = glyphs("2048⁺")
        val prev = chevronX("Previous mode")
        println("Normal glyphs [$nLeft, $nRight], 2048+ glyphs [$pLeft, $pRight] px, chevron centres $prev, $next px")
        assertTrue("Normal [$nLeft, $nRight] not between $prev..$next", nLeft > prev && nRight < next)
        assertTrue("2048+ [$pLeft, $pRight] not between $prev..$next", pLeft > prev && pRight < next)
    }

    @Test
    fun gameLabelsFitAt130() {
        val vm = AppViewModel().apply { startNewGame() }
        vm.restoreGame(ShowcaseBoard, score = 999_999, best = 999_999)
        rule.setAppWithFontScale(vm, fontScale)
        listOf("999999", "Best 999999", "0S", "Swipe to begin.").forEach(::assertFits)
        vm.restoreGame(ShowcaseBoard, score = 0, best = 0, status = GameStatus.OVER)
        rule.waitForIdle()
        assertFits("Game over.")
    }

    @Test
    fun userFontSizeScalesLabelsDown() {
        val vm = AppViewModel().apply { startNewGame() }
        rule.setAppWithFontScale(vm, fontScale, ShowcaseBoard)
        setFontScale(1.0f)
        vm.setFontScale(100)
        rule.waitForIdle()
        val hundred = height("Swipe to begin.")
        vm.setFontScale(70)
        rule.waitForIdle()
        val seventy = height("Swipe to begin.")
        println("hint at user 100 %: $hundred px, at 70 %: $seventy px")
        assertTrue("70 % did not shrink the label: $seventy vs $hundred", seventy < hundred * 0.9f)
    }

    @Test
    fun userFontSizeIsCappedWithSystemScale() {
        val vm = AppViewModel().apply { startNewGame() }
        rule.setAppWithFontScale(vm, fontScale, ShowcaseBoard)
        setFontScale(1.3f)
        vm.setFontScale(100)
        rule.waitForIdle()
        val systemOnly = height("Swipe to begin.")
        vm.setFontScale(130)
        rule.waitForIdle()
        val capped = height("Swipe to begin.")
        assertEquals("1.3 x 1.3 not capped at 1.3", systemOnly, capped, 0.5f)
    }

    @Test
    fun userFontSizeLeavesTilesAlone() {
        val vm = AppViewModel().apply { startNewGame() }
        rule.setAppWithFontScale(vm, fontScale, ShowcaseBoard)
        setFontScale(1.0f)
        vm.setFontScale(100)
        rule.waitForIdle()
        val tile100 = height("2048")
        vm.setFontScale(70)
        rule.waitForIdle()
        assertEquals("tile text follows user font size", tile100, height("2048"), 0.5f)
        vm.setFontScale(130)
        rule.waitForIdle()
        assertEquals("tile text follows user font size", tile100, height("2048"), 0.5f)
    }

    @Test
    fun labelsGrowTilesDoNotAndScaleIsClamped() {
        val vm = AppViewModel().apply { startNewGame() }
        rule.setAppWithFontScale(vm, fontScale, ShowcaseBoard)
        setFontScale(1.0f)
        val hint10 = height("Swipe to begin.")
        val best10 = height("Best 0")
        val tile10 = height("2048")
        setFontScale(1.3f)
        val hint13 = height("Swipe to begin.")
        val best13 = height("Best 0")
        val tile13 = height("2048")
        setFontScale(2.0f)
        val hint20 = height("Swipe to begin.")
        val tile20 = height("2048")
        setFontScale(0.85f)
        val hint085 = height("Swipe to begin.")

        assertTrue("hint did not grow: $hint10 -> $hint13", hint13 > hint10 * 1.2f)
        assertTrue("best did not grow: $best10 -> $best13", best13 > best10 * 1.2f)
        assertEquals("tile text follows font scale", tile10, tile13, 0.5f)
        assertEquals("tile text follows font scale", tile10, tile20, 0.5f)
        assertTrue("2.0 not clamped to 1.3: $hint13 vs $hint20", abs(hint13 - hint20) <= 0.5f)
        assertTrue("0.85 not clamped to 1.0: $hint10 vs $hint085", abs(hint10 - hint085) <= 0.5f)
    }
}
