package com.apocalypse03.game2048.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.apocalypse03.game2048.ui.theme.LocalAppTheme

/**
 * Outlined fully rounded 330 x 100 rp button centred at ([cx], [cy]): 3 rp [stroke], no fill,
 * [label] at [textSize] rp in [text] colour (defaults to onBackground). Content description = label.
 * Used by the new-game confirmation (DESIGN_SPEC section 6) and the win screen (section 13).
 */
@Composable
fun RefScope.PillButton(
    cx: Number,
    cy: Number,
    label: String,
    stroke: Color,
    onClick: () -> Unit,
    text: Color = LocalAppTheme.current.onBackground,
    textSize: Number = 40,
) {
    TapTarget(cx, cy, 330, 100, label, onClick) {
        Box(
            Modifier
                .size(330.rp, 100.rp)
                .border(3.rp, stroke, RoundedCornerShape(percent = 50)),
            contentAlignment = Alignment.Center,
        ) { RefText(label, textSize, text) }
    }
}
