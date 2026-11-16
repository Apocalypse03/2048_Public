package com.apocalypse03.game2048.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import com.apocalypse03.game2048.R
import com.apocalypse03.game2048.ui.theme.AppThemes
import com.apocalypse03.game2048.ui.theme.LocalAppTheme
import com.apocalypse03.game2048.ui.theme.ThemeSpec
import com.apocalypse03.game2048.ui.theme.getActiveVariant
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

private const val BAR_X = 27f
private const val BAR_Y = 35f
private const val BAR_W = 848f
private const val BAR_H = 139f
/** Swatch centre y: the pill's vertical centre (35 + 139 / 2). */
private const val SWATCH_Y = BAR_Y + BAR_H / 2f

/** Right edge of the visible strip: the open palette toggle's left edge (893 - 61). */
private const val VIEW_RIGHT = 832f

/** Swatches visible at once. */
const val THEME_BAR_VISIBLE = 7

/** Uniform swatch pitch in rp: (832 - 27) / 7 = 115. */
const val THEME_BAR_PITCH = (VIEW_RIGHT - BAR_X) / THEME_BAR_VISIBLE

/** First swatch centre at scroll 0 (DESIGN_SPEC section 8): 84.5, equal margins at both ends. */
const val THEME_BAR_FIRST_X = BAR_X + THEME_BAR_PITCH / 2f

/**
 * Scrolling strip: one pitch-wide slot per swatch, slot 0 starting at the pill's left edge. The
 * viewport spans the pill's left edge to the toggle's left edge, so exactly 7 slots are in view.
 */
private const val SLOT0_X = BAR_X
private const val VIEW_W = VIEW_RIGHT - BAR_X

/** First visible swatch when the bar opens: the selected one in view, snapped to whole swatches. */
fun themeBarFirstVisible(selectedIndex: Int, count: Int): Int =
    (selectedIndex - THEME_BAR_VISIBLE / 2).coerceIn(0, maxOf(0, count - THEME_BAR_VISIBLE))

/** Palette button shared by both screens; becomes an accent circle while the bar is open. */
@Composable
fun RefScope.PaletteToggle(open: Boolean, onClick: () -> Unit) {
    val theme = LocalAppTheme.current
    val cx = if (open) 893f else 898f
    val cy = if (open) 97f else 92f
    val box = if (open) 122 else 69
    TapTarget(cx, cy, box, box, "Palette", onClick) {
        if (open) {
            Box(
                Modifier.size(122.rp).background(theme.accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) { RefIcon(R.drawable.ic_palette, 70, theme.onAccent) }
        } else {
            RefIcon(R.drawable.ic_palette, 83, theme.onBackground)
        }
    }
}

/**
 * The theme bar overlay pill (DESIGN_SPEC section 8): all [AppThemes.all] swatches, each in the
 * active variant ([getActiveVariant]), at a uniform
 * pitch (115 rp) in a horizontally scrollable strip, 7 visible, centred on y 104.5, snapping to whole swatches.
 * The pill and the strip share a transparent container that starts at the frame top and is tall
 * enough for the 48 dp swatch hit areas, so the 139 rp pill never shrinks them on small frames.
 * Visuals and hit areas are separate: the strip holds only the (invisible) hit areas, and one
 * canvas draws the swatches at the strip's scroll position, clipped to the pill shape.
 */
@Composable
fun RefScope.ThemeBar(selectedId: Int, onSelect: (Int) -> Unit) {
    val theme = LocalAppTheme.current
    val themes = AppThemes.all
    val variant = getActiveVariant()
    val density = LocalDensity.current
    val pill = RoundedCornerShape(percent = 50)
    val hitH = maxOf(86.rp, MIN_TOUCH)
    val hitW = minOf(hitH, THEME_BAR_PITCH.rp)
    val hitHalfRp = hitH.value / scale / 2f
    val containerH = maxOf(BAR_Y + BAR_H, SWATCH_Y + hitHalfRp)
    val containerW = maxOf(BAR_W, VIEW_W)

    val pitchPx = with(density) { THEME_BAR_PITCH.rp.toPx() }
    // Never wider than the pitch in px, so rounding cannot make neighbouring areas overlap.
    val hitWPx = minOf(with(density) { hitW.roundToPx() }, pitchPx.toInt())
    val hitHPx = with(density) { hitH.roundToPx() }
    val slot0Px = with(density) { (SLOT0_X - BAR_X).rp.toPx() }
    val swatchYPx = with(density) { SWATCH_Y.rp.toPx() }
    // Left edge of hit area i inside the strip content; the visual is centred on the same point.
    fun hitLeft(i: Int): Int = (slot0Px + (i + 0.5f) * pitchPx - hitWPx / 2f).roundToInt()

    val selectedIndex = themes.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
    val scroll = rememberScrollState((themeBarFirstVisible(selectedIndex, themes.size) * pitchPx).roundToInt())
    val snap = remember(scroll, pitchPx) { PitchSnap(scroll, pitchPx) }
    val fling = rememberSnapFlingBehavior(snap)

    Box(
        Modifier
            .offset(BAR_X.rp, 0.rp)
            .size(containerW.rp, containerH.rp),
    ) {
        Box(
            Modifier
                .offset(0.rp, BAR_Y.rp)
                .size(BAR_W.rp, BAR_H.rp)
                .background(theme.background, pill),
        )
        val k = scale
        Canvas(
            Modifier
                .offset(0.rp, BAR_Y.rp)
                .size(BAR_W.rp, BAR_H.rp),
        ) {
            fun px(rp: Float) = (rp * k).dp.toPx()
            val clip = Path().apply { addOutline(pill.createOutline(size, layoutDirection, this@Canvas)) }
            val r = px(43f)
            val cy = swatchYPx - px(BAR_Y)
            val shift = hitWPx / 2f - scroll.value
            // Drawing is clipped to the pill and to the toggle's left edge (x 832).
            clipPath(clip) {
              clipRect(right = px(VIEW_W)) {
                themes.forEachIndexed { i, spec ->
                    val t = spec.variant(variant)
                    val cx = shift + hitLeft(i)
                    if (cx + r < 0f || cx - r > size.width) return@forEachIndexed
                    val c = Offset(cx, cy)
                    drawCircle(t.background, radius = r, center = c)
                    drawCircle(theme.outline, radius = px(42f), center = c, style = Stroke(px(2f)))
                    if (t.id == selectedId) {
                        drawCircle(t.accent, radius = px(39f), center = c, style = Stroke(px(8f)))
                    } else {
                        drawCircle(t.accent, radius = px(19f), center = c)
                    }
                }
              }
            }
        }
        Box(
            Modifier
                .offset(0.rp, BAR_Y.rp)
                .size(BAR_W.rp, BAR_H.rp)
                .border(3.rp, theme.outline, pill),
        )
        Layout(
            modifier = Modifier
                .size(VIEW_W.rp, containerH.rp)
                .horizontalScroll(scroll, flingBehavior = fling),
            content = {
                themes.forEach { t ->
                    SwatchHitArea(t, selected = t.id == selectedId, onClick = { onSelect(t.id) })
                }
            },
        ) { measurables, constraints ->
            val fixed = Constraints.fixed(hitWPx, hitHPx)
            val placeables = measurables.map { it.measure(fixed) }
            val width = (slot0Px + themes.size * pitchPx).roundToInt()
            val height = constraints.constrainHeight(with(density) { containerH.rp.roundToPx() })
            val top = (swatchYPx - hitHPx / 2f).roundToInt()
            layout(width, height) {
                placeables.forEachIndexed { i, p -> p.place(hitLeft(i), top) }
            }
        }
    }
}

/** Snaps the strip to whole swatches: a fling or drag release ends on a multiple of the pitch. */
private class PitchSnap(private val scroll: ScrollState, private val pitchPx: Float) : SnapLayoutInfoProvider {
    override fun calculateSnapOffset(velocity: Float): Float {
        val cur = scroll.value.toFloat()
        val pos = cur / pitchPx
        val lower = floor(pos + 1e-3f) * pitchPx
        val upper = ceil(pos - 1e-3f) * pitchPx
        val target = when {
            velocity > 0f -> upper
            velocity < 0f -> lower
            cur - lower <= upper - cur -> lower
            else -> upper
        }
        return target.coerceIn(0f, scroll.maxValue.toFloat()) - cur
    }
}

/**
 * Invisible hit area of one swatch: width min(max(86 rp, 48 dp), pitch) so neighbouring areas
 * never overlap, height max(86 rp, 48 dp) (DESIGN_SPEC section 8). Size comes from the strip.
 */
@Composable
private fun SwatchHitArea(t: ThemeSpec, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .semantics { contentDescription = "Theme ${t.name}" }
            .selectable(
                selected = selected,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
    )
}
