package com.apocalypse03.game2048.ui

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.core.app.ApplicationProvider
import com.apocalypse03.game2048.ui.components.THEME_BAR_FIRST_X
import com.apocalypse03.game2048.ui.components.THEME_BAR_PITCH
import com.apocalypse03.game2048.ui.components.THEME_BAR_VISIBLE
import com.apocalypse03.game2048.ui.components.themeBarFirstVisible
import com.apocalypse03.game2048.ui.data.SharedPrefsSettingsStore
import com.apocalypse03.game2048.ui.theme.AppThemes
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Scrollable theme bar: order, pitch, swipe, initial scroll, selection (DESIGN_SPEC section 8). */
@RunWith(RobolectricTestRunner::class)
// xxxhdpi: 1 px is about 0.6 rp on this frame, fine enough for the 1 rp position checks.
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxxhdpi")
class ThemeBarScrollTest {

    @get:Rule
    val rule = createComposeRule()

    private val names = AppThemes.all.map { it.name }
    private val strip get() = rule.onNode(hasScrollAction() and hasAnyDescendant(hasContentDescription("Theme Lavender")))

    private fun swatch(name: String): SemanticsNodeInteraction = rule.onNodeWithContentDescription("Theme $name")

    private fun open(vm: AppViewModel) {
        rule.setApp(vm)
        rule.onNodeWithContentDescription("Palette").performClick()
        rule.waitForIdle()
    }

    /** Swatch hit-area centre x in frame rp, measured from the open palette toggle (centre x 893 rp). */
    private fun centreRp(name: String): Float {
        val root = rule.onRoot().fetchSemanticsNode().size
        val s = minOf(root.width / 989f, root.height / 2048f)
        val toggle = rule.onNodeWithContentDescription("Palette").fetchSemanticsNode()
        val toggleX = toggle.positionInRoot.x + toggle.size.width / 2f
        val n = swatch(name).fetchSemanticsNode()
        return 893f + (n.positionInRoot.x + n.size.width / 2f - toggleX) / s
    }

    /** Swipe on the strip away from the palette toggle, which covers its right end. */
    private fun swipeStrip(left: Boolean) = strip.performTouchInput {
        val a = Offset(width * 0.7f, centerY)
        val b = Offset(width * 0.1f, centerY)
        if (left) swipe(a, b) else swipe(b, a)
    }

    @Test
    fun atScrollZeroFirstSevenAreDefaultToSlateAtUniformPitch() {
        open(AppViewModel().apply { selectTheme(7) })
        names.take(7).forEachIndexed { i, name ->
            swatch(name).assertIsDisplayed()
            val expected = THEME_BAR_FIRST_X + i * THEME_BAR_PITCH
            val got = centreRp(name)
            assertTrue("$name centre $got rp, expected $expected", abs(got - expected) <= 1f)
        }
        assertEquals(listOf("Default", "Lavender", "Lilac", "Orchid", "Cream", "Periwinkle", "Slate"), names.take(7))
        names.drop(7).forEach { swatch(it).assertIsNotDisplayed() }
    }

    @Test
    fun swipeLeftRevealsBuiltInThemesWithoutSelecting() {
        val vm = AppViewModel().apply { selectTheme(7) }
        open(vm)
        repeat(10) {
            swipeStrip(left = true)
            rule.waitForIdle()
        }
        assertEquals(7, vm.themeId)
        listOf("Red", "Olive", "Orange", "Blue", "Light blue", "Green", "Pink").forEachIndexed { i, name ->
            swatch(name).assertIsDisplayed()
            val got = centreRp(name)
            val expected = THEME_BAR_FIRST_X + i * THEME_BAR_PITCH
            assertTrue("$name centre $got rp, expected $expected (snapped to the end)", abs(got - expected) <= 1f)
        }
        swatch("Lavender").assertIsNotDisplayed()
        repeat(10) {
            swipeStrip(left = false)
            rule.waitForIdle()
        }
        assertEquals(7, vm.themeId)
        swatch("Default").assertIsDisplayed()
        assertTrue(abs(centreRp("Default") - THEME_BAR_FIRST_X) <= 1f)
    }

    @Test
    fun selectingEachThemeAppliesAndPersists() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val store = SharedPrefsSettingsStore(context)
        val vm = AppViewModel(settings = store)
        open(vm)
        for (t in AppThemes.all) {
            swatch(t.name).tapSwatch()
            rule.waitForIdle()
            assertEquals(t.id, vm.themeId)
            swatch(t.name).assertIsSelected()
            assertEquals(t.id, SharedPrefsSettingsStore(context).themeId)
            assertEquals(t.id, AppViewModel(settings = SharedPrefsSettingsStore(context)).themeId)
        }
    }

    @Test
    fun openingShowsTheSelectedSwatch() {
        val vm = AppViewModel()
        open(vm)
        // Default (index 0) selected: first visible = clamp(0 - 3, 0, 10) = 0, Default at the left end.
        swatch("Default").assertIsDisplayed().assertIsSelected()
        assertTrue(abs(centreRp("Default") - THEME_BAR_FIRST_X) <= 1f)
        rule.onNodeWithContentDescription("Palette").performClick()
        rule.waitForIdle()
        AppThemes.all.forEachIndexed { index, t ->
            vm.selectTheme(t.id)
            rule.onNodeWithContentDescription("Palette").performClick()
            rule.waitForIdle()
            swatch(t.name).assertIsDisplayed().assertIsSelected()
            val first = themeBarFirstVisible(index, AppThemes.all.size)
            assertEquals(first, (index - 3).coerceIn(0, AppThemes.all.size - THEME_BAR_VISIBLE))
            val got = centreRp(names[first])
            assertTrue("${t.name}: first visible ${names[first]} at $got rp", abs(got - THEME_BAR_FIRST_X) <= 1f)
            rule.onNodeWithContentDescription("Palette").performClick()
            rule.waitForIdle()
        }
    }

    /** Top edge (rp) of [n], measured from the open palette toggle (centre y 97 rp). */
    private fun topRp(n: androidx.compose.ui.semantics.SemanticsNode): Float {
        val root = rule.onRoot().fetchSemanticsNode().size
        val s = minOf(root.width / 989f, root.height / 2048f)
        val toggle = rule.onNodeWithContentDescription("Palette").fetchSemanticsNode()
        val toggleY = toggle.positionInRoot.y + toggle.size.height / 2f
        return 97f + (n.positionInRoot.y - toggleY) / s
    }

    @Test
    fun swatchesAreCentredOnThePillAndStayLeftOfTheToggle() {
        open(AppViewModel().apply { selectTheme(7) })
        val root = rule.onRoot().fetchSemanticsNode().size
        val s = minOf(root.width / 989f, root.height / 2048f)
        names.take(7).forEach { name ->
            val n = swatch(name).fetchSemanticsNode()
            val cy = topRp(n) + n.size.height / s / 2f
            assertTrue("$name centre y $cy rp, expected 104.5", abs(cy - 104.5f) <= 1f)
            val right = centreRp(name) + n.size.width / s / 2f
            assertTrue("$name hit area right edge $right rp passes 832", right <= 832.5f)
        }
    }

    @Test
    fun scoreBestAndTimerStayVisibleBelowThePillWhileTheBarIsOpen() {
        val vm = AppViewModel().apply { startNewGame() }
        vm.restoreGame(com.apocalypse03.game2048.ui.game.ShowcaseBoard, score = 7777, best = 8888)
        rule.setApp(vm)
        rule.onNodeWithContentDescription("Palette").performClick()
        rule.waitForIdle()
        val nodes = listOf(
            rule.onNodeWithText("7777"),
            rule.onNodeWithText("Best 8888"),
            rule.onNodeWithContentDescription("Elapsed time", substring = true),
        )
        nodes.forEach { n ->
            n.assertIsDisplayed()
            val top = topRp(n.fetchSemanticsNode())
            assertTrue("header node top $top rp above pill bottom 174", top >= 174f - 1f)
        }
    }
}
