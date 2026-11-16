package com.apocalypse03.game2048.ui.home

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.apocalypse03.game2048.R
import com.apocalypse03.game2048.ui.GameMode
import com.apocalypse03.game2048.ui.components.Logo2048
import com.apocalypse03.game2048.ui.components.MIN_TOUCH
import com.apocalypse03.game2048.ui.components.PillButton
import com.apocalypse03.game2048.ui.components.RefFrame
import com.apocalypse03.game2048.ui.components.RefIcon
import com.apocalypse03.game2048.ui.components.RefScope
import com.apocalypse03.game2048.ui.components.RefText
import com.apocalypse03.game2048.ui.components.TapTarget
import com.apocalypse03.game2048.ui.theme.LocalAppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class NavItem(val x: Int, val icon: Int, val label: String, val box: Int, val onClick: () -> Unit)

/** Centre y of the bottom nav icons, rp. */
private const val NAV_Y = 1697

/** Nav press highlight timing (DESIGN_SPEC section 14), ms. */
private const val HIGHLIGHT_MIN_MS = 150L
private const val HIGHLIGHT_FADE_MS = 120
private const val NAV_ACTION_DELAY_MS = 120L

/**
 * Home screen content (DESIGN_SPEC section 6); the palette button lives in the app root.
 * [onSettings], [onResults] and [onInfo] are the gear, list and info nav icons; Shop only highlights.
 */
@Composable
fun RefScope.HomeScreen(
    mode: GameMode,
    onStepMode: (Int) -> Unit,
    onNewGame: () -> Unit,
    hasGameInProgress: Boolean,
    onResume: () -> Unit,
    onSettings: () -> Unit,
    onResults: () -> Unit,
    onInfo: () -> Unit,
) {
    val theme = LocalAppTheme.current
    Box(Modifier.fillMaxSize()) {
        Box(refBox(494, 642, 395, 395)) { Logo2048(395.rp) }

        Box(refBox(496, 1012, 400, 80), contentAlignment = Alignment.Center) {
            RefText(mode.label, 46, theme.onBackground)
        }
        if (mode.ordinal > 0) {
            TapTarget(219, 1000, 40, 40, "Previous mode", { onStepMode(-1) }) {
                RefIcon(R.drawable.ic_chevron_left, 80, theme.onBackground)
            }
        }
        if (mode.ordinal < GameMode.entries.lastIndex) {
            TapTarget(769, 1000, 40, 40, "Next mode", { onStepMode(1) }) {
                RefIcon(R.drawable.ic_chevron_right, 80, theme.onBackground)
            }
        }

        Pill(494.5f, 1167.5f, "New Game", onNewGame)
        if (hasGameInProgress) Pill(494.5f, 1316.5f, "Resume", onResume)

        val nav = listOf(
            NavItem(194, R.drawable.ic_settings, "Settings", 50, onSettings),
            NavItem(405, R.drawable.ic_cart, "Shop", 46) {},
            NavItem(606, R.drawable.ic_list, "Leaderboard", 47, onResults),
            NavItem(806, R.drawable.ic_info, "Info", 46, onInfo),
        )
        nav.forEach { (x, icon, label, box, onClick) -> NavButton(x, icon, label, box, onClick) }
    }
}

/**
 * Bottom nav icon with the press highlight (DESIGN_SPEC section 14): a 145 x 109 stadium behind
 * the icon, `accent` at 15 % with a 2 rp `accent` 35 % border. It appears on press-down; after
 * release it stays until [HIGHLIGHT_MIN_MS] after the press, then fades out over
 * [HIGHLIGHT_FADE_MS]. [onAction] fires [NAV_ACTION_DELAY_MS] after the release. A cancelled press
 * fades with no action; an accessibility click (no press) flashes it for [HIGHLIGHT_MIN_MS], then
 * acts. Hit area and semantics as [TapTarget] (42 rp, at least 48 dp square; button, [label]).
 */
@Composable
private fun RefScope.NavButton(x: Int, @DrawableRes icon: Int, label: String, box: Int, onAction: () -> Unit) {
    val theme = LocalAppTheme.current
    val alpha = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val action by rememberUpdatedState(onAction)
    // Bumped on every press or flash, so a fade scheduled by an older one cannot hide a newer one.
    val generation = remember { intArrayOf(0) }
    val shape = RoundedCornerShape(percent = 50)

    Box(
        refBox(x, NAV_Y, 145, 109)
            .graphicsLayer { this.alpha = alpha.value }
            .background(theme.accent.copy(alpha = 0.15f), shape)
            .border(2.rp, theme.accent.copy(alpha = 0.35f), shape),
    )
    val side = maxOf(42.rp, MIN_TOUCH)
    Box(
        Modifier
            .offset(x.rp - side / 2, NAV_Y.rp - side / 2)
            .size(side)
            .semantics {
                contentDescription = label
                role = Role.Button
                onClick {
                    val gen = ++generation[0]
                    scope.launch {
                        alpha.snapTo(1f)
                        delay(HIGHLIGHT_MIN_MS)
                        action()
                        if (gen == generation[0]) alpha.animateTo(0f, tween(HIGHLIGHT_FADE_MS))
                    }
                    true
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        val gen = ++generation[0]
                        val shown = scope.launch {
                            alpha.snapTo(1f)
                            delay(HIGHLIGHT_MIN_MS)
                        }
                        val released = tryAwaitRelease()
                        scope.launch {
                            shown.join()
                            if (gen == generation[0]) alpha.animateTo(0f, tween(HIGHLIGHT_FADE_MS))
                        }
                        if (released) {
                            scope.launch {
                                delay(NAV_ACTION_DELAY_MS)
                                action()
                            }
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) { RefIcon(icon, box, theme.onBackground) }
}

/** Outlined fully rounded button, 717 x 109 rp, centred at ([cx], [cy]). */
@Composable
private fun RefScope.Pill(cx: Float, cy: Float, label: String, onClick: () -> Unit) {
    val theme = LocalAppTheme.current
    TapTarget(cx, cy, 717, 109, label, onClick) {
        Box(
            Modifier
                .size(717.rp, 109.rp)
                .border(3.rp, theme.outline, RoundedCornerShape(percent = 50)),
            contentAlignment = Alignment.Center,
        ) { RefText(label, 46, theme.onBackground) }
    }
}

/**
 * "Start a new game?" confirmation card (DESIGN_SPEC section 6), shown as the [RefFrame] modal;
 * the window-wide dim behind it is the frame's, and tapping it means No.
 */
@Composable
fun RefScope.ConfirmNewGameDialog(onYes: () -> Unit, onNo: () -> Unit) {
    val theme = LocalAppTheme.current
    val shape = RoundedCornerShape(40.rp)
    Box(
        refBox(494, 1024, 820, 470)
            .testTag("confirm_dialog")
            .semantics { paneTitle = "New game" }
            .clip(shape)
            .background(theme.background)
            .border(3.rp, theme.outline, shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
    ) {
        Box(refBox(410, 167.5, 700, 255), contentAlignment = Alignment.Center) {
            RefText(
                "There is still a game in progress. Are you sure you want to start a new game?",
                40,
                theme.onBackground,
                singleLine = false,
                textAlign = TextAlign.Center,
            )
        }
    }
    PillButton(304f, 1164, "No", theme.outline, onNo)
    PillButton(684f, 1164, "Yes", theme.accent, onYes)
}

@Preview(widthDp = 411, heightDp = 891)
@Composable
private fun HomePreview() {
    RefFrame { HomeScreen(GameMode.NORMAL, {}, {}, true, {}, {}, {}, {}) }
}
