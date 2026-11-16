package com.apocalypse03.game2048.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.apocalypse03.game2048.R
import com.apocalypse03.game2048.ui.theme.LocalAppTheme

/** Accent rounded square with the 2048 glyphs tinted in the on-accent colour. */
@Composable
fun Logo2048(size: Dp, modifier: Modifier = Modifier) {
    val theme = LocalAppTheme.current
    Box(
        modifier
            .size(size)
            .semantics { contentDescription = "2048 logo" }
            .drawBehind {
                val r = this.size.width * 46.812f / 300f
                drawRoundRect(theme.accent, cornerRadius = CornerRadius(r, r))
            },
    ) {
        Image(
            painter = painterResource(R.drawable.logo_2048_glyphs),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            colorFilter = ColorFilter.tint(theme.onAccent.copy(alpha = 0.94f)),
        )
    }
}
