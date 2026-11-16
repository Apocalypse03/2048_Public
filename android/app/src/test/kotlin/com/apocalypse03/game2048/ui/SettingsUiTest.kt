package com.apocalypse03.game2048.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import com.apocalypse03.game2048.ui.components.RefFrame
import com.apocalypse03.game2048.ui.settings.FONT_SLIDER_TAG
import com.apocalypse03.game2048.ui.settings.INTENSITY_SLIDER_TAG
import com.apocalypse03.game2048.ui.settings.INTENSITY_TEXT
import com.apocalypse03.game2048.ui.settings.SPEED_SLIDER_TAG
import com.apocalypse03.game2048.ui.settings.SPEED_TEXT
import com.apocalypse03.game2048.ui.settings.SettingsScreen
import com.apocalypse03.game2048.ui.settings.snapToStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Settings screen alone (DESIGN_SPEC section 15): texts, sliders, toggle, disabled intensity. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class SettingsScreenUiTest {

    @get:Rule
    val rule = createComposeRule()

    private var speed by mutableIntStateOf(100)
    private var vibration by mutableStateOf(true)
    private var ms by mutableIntStateOf(50)
    private var font by mutableIntStateOf(100)
    private val fontCalls = mutableListOf<Int>()
    private val speedCalls = mutableListOf<Int>()
    private val vibrationCalls = mutableListOf<Boolean>()
    private val msCalls = mutableListOf<Int>()

    /** Ordered log of intensity changes ("ms:N") and previews ("preview"). */
    private val log = mutableListOf<String>()
    private var backs = 0

    private fun show(vibrationOn: Boolean = true) {
        vibration = vibrationOn
        rule.setContent {
            RefFrame {
                SettingsScreen(
                    speed = speed,
                    onSpeed = { speedCalls += it; speed = it },
                    vibration = vibration,
                    onVibration = { vibrationCalls += it; vibration = it },
                    vibrationMs = ms,
                    onVibrationMs = { msCalls += it; log += "ms:$it"; ms = it },
                    onPreviewVibration = { log += "preview" },
                    fontScale = font,
                    onFontScale = { fontCalls += it; font = it },
                    onBack = { backs++ },
                )
            }
        }
        rule.waitForIdle()
    }

    private fun speedSlider() = rule.onNodeWithContentDescription("Animation speed")
    private fun intensitySlider() = rule.onNodeWithContentDescription("Vibration intensity")

    private fun SemanticsNodeInteraction.setProgress(v: Float) {
        performSemanticsAction(SemanticsActions.SetProgress) { it(v) }
        rule.waitForIdle()
    }

    @Test
    fun textsPresent() {
        show()
        listOf(
            "Animation speeds", SPEED_TEXT, "0%", "150%",
            "Vibration", "ON", "Vibration intensity", INTENSITY_TEXT, "50 ms", "5 ms", "200 ms",
            "Font Size:", "70%", "130%",
        ).forEach { rule.onNodeWithText(it, useUnmergedTree = true).assertExists() }
        // Speed value and font size value both read 100 %.
        rule.onAllNodesWithText("100%", useUnmergedTree = true).assertCountEquals(2)
        rule.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun sliderSemanticsRangeAndState() {
        show()
        val info = speedSlider().fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(100f, info.current)
        assertEquals(0f..150f, info.range)
        assertEquals(29, info.steps)
        assertEquals("100%", speedSlider().fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        val ms = intensitySlider().fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(5f..200f, ms.range)
        assertEquals(194, ms.steps)
    }

    @Test
    fun setProgressSnapsToStepAndUpdatesValueText() {
        show()
        speedSlider().setProgress(73f)
        assertEquals(listOf(75), speedCalls)
        rule.onNodeWithText("75%").assertExists()
        speedSlider().setProgress(76f)
        assertEquals(listOf(75), speedCalls) // snaps back to 75: no change, no call
        intensitySlider().setProgress(68.6f)
        assertEquals(listOf(69), msCalls)
        rule.onNodeWithText("69 ms").assertExists()
    }

    @Test
    fun fontSliderScrollsIntoViewAndSnapsToStep() {
        show()
        val slider = rule.onNodeWithTag(FONT_SLIDER_TAG)
        slider.performScrollTo()
        slider.assertIsDisplayed()
        rule.onNodeWithText("Font Size:", useUnmergedTree = true).assertExists()
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(73f) }
        rule.waitForIdle()
        assertEquals(listOf(75), fontCalls)
        rule.onNodeWithText("75%").assertExists()
        // Fonts ignore the speed and vibration sliders.
        assertTrue(speedCalls.isEmpty())
        assertTrue(msCalls.isEmpty())
    }

    @Test
    fun valuesClamp() {
        show()
        speedSlider().setProgress(500f)
        speedSlider().setProgress(-20f)
        assertEquals(listOf(150, 0), speedCalls)
        intensitySlider().setProgress(1f)
        intensitySlider().setProgress(1000f)
        assertEquals(listOf(5, 200), msCalls)
        assertEquals(0, snapToStep(-3f, 0..150, 5))
        assertEquals(150, snapToStep(152.4f, 0..150, 5))
        assertEquals(10, snapToStep(12.4f, 0..150, 5))
    }

    @Test
    fun tapAndDragOnTrack() {
        show()
        val slider = rule.onNodeWithTag(SPEED_SLIDER_TAG)
        // Tap the track centre: half way = 75 %, released once.
        slider.performTouchInput { click(center) }
        rule.waitForIdle()
        assertEquals(listOf(75), speedCalls)
        // Drag from the left end to the right end: live updates, ending clamped at 150.
        // Separate injections, so each move reaches the slider as its own event.
        slider.performTouchInput { down(Offset(1f, centerY)) }
        rule.waitForIdle()
        slider.performTouchInput { moveTo(Offset(width * 0.5f, centerY)) }
        rule.waitForIdle()
        slider.performTouchInput { moveTo(Offset(width - 1f, centerY)) }
        rule.waitForIdle()
        slider.performTouchInput { up() }
        rule.waitForIdle()
        assertEquals(listOf(75, 0, 75, 150), speedCalls)
        rule.onAllNodesWithText("150%").assertCountEquals(2) // value text and range label
    }

    @Test
    fun releaseAndTapPreviewAfterTheNewValue() {
        show()
        val slider = rule.onNodeWithTag(INTENSITY_SLIDER_TAG)
        slider.performTouchInput {
            down(center)
            moveTo(Offset(width * 0.75f, centerY))
            moveTo(Offset(width - 1f, centerY))
        }
        rule.waitForIdle()
        assertFalse("no preview before release", "preview" in log)
        slider.performTouchInput { up() }
        rule.waitForIdle()
        assertEquals("preview", log.last())
        assertEquals(1, log.count { it == "preview" })
        assertEquals("ms:200", log[log.size - 2])

        log.clear()
        slider.performTouchInput { click(Offset(1f, centerY)) }
        rule.waitForIdle()
        assertEquals(listOf("ms:5", "preview"), log)

        log.clear()
        intensitySlider().setProgress(50f)
        assertEquals(listOf("ms:50", "preview"), log)

        // The speed slider never previews.
        log.clear()
        rule.onNodeWithTag(SPEED_SLIDER_TAG).performTouchInput { click(center) }
        rule.waitForIdle()
        assertTrue(log.isEmpty())
    }

    @Test
    fun toggleFlipsAndDoesNotPreview() {
        show()
        val toggle = rule.onNodeWithContentDescription("Vibration")
        toggle.assertIsOn()
        toggle.performClick()
        rule.waitForIdle()
        assertEquals(listOf(false), vibrationCalls)
        toggle.assertIsOff()
        rule.onNodeWithText("OFF", useUnmergedTree = true).assertExists()
        toggle.performClick()
        rule.waitForIdle()
        assertEquals(listOf(false, true), vibrationCalls)
        toggle.assertIsOn()
        assertTrue("toggle must not call the preview", log.isEmpty())
    }

    @Test
    fun intensityDisabledWhileVibrationOff() {
        show(vibrationOn = false)
        intensitySlider().assertIsNotEnabled()
        assertFalse(SemanticsActions.SetProgress in intensitySlider().fetchSemanticsNode().config)
        rule.onNodeWithTag(INTENSITY_SLIDER_TAG).performTouchInput { click(Offset(1f, centerY)) }
        rule.waitForIdle()
        assertTrue(msCalls.isEmpty())
        assertTrue(log.isEmpty())
        speedSlider().assertIsEnabled()

        rule.onNodeWithContentDescription("Vibration").performClick()
        rule.waitForIdle()
        intensitySlider().assertIsEnabled()
    }
}

/** Settings through the real app: Home gear icon, speed change saved in the ViewModel, back. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class SettingsFlowTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun homeToSettingsChangeSpeedAndBack() {
        val vm = AppViewModel()
        rule.setApp(vm)
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.mainClock.advanceTimeBy(500) // press highlight delay before the action (section 14)
        rule.waitForIdle()
        assertEquals(Screen.SETTINGS, vm.screen)
        rule.onNodeWithText("Animation speeds").assertExists()

        rule.onNodeWithContentDescription("Animation speed")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(42f) }
        rule.waitForIdle()
        assertEquals(40, vm.animSpeed)
        rule.onNodeWithText("40%").assertExists()

        rule.onNodeWithContentDescription("Vibration").performClick()
        rule.waitForIdle()
        assertFalse(vm.vibration)
        rule.onNodeWithContentDescription("Vibration intensity").assertIsNotEnabled()

        rule.onNodeWithContentDescription("Back").performClick()
        rule.waitForIdle()
        assertEquals(Screen.HOME, vm.screen)
        rule.onNodeWithText("New Game").assertExists()
    }
}

/** Settings texts fit at the 1.3 font-scale clamp and do not overlap (DESIGN_SPEC sections 1 and 15). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class SettingsFontScaleTest {

    @get:Rule
    val rule = createComposeRule()

    private fun SemanticsNodeInteraction.layout(): TextLayoutResult {
        val out = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(out)
        return out.single()
    }

    private fun text(t: String) = rule.onNodeWithText(t, useUnmergedTree = true)

    @Test
    fun textsFitAndDoNotOverlapAt130() {
        val vm = AppViewModel()
        vm.openSettings()
        rule.setAppWithFontScale(vm, mutableFloatStateOf(1.3f))
        val speedBounds = rule.onNodeWithTag(SPEED_SLIDER_TAG).fetchSemanticsNode().boundsInRoot
        val pxPerRp = speedBounds.width / 650f
        val frameLeft = speedBounds.left - 169 * pxPerRp
        val frameTop = speedBounds.center.y - 572 * pxPerRp
        val frame = Rect(frameLeft, frameTop, frameLeft + 989 * pxPerRp, frameTop + 2048 * pxPerRp)

        // Descriptions: unclipped, every line within the 700 rp box centred on x 494.
        listOf(SPEED_TEXT, INTENSITY_TEXT).forEach { d ->
            val l = text(d).layout()
            assertTrue("'$d' clipped", l.multiParagraph.height <= l.size.height + 0.5f)
            val b = text(d).fetchSemanticsNode().boundsInRoot
            println("settings description: ${l.lineCount} lines, $b, box ${700 * pxPerRp} px")
            assertTrue("'$d' wider than 700 rp", b.width <= 700 * pxPerRp + 0.5f)
            assertEquals(frameLeft + 494 * pxPerRp, b.center.x, 1f)
            (0 until l.lineCount).forEach { assertTrue(l.getLineRight(it) - l.getLineLeft(it) <= 700 * pxPerRp + 0.5f) }
        }
        // Single-line labels stay on one line, unclipped. "100%" occurs twice (speed and font value):
        // the first node in tree order is the speed value.
        val speedValue = rule.onAllNodesWithText("100%", useUnmergedTree = true)[0]
        listOf(
            "Animation speeds", "0%", "150%", "Vibration", "ON",
            "Vibration intensity", "50 ms", "5 ms", "200 ms",
        ).forEach { t ->
            val l = text(t).layout()
            assertEquals(1, l.lineCount)
            assertTrue("'$t' clipped", l.getLineRight(0) - l.getLineLeft(0) <= l.size.width + 0.5f)
        }
        speedValue.layout().let { l ->
            assertEquals(1, l.lineCount)
            assertTrue("speed value clipped", l.getLineRight(0) - l.getLineLeft(0) <= l.size.width + 0.5f)
        }

        // Top to bottom, nothing overlaps and everything stays in the frame.
        val stack: List<Pair<String, Rect>> = listOf(
            "Animation speeds", SPEED_TEXT,
        ).map { it to text(it).fetchSemanticsNode().boundsInRoot } +
            ("speed value" to speedValue.fetchSemanticsNode().boundsInRoot) +
            ("speed slider" to speedBounds) +
            listOf("0%", "Vibration").map { it to text(it).fetchSemanticsNode().boundsInRoot } +
            ("toggle" to rule.onNodeWithContentDescription("Vibration").fetchSemanticsNode().boundsInRoot) +
            listOf("Vibration intensity", INTENSITY_TEXT, "50 ms").map { it to text(it).fetchSemanticsNode().boundsInRoot } +
            ("intensity slider" to rule.onNodeWithTag(INTENSITY_SLIDER_TAG).fetchSemanticsNode().boundsInRoot) +
            ("5 ms" to text("5 ms").fetchSemanticsNode().boundsInRoot)
        stack.forEach { (name, b) ->
            assertTrue("'$name' outside the frame: $b vs $frame", b.top >= frame.top - 0.5f && b.bottom <= frame.bottom + 0.5f)
            assertTrue("'$name' outside the frame: $b vs $frame", b.left >= frame.left - 0.5f && b.right <= frame.right + 0.5f)
        }
        stack.zipWithNext().forEach { (a, b) ->
            println("settings gap '${a.first}' -> '${b.first}': ${(b.second.top - a.second.bottom) / pxPerRp} rp")
            assertTrue("'${a.first}' overlaps '${b.first}'", a.second.bottom <= b.second.top + 0.5f)
        }
        // Range labels on one row do not touch each other.
        assertTrue(text("0%").fetchSemanticsNode().boundsInRoot.right < text("150%").fetchSemanticsNode().boundsInRoot.left)
        assertTrue(text("5 ms").fetchSemanticsNode().boundsInRoot.right < text("200 ms").fetchSemanticsNode().boundsInRoot.left)
    }
}
