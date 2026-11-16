package com.apocalypse03.game2048.ui

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import com.apocalypse03.game2048.game.Board
import java.io.File

/** Shows [vm]; a non-null [board] replaces its game with a READY session on that board. */
fun ComposeContentTestRule.setApp(vm: AppViewModel, board: Board? = null) {
    if (board != null) vm.restoreGame(board)
    setContent { AppRoot(vm) }
    waitForIdle()
}

/**
 * Like [setApp], but overrides the system font scale with [fontScale] (read on every
 * recomposition, so a test can change it and re-measure).
 */
fun ComposeContentTestRule.setAppWithFontScale(vm: AppViewModel, fontScale: State<Float>, board: Board? = null) {
    if (board != null) vm.restoreGame(board)
    setContent {
        val base = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale.value)) { AppRoot(vm) }
    }
    waitForIdle()
}

/**
 * Scrolls a theme-bar swatch into view and taps it. The tap lands on the left quarter of its hit
 * area: an unsnapped performScrollTo can leave the swatch at the strip's right end, where the
 * open palette toggle (drawn on top) covers the right part of the hit area.
 */
fun SemanticsNodeInteraction.tapSwatch(): SemanticsNodeInteraction {
    performScrollTo()
    return performTouchInput { click(Offset(width * 0.25f, centerY)) }
}

/** Writes the whole window to build/screenshots/[name].png (Gradle test cwd is the module dir). */
fun ComposeContentTestRule.saveScreenshot(name: String) {
    waitForIdle()
    val bmp = onRoot().captureToImage().asAndroidBitmap()
    val dir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
    File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
}
