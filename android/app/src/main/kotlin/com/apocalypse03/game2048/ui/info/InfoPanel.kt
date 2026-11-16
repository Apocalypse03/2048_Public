package com.apocalypse03.game2048.ui.info

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import com.apocalypse03.game2048.BuildConfig
import com.apocalypse03.game2048.ui.components.Logo2048
import com.apocalypse03.game2048.ui.components.RefScope
import com.apocalypse03.game2048.ui.components.RefText
import com.apocalypse03.game2048.ui.components.TapTarget
import com.apocalypse03.game2048.ui.theme.LocalAppTheme

/** Author shown in the Info panel; the only place the handle appears. */
const val AUTHOR_HANDLE = "Apocalypse03"

/** Panel rectangle in the frame, rp: centre (494.5, 1024), 824 x 640 (top 704, left 82.5). */
private const val PANEL_CX = 494.5f
private const val PANEL_CY = 1024f
private const val PANEL_W = 824f
private const val PANEL_H = 640f
private const val PANEL_TOP = PANEL_CY - PANEL_H / 2f

/**
 * Info panel (DESIGN_SPEC section 19), shown as the [com.apocalypse03.game2048.ui.components.RefFrame]
 * modal over Home: logo + "2048", version (from BuildConfig), author, and a Close pill. The dim
 * behind it is the frame's; tapping the dim or Back closes it, taps on the panel do not.
 * Children are placed in panel-local rp (frame y minus [PANEL_TOP], frame x minus the panel's left edge, 82.5).
 */
@Composable
fun RefScope.InfoPanel(onClose: () -> Unit) {
    val theme = LocalAppTheme.current
    val shape = RoundedCornerShape(63.rp)
    val cx = PANEL_W / 2f
    Box(
        refBox(PANEL_CX, PANEL_CY, PANEL_W, PANEL_H)
            .testTag("info_panel")
            .semantics { paneTitle = "Info" }
            .clip(shape)
            .background(theme.background)
            .border(2.rp, theme.outline, shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
    ) {
        Box(refBox(cx, 846f - PANEL_TOP, PANEL_W, 135), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Logo2048(135.rp)
                Spacer(Modifier.width(24.rp))
                RefText("2048", 56, theme.onBackground)
            }
        }
        Box(refBox(cx, 983f - PANEL_TOP, PANEL_W, 50), contentAlignment = Alignment.Center) {
            RefText("Version ${BuildConfig.VERSION_NAME}", 28, theme.onBackground.copy(alpha = 0.65f))
        }
        Box(refBox(cx, 1040f - PANEL_TOP, PANEL_W, 50), contentAlignment = Alignment.Center) {
            RefText("Author: $AUTHOR_HANDLE", 28, theme.onBackground.copy(alpha = 0.85f))
        }
        // Outlined pill like Home's "New Game" (3 rp outline); label 34 rp, below the 56 rp title. PillButton is fixed at 330 x 100.
        TapTarget(cx, 1212f - PANEL_TOP, 662, 105, "Close", onClose) {
            Box(
                Modifier
                    .size(662.rp, 105.rp)
                    .border(3.rp, theme.outline, RoundedCornerShape(percent = 50)),
                contentAlignment = Alignment.Center,
            ) { RefText("Close", 34, theme.onBackground) }
        }
    }
}
