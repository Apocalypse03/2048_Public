package com.apocalypse03.game2048.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.apocalypse03.game2048.ui.theme.LocalAppTheme

const val REF_W = 989f
const val REF_H = 2048f
internal val MIN_TOUCH = 48.dp

/** UI labels follow the system font scale, clamped to this range (DESIGN_SPEC section 1). */
const val MIN_LABEL_FONT_SCALE = 1.0f
const val MAX_LABEL_FONT_SCALE = 1.3f

/** Lowest user Font Size factor (the Settings slider's 70 %); see [RefScope.labelFontScale]. */
const val MIN_USER_FONT_SCALE = 0.7f

/** Reference-pixel scale: 1 rp = [scale] dp (DESIGN_SPEC section 1). */
@Immutable
class RefScope(val scale: Float, private val density: Density, private val userFontScale: Float = 1f) {
    val Number.rp: Dp get() = (this.toFloat() * scale).dp

    /** rp to sp with the system font scale cancelled: text tied to the frame only (tile numbers). */
    val Number.rsp: TextUnit get() = with(density) { (this@rsp.toFloat() * scale).dp.toSp() }

    /**
     * System font scale clamped to [MIN_LABEL_FONT_SCALE]..[MAX_LABEL_FONT_SCALE], times the user
     * Font Size setting ([userFontScale], 0.7..1.3), total capped at [MAX_LABEL_FONT_SCALE] so
     * labels still fit their boxes.
     */
    val labelFontScale: Float
        get() = (density.fontScale.coerceIn(MIN_LABEL_FONT_SCALE, MAX_LABEL_FONT_SCALE) * userFontScale)
            .coerceIn(MIN_USER_FONT_SCALE, MAX_LABEL_FONT_SCALE)

    /** rp to sp for UI labels: [rsp] times [labelFontScale]. */
    val Number.labelSp: TextUnit get() = (this.toFloat() * labelFontScale).rsp

    /** Absolute placement of a box given its centre and size, in rp. */
    fun refBox(cx: Number, cy: Number, w: Number, h: Number): Modifier =
        Modifier
            .offset((cx.toFloat() - w.toFloat() / 2f).rp, (cy.toFloat() - h.toFloat() / 2f).rp)
            .size(w.rp, h.rp)
}

/**
 * Edge-to-edge themed background plus a centred 989x2048 rp frame inside the safe area.
 * [fontScale] is the user Font Size factor (1.0 = 100 %), applied to UI labels only; see
 * [RefScope.labelFontScale].
 * [onScrimTap], when non-null, adds a transparent full-window layer above [content] and
 * below [overlay]; a tap on it (anywhere in the window) calls the lambda.
 * [modal], when non-null, dims the whole window above everything else (a tap on the dim calls
 * [onModalDismiss]) and draws its content in the frame on top.
 * Accessibility: the scrims are hidden (Back and the modal's own buttons dismiss), and while
 * [modal] is shown the layers beneath it are hidden too, so only the modal is reachable.
 */
@Composable
fun RefFrame(
    modifier: Modifier = Modifier,
    fontScale: Float = 1f,
    onScrimTap: (() -> Unit)? = null,
    overlay: @Composable RefScope.() -> Unit = {},
    modal: (@Composable RefScope.() -> Unit)? = null,
    onModalDismiss: () -> Unit = {},
    content: @Composable RefScope.() -> Unit,
) {
    val theme = LocalAppTheme.current
    val beneath = if (modal != null) Modifier.clearAndSetSemantics {} else Modifier
    Box(modifier.fillMaxSize().background(theme.background)) {
        FrameLayer(content, beneath, fontScale)
        if (onScrimTap != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clearAndSetSemantics {}
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onScrimTap,
                    ),
            )
        }
        FrameLayer(overlay, beneath, fontScale)
        if (modal != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clearAndSetSemantics {}
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onModalDismiss,
                    ),
            )
            FrameLayer(modal, Modifier, fontScale)
        }
    }
}

@Composable
private fun FrameLayer(
    content: @Composable RefScope.() -> Unit,
    modifier: Modifier = Modifier,
    fontScale: Float = 1f,
) {
    BoxWithConstraints(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        val density = LocalDensity.current
        val s = minOf(maxWidth.value / REF_W, maxHeight.value / REF_H)
        val scope = remember(s, density, fontScale) { RefScope(s, density, fontScale) }
        Box(
            Modifier
                .align(Alignment.Center)
                .size((REF_W * s).dp, (REF_H * s).dp),
        ) { scope.content() }
    }
}

/**
 * Clickable area centred on (cx, cy) rp, at least 48 dp square; [content] is centred in it
 * and keeps its own (possibly smaller) visual size. When [enabled] is false it ignores taps.
 * [role] defaults to a button; null sets no role (e.g. the timer row, which reads as the elapsed time).
 */
@Composable
fun RefScope.TapTarget(
    cx: Number,
    cy: Number,
    w: Number,
    h: Number,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    role: Role? = Role.Button,
    content: @Composable BoxScope.() -> Unit,
) {
    val width = maxOf(w.rp, MIN_TOUCH)
    val height = maxOf(h.rp, MIN_TOUCH)
    Box(
        modifier
            .offset(cx.rp - width / 2, cy.rp - height / 2)
            .size(width, height)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                role = role,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
fun RefScope.RefIcon(@DrawableRes id: Int, sizeRp: Number, tint: Color) {
    Icon(painterResource(id), contentDescription = null, tint = tint, modifier = Modifier.size(sizeRp.rp))
}

/**
 * Text sized in rp, single-line unless [singleLine] is false (then it wraps, with optional [textAlign]). With [followFontScale] (UI labels) the size is multiplied by the
 * clamped system font scale; tile numbers pass false and stay a fixed fraction of the cell.
 */
@Composable
fun RefScope.RefText(
    text: String,
    sizeRp: Number,
    color: Color,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.Medium,
    followFontScale: Boolean = true,
    singleLine: Boolean = true,
    textAlign: TextAlign? = null,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = if (followFontScale) sizeRp.labelSp else sizeRp.rsp,
        fontWeight = weight,
        fontFamily = FontFamily.Default,
        textAlign = textAlign,
        maxLines = if (singleLine) 1 else Int.MAX_VALUE,
        softWrap = !singleLine,
    )
}
