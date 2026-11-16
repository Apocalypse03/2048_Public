package com.apocalypse03.game2048.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleStartEffect
import com.apocalypse03.game2048.ui.components.PaletteToggle
import com.apocalypse03.game2048.ui.components.RefFrame
import com.apocalypse03.game2048.ui.components.ThemeBar
import com.apocalypse03.game2048.ui.game.GameScreen
import com.apocalypse03.game2048.ui.game.swipeToMove
import com.apocalypse03.game2048.ui.home.ConfirmNewGameDialog
import com.apocalypse03.game2048.ui.home.HomeScreen
import com.apocalypse03.game2048.ui.info.InfoPanel
import com.apocalypse03.game2048.ui.results.ResultsScreen
import com.apocalypse03.game2048.ui.settings.SettingsScreen
import com.apocalypse03.game2048.ui.theme.AppThemes
import com.apocalypse03.game2048.ui.theme.LocalAppTheme
import com.apocalypse03.game2048.ui.theme.animatedAppTheme
import com.apocalypse03.game2048.ui.theme.getActiveVariant
import com.apocalypse03.game2048.ui.timer.formatElapsed
import com.apocalypse03.game2048.ui.timer.rememberTimerText

/** App root: themed frame, Home/Game/Settings/Results transitions, shared palette button and theme bar. */
@Composable
fun AppRoot(vm: AppViewModel) {
    // The system dark mode picks the variant; a switch animates like a theme change.
    val theme = animatedAppTheme(AppThemes.resolve(vm.themeId, getActiveVariant()))
    var paletteOpen by rememberSaveable { mutableStateOf(false) }
    val easing = FastOutSlowInEasing

    LifecycleStartEffect(vm) {
        vm.onAppStart()
        onStopOrDispose { vm.onAppStop() }
    }

    CompositionLocalProvider(LocalAppTheme provides theme) {
        BackHandler(enabled = paletteOpen) { paletteOpen = false }
        BackHandler(enabled = vm.screen != Screen.HOME && !paletteOpen) { vm.goHome() }
        BackHandler(enabled = vm.infoOpen) { vm.closeInfo() }
        // Registered last so an open confirmation dialog wins over the other back handlers.
        BackHandler(enabled = vm.confirmNewGame) { vm.confirmNewGameNo() }
        RefFrame(
            fontScale = vm.fontScale / 100f,
            // Swipes move the board from anywhere in the window (DESIGN_SPEC section 7): off while
            // leaving the game, while the theme bar is open and under a modal.
            modifier = Modifier.swipeToMove(
                enabled = vm.screen == Screen.GAME && !paletteOpen && !vm.confirmNewGame && !vm.infoOpen,
                onSwipe = vm::move,
            ),
            onScrimTap = if (paletteOpen) ({ paletteOpen = false }) else null,
            // New Game on Home, or on the win screen (DESIGN_SPEC section 13).
            // Info panel from Home (DESIGN_SPEC section 19); the confirmation wins if both are set.
            modal = when {
                vm.confirmNewGame -> {
                    { ConfirmNewGameDialog(vm::confirmNewGameYes, vm::confirmNewGameNo) }
                }
                vm.infoOpen -> {
                    { InfoPanel(vm::closeInfo) }
                }
                else -> null
            },
            onModalDismiss = { if (vm.confirmNewGame) vm.confirmNewGameNo() else vm.closeInfo() },
            overlay = {
                AnimatedVisibility(
                    visible = paletteOpen,
                    enter = fadeIn(tween(180)) + expandHorizontally(tween(180), expandFrom = Alignment.End),
                    exit = fadeOut(tween(180)) + shrinkHorizontally(tween(180), shrinkTowards = Alignment.End),
                ) {
                    ThemeBar(selectedId = vm.themeId, onSelect = vm::selectTheme)
                }
                PaletteToggle(open = paletteOpen, onClick = { paletteOpen = !paletteOpen })
            },
        ) {
            AnimatedContent(
                targetState = vm.screen,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    // Into a sub-screen slides in from the right; back to Home slides the other way.
                    val dir = if (targetState != Screen.HOME) 1 else -1
                    (
                        fadeIn(tween(250, easing = easing)) +
                            slideInHorizontally(tween(250, easing = easing)) { (it * 0.08f).toInt() * dir }
                        ) togetherWith (
                        fadeOut(tween(250, easing = easing)) +
                            slideOutHorizontally(tween(250, easing = easing)) { -(it * 0.08f).toInt() * dir }
                        )
                },
                label = "screen",
            ) { screen ->
                when (screen) {
                    Screen.HOME -> HomeScreen(
                        mode = vm.mode,
                        onStepMode = vm::stepMode,
                        onNewGame = vm::requestNewGame,
                        hasGameInProgress = vm.hasGameInProgress,
                        onResume = vm::resumeGame,
                        onSettings = vm::openSettings,
                        onResults = vm::openResults,
                        onInfo = vm::openInfo,
                    )
                    Screen.GAME -> GameScreen(
                        state = vm.game,
                        timer = rememberTimerText(vm),
                        onMove = vm::move,
                        onUndo = vm::undo,
                        onBack = vm::goHome,
                        onTimerTap = { if (!paletteOpen) vm.onTimerTap() },
                        selected = vm.selected,
                        onTileTap = vm::onTileTap,
                        onExitPrincess = vm::exitPrincess,
                        winPending = vm.winPending,
                        // The timer is frozen while the win is pending, so this read is stable.
                        winElapsed = if (vm.winPending) formatElapsed(vm.elapsedMs()) else "",
                        onEndless = vm::continueEndless,
                        onWinNewGame = vm::requestNewGame,
                        moveAnimation = vm.moveAnimation,
                        animSpeed = vm.animSpeed,
                        logoHidden = paletteOpen,
                    )
                    Screen.SETTINGS -> SettingsScreen(
                        speed = vm.animSpeed,
                        onSpeed = vm::setAnimSpeed,
                        vibration = vm.vibration,
                        onVibration = vm::setVibration,
                        vibrationMs = vm.vibrationMs,
                        onVibrationMs = vm::setVibrationMs,
                        onPreviewVibration = vm::previewVibration,
                        fontScale = vm.fontScale,
                        onFontScale = vm::setFontScale,
                        onBack = vm::goHome,
                    )
                    Screen.RESULTS -> ResultsScreen(
                        mode = vm.resultsMode,
                        onStepMode = vm::stepResultsMode,
                        results = vm.results(vm.resultsMode),
                        onBack = vm::goHome,
                    )
                }
            }

        }
    }
}
