package com.apocalypse03.game2048.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import com.apocalypse03.game2048.R
import com.apocalypse03.game2048.ui.components.MIN_TOUCH
import com.apocalypse03.game2048.ui.components.REF_H
import com.apocalypse03.game2048.ui.components.REF_W
import com.apocalypse03.game2048.ui.components.RefIcon
import com.apocalypse03.game2048.ui.components.RefScope
import com.apocalypse03.game2048.ui.components.RefText
import com.apocalypse03.game2048.ui.components.TapTarget
import com.apocalypse03.game2048.ui.data.SettingsStore
import com.apocalypse03.game2048.ui.theme.LocalAppTheme
import kotlin.math.roundToInt

const val SETTINGS_TAG = "settings"
const val SPEED_SLIDER_TAG = "settings_speed_slider"
const val INTENSITY_SLIDER_TAG = "settings_intensity_slider"
const val VIBRATION_TOGGLE_TAG = "settings_vibration_toggle"
const val FONT_SLIDER_TAG = "settings_font_slider"
const val SETTINGS_SCROLL_TAG = "settings_scroll"

const val SPEED_TEXT =
    "Increase the percentage for a more relaxing experience. Set it to 0% to disable most animations for a faster pace."
const val INTENSITY_TEXT = "Adjust the vibration intensity when tiles merge."

/** Speed range 0..150 %, step 5; intensity range 5..200 ms, step 1; font size 70..130 %, step 5 (DESIGN_SPEC section 15). */
private val SPEED_RANGE = 0..150
private const val SPEED_STEP = 5
private val INTENSITY_RANGE = 5..200
private const val INTENSITY_STEP = 1
private val FONT_RANGE = SettingsStore.FONT_SCALE_MIN..SettingsStore.FONT_SCALE_MAX
private const val FONT_STEP = SettingsStore.FONT_SCALE_STEP

/** Everything below the pinned back arrow scrolls: the viewport starts at y 160 rp; content is 2500 rp tall from the frame top. */
private const val SCROLL_TOP = 160
private const val CONTENT_H = 2500

/** Slider geometry in rp (section 15): track x 169..819, thumb outer diameter 106. */
private const val TRACK_LEFT = 169
private const val TRACK_RIGHT = 819
private const val TRACK_W = TRACK_RIGHT - TRACK_LEFT
private const val TRACK_THICK = 11
private const val THUMB_D = 106
private const val THUMB_STROKE = 8

/** Dim text (onBackground at 70 %) and the intensity block while vibration is off (40 %). */
private const val DIM_ALPHA = 0.70f
private const val OFF_ALPHA = 0.40f

/**
 * Settings screen (DESIGN_SPEC section 15); the palette button lives in the app root.
 * [speed] is the animation speed in percent (0..150, step 5), [vibrationMs] the pulse length
 * (5..200 ms, step 1). Both sliders call their setter live while dragging; releasing the
 * intensity slider (or a tap on its track, or an accessibility set) calls [onVibrationMs] first
 * and then [onPreviewVibration]. The toggle only calls [onVibration]: the ViewModel plays the
 * preview pulse itself when vibration turns on. While vibration is off the intensity block
 * draws at 40 % alpha and its slider is disabled.
 * [fontScale] is the user Font Size in percent (70..130, step 5), set through [onFontScale]; it
 * scales UI labels only. The back arrow stays pinned; the rest scrolls vertically below it.
 */
@Composable
fun RefScope.SettingsScreen(
    speed: Int,
    onSpeed: (Int) -> Unit,
    vibration: Boolean,
    onVibration: (Boolean) -> Unit,
    vibrationMs: Int,
    onVibrationMs: (Int) -> Unit,
    onPreviewVibration: () -> Unit,
    fontScale: Int,
    onFontScale: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val theme = LocalAppTheme.current
    val dim = theme.onBackground.copy(alpha = DIM_ALPHA)
    Box(Modifier.fillMaxSize().testTag(SETTINGS_TAG)) {
        TapTarget(89, 93, 45, 45, "Back", onBack) { RefIcon(R.drawable.ic_arrow_back, 67, theme.onBackground) }

        Box(
            Modifier
                .offset(y = SCROLL_TOP.rp)
                .size(REF_W.rp, (REF_H - SCROLL_TOP).rp)
                .testTag(SETTINGS_SCROLL_TAG)
                .verticalScroll(rememberScrollState()),
        ) {
            Box(Modifier.size(REF_W.rp, (CONTENT_H - SCROLL_TOP).rp)) {
                // Children keep their frame-relative cy values: shift the whole layer up by SCROLL_TOP.
                Box(Modifier.fillMaxSize().offset(y = (-SCROLL_TOP).rp)) {
                    // Animation speed.
                    Label("Animation speeds", 248, 32, theme.onBackground)
                    Paragraph(SPEED_TEXT, 351, dim)
                    Label("$speed%", 455, 30, theme.onBackground)
                    RefSlider(
                        value = speed,
                        range = SPEED_RANGE,
                        step = SPEED_STEP,
                        centreY = 572,
                        description = "Animation speed",
                        valueText = "$speed%",
                        onValueChange = onSpeed,
                        onRelease = {},
                        modifier = Modifier.testTag(SPEED_SLIDER_TAG),
                    )
                    RangeLabels("0%", "150%", 689, dim)

                    // Vibration switch.
                    Label("Vibration", 935, 32, theme.onBackground)
                    Box(
                        refBox(494, 1079, 188, 188)
                            .testTag(VIBRATION_TOGGLE_TAG)
                            .clip(CircleShape)
                            .then(
                                if (vibration) {
                                    Modifier.background(theme.accent, CircleShape)
                                } else {
                                    Modifier.border(3.rp, theme.outline, CircleShape)
                                },
                            )
                            .toggleable(
                                value = vibration,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Switch,
                                onValueChange = onVibration,
                            )
                            .semantics { contentDescription = "Vibration" },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (vibration) RefText("ON", 45, theme.onAccent) else RefText("OFF", 45, theme.onBackground)
                    }

                    // Vibration intensity: dimmed and disabled while vibration is off.
                    Box(Modifier.fillMaxSize().alpha(if (vibration) 1f else OFF_ALPHA)) {
                        Label("Vibration intensity", 1403, 32, theme.onBackground)
                        Paragraph(INTENSITY_TEXT, 1488, dim)
                        Label("$vibrationMs ms", 1573, 30, theme.onBackground)
                        RefSlider(
                            value = vibrationMs,
                            range = INTENSITY_RANGE,
                            step = INTENSITY_STEP,
                            centreY = 1690,
                            description = "Vibration intensity",
                            valueText = "$vibrationMs ms",
                            onValueChange = onVibrationMs,
                            onRelease = onPreviewVibration,
                            enabled = vibration,
                            modifier = Modifier.testTag(INTENSITY_SLIDER_TAG),
                        )
                        RangeLabels("5 ms", "200 ms", 1807, dim)
                    }

                    // Font size: scales UI labels only (tile numbers and the game logo keep their size).
                    Label("Font Size:", 2053, 32, theme.onBackground)
                    Label("$fontScale%", 2138, 30, theme.onBackground)
                    RefSlider(
                        value = fontScale,
                        range = FONT_RANGE,
                        step = FONT_STEP,
                        centreY = 2255,
                        description = "Font size",
                        valueText = "$fontScale%",
                        onValueChange = onFontScale,
                        onRelease = {},
                        modifier = Modifier.testTag(FONT_SLIDER_TAG),
                    )
                    RangeLabels("70%", "130%", 2372, dim)
                }
            }
        }
    }
}

/** Single-line label centred on (494, [cy]); the 120 rp tall box leaves room for font scale 1.3. */
@Composable
private fun RefScope.Label(text: String, cy: Int, sizeRp: Int, color: Color) {
    Box(refBox(494, cy, 900, 120), contentAlignment = Alignment.Center) { RefText(text, sizeRp, color) }
}

/** Wrapping text, size 28, in a 700 rp wide box; the text block is vertically centred on [cy]. */
@Composable
private fun RefScope.Paragraph(text: String, cy: Int, color: Color) {
    Box(refBox(494, cy, 700, 400), contentAlignment = Alignment.Center) {
        RefText(text, 28, color, singleLine = false, textAlign = TextAlign.Center)
    }
}

/** Range labels: [start] left-aligned at the track's left end, [end] right-aligned at its right end. */
@Composable
private fun RefScope.RangeLabels(start: String, end: String, cy: Int, color: Color) {
    Box(refBox(494, cy, TRACK_W, 120)) {
        Box(Modifier.align(Alignment.CenterStart)) { RefText(start, 28, color) }
        Box(Modifier.align(Alignment.CenterEnd)) { RefText(end, 28, color) }
    }
}

/** Snaps [raw] to [step] from the range start and clamps it to [range]. */
internal fun snapToStep(raw: Float, range: IntRange, step: Int): Int {
    val steps = ((raw - range.first) / step).roundToInt()
    return (range.first + steps * step).coerceIn(range.first, range.last)
}

/**
 * Slider drawn to section 15: an 11 rp round-cap track over x 169..819 at [centreY] (`accent`
 * left of the thumb, `outline` right of it) and a hollow ring thumb (outer diameter 106, stroke
 * 8 `outline`, filled `background`). The thumb centre travels from 53 rp inside each track end,
 * so the thumb never leaves the track (as in the reference). Pressing anywhere on the track
 * (thumb included) jumps to that point and dragging follows the finger; [onValueChange] is called
 * live with the snapped, clamped value whenever it changes, and [onRelease] once when the finger
 * lifts. The touch area is the track width by max(106 rp, 48 dp).
 * Semantics: [description], range info with the step count, [valueText] as the state, and a
 * set-progress action (snapped and clamped, then [onRelease]); disabled when [enabled] is false.
 */
@Composable
private fun RefScope.RefSlider(
    value: Int,
    range: IntRange,
    step: Int,
    centreY: Int,
    description: String,
    valueText: String,
    onValueChange: (Int) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val theme = LocalAppTheme.current
    val current by rememberUpdatedState(value)
    val change by rememberUpdatedState(onValueChange)
    val release by rememberUpdatedState(onRelease)
    val height = maxOf(THUMB_D.rp, MIN_TOUCH)
    val span = (range.last - range.first).toFloat()
    val fraction = ((value - range.first) / span).coerceIn(0f, 1f)

    Box(
        Modifier
            .offset(TRACK_LEFT.rp, centreY.rp - height / 2)
            .size(TRACK_W.rp, height)
            .then(modifier)
            .semantics {
                contentDescription = description
                stateDescription = valueText
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = value.toFloat(),
                    range = range.first.toFloat()..range.last.toFloat(),
                    steps = (range.last - range.first) / step - 1,
                )
                if (enabled) {
                    setProgress { target ->
                        val snapped = snapToStep(target, range, step)
                        if (snapped != current) change(snapped)
                        release()
                        true
                    }
                } else {
                    disabled()
                }
            }
            .pointerInput(enabled, range, step) {
                if (!enabled) return@pointerInput
                val inset = (THUMB_D / 2f).rp.toPx()
                fun update(x: Float) {
                    val travel = (size.width - 2 * inset).coerceAtLeast(1f)
                    val f = ((x - inset) / travel).coerceIn(0f, 1f)
                    val snapped = snapToStep(range.first + f * span, range, step)
                    if (snapped != current) change(snapped)
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    update(down.position.x)
                    while (true) {
                        val event = awaitPointerEvent()
                        val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!pointer.pressed) {
                            pointer.consume()
                            break
                        }
                        update(pointer.position.x)
                        pointer.consume()
                    }
                    release()
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cy = size.height / 2
            val thick = TRACK_THICK.rp.toPx()
            val cap = thick / 2
            val radius = (THUMB_D / 2f).rp.toPx()
            val thumbX = radius + fraction * (size.width - 2 * radius)
            drawLine(theme.outline, Offset(cap, cy), Offset(size.width - cap, cy), thick, StrokeCap.Round)
            drawLine(theme.accent, Offset(cap, cy), Offset(thumbX, cy), thick, StrokeCap.Round)
            val stroke = THUMB_STROKE.rp.toPx()
            drawCircle(theme.background, radius, Offset(thumbX, cy))
            drawCircle(theme.outline, radius - stroke / 2, Offset(thumbX, cy), style = Stroke(stroke))
        }
    }
}
