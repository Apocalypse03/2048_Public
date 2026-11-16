package com.apocalypse03.game2048.ui.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.apocalypse03.game2048.R
import com.apocalypse03.game2048.game.Direction
import com.apocalypse03.game2048.game.GameState
import com.apocalypse03.game2048.game.GameStatus
import com.apocalypse03.game2048.ui.MoveAnimation
import com.apocalypse03.game2048.ui.components.Logo2048
import com.apocalypse03.game2048.ui.components.MIN_TOUCH
import com.apocalypse03.game2048.ui.components.PillButton
import com.apocalypse03.game2048.ui.components.RefFrame
import com.apocalypse03.game2048.ui.components.RefIcon
import com.apocalypse03.game2048.ui.components.RefScope
import com.apocalypse03.game2048.ui.components.RefText
import com.apocalypse03.game2048.ui.components.TapTarget
import com.apocalypse03.game2048.ui.theme.LocalAppTheme

const val GAME_TAG = "game"
const val WIN_TAG = "win_overlay"

/** Win screen line (DESIGN_SPEC section 13); [elapsed] is already formatted ("1M 05S"). */
// No-break space keeps the count on the same line as "move(s)" when the text wraps.
fun winMessage(moves: Int, elapsed: String): String =
    "You unlocked the 2048 tile with $moves ${if (moves == 1) "move" else "moves"} in $elapsed."

/**
 * Game screen (DESIGN_SPEC section 7): arrow/D-pad keys call [onMove]; swipes are not handled here
 * but window-wide by [swipeToMove] on the app's root frame. Key input is ignored when OVER or in
 * Princess Mode (section 11), where tile taps select/swap and the crown exits.
 * While [winPending] (section 13) the win overlay covers the board: input, undo and the hint are
 * off; [winElapsed] is the frozen game time, [onEndless] / [onWinNewGame] are its two buttons.
 * [moveAnimation] and [animSpeed] (percent) drive the board move animation (section 17).
 * [logoHidden] fades the header logo out (the open theme bar covers that row but is shorter than the logo).
 */
@Composable
fun RefScope.GameScreen(
    state: GameState,
    timer: () -> String,
    onMove: (Direction) -> Unit,
    onUndo: () -> Unit,
    onBack: () -> Unit,
    onTimerTap: () -> Unit = {},
    selected: Int? = null,
    onTileTap: (Int, Int) -> Unit = { _, _ -> },
    onExitPrincess: () -> Unit = {},
    winPending: Boolean = false,
    winElapsed: String = "",
    onEndless: () -> Unit = {},
    onWinNewGame: () -> Unit = {},
    moveAnimation: MoveAnimation? = null,
    animSpeed: Int = 100,
    logoHidden: Boolean = false,
) {
    val theme = LocalAppTheme.current
    val dim = theme.onBackground.copy(alpha = 0.7f)
    val over = state.status == GameStatus.OVER
    // Gate for the keyboard path (the view model applies the same rules to swipes).
    val move: (Direction) -> Unit = { if (!over && !state.princess && !winPending) onMove(it) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Box(
        Modifier
            .fillMaxSize()
            .testTag(GAME_TAG)
            .onKeyEvent { e ->
                val dir = if (e.type == KeyEventType.KeyDown) keyDirection(e.key) else null
                dir?.let(move)
                dir != null
            }
            .focusRequester(focus)
            .focusable(),
    ) {
        TapTarget(89, 93, 45, 45, "Back", onBack) { RefIcon(R.drawable.ic_arrow_back, 67, theme.onBackground) }

        val canUndo = state.canUndo && !winPending
        val undoTint = theme.onBackground.let { if (canUndo) it else it.copy(alpha = it.alpha * 0.3f) }
        TapTarget(772, 92, 67, 67, "Undo", onUndo, enabled = canUndo) {
            RefIcon(R.drawable.ic_undo, 67, undoTint)
        }

        // Header (DESIGN_SPEC section 7): logo centred on the Back/Undo row (y 92); score/Best/timer below it,
        // all below the theme-bar pill (bottom 174) so they stay visible while the bar is open. Logo, score, Best and timer are 1.5x.
        val logoAlpha by animateFloatAsState(if (logoHidden) 0f else 1f, tween(180), label = "logo")
        Box(refBox(494, 92, 125, 125).graphicsLayer { alpha = logoAlpha }) { Logo2048(125.rp) }
        Box(refBox(494, 287, 900, 90), contentAlignment = Alignment.Center) {
            RefText(state.score.toString(), 51, theme.onBackground)
        }

        Box(refBox(494, 353, 900, 70), contentAlignment = Alignment.Center) { RefText("Best ${state.best}", 39, dim) }
        // No button role: the row reads as the elapsed time. timer() is read only in this lambda,
        // so each tick recomposes just the label.
        TapTarget(494, 416, 450, 70, contentDescription = null, onClick = onTimerTap, role = null) {
            val text = timer()
            RefText(text, 39, dim, Modifier.semantics { contentDescription = "Elapsed time $text" })
        }

        val hint = if (state.princess || winPending) null else when (state.status) {
            GameStatus.READY -> "Swipe to begin."
            GameStatus.OVER -> "Game over."
            GameStatus.PLAYING -> null
        }
        if (hint != null) {
            Box(refBox(494, 620, 800, 50), contentAlignment = Alignment.Center) { RefText(hint, 30, dim) }
        }

        BoardView(
            state.board,
            princess = state.princess,
            selected = selected,
            onTileTap = onTileTap,
            moveAnimation = moveAnimation,
            animSpeed = animSpeed,
        )

        // The animated box sits exactly on the crown's tap target so scaleIn pivots on the crown.
        val crownSide = maxOf(140.rp, MIN_TOUCH).value / scale
        AnimatedVisibility(
            visible = state.princess,
            modifier = refBox(494, 1640, crownSide, crownSide),
            enter = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.8f),
            exit = fadeOut(tween(180)),
        ) {
            TapTarget(crownSide / 2, crownSide / 2, 140, 140, "Exit Princess Mode", onExitPrincess) {
                RefIcon(R.drawable.ic_crown, 140, theme.accent)
            }
        }

        AnimatedVisibility(
            visible = winPending,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180)),
        ) {
            Box(Modifier.fillMaxSize()) { WinOverlay(state.moves, winElapsed, onEndless, onWinNewGame) }
        }
    }
}

/**
 * Win screen (DESIGN_SPEC section 13): a scrim over the board rect only (the board stays visible)
 * that swallows touches, the title and summary centred on the board centre, and two buttons at
 * y 1640.
 */
@Composable
private fun RefScope.WinOverlay(moves: Int, elapsed: String, onEndless: () -> Unit, onNewGame: () -> Unit) {
    val theme = LocalAppTheme.current
    Box(
        refBox(494, 1094, 778, 778)
            .testTag(WIN_TAG)
            .background(theme.background.copy(alpha = 0.88f), RoundedCornerShape(14.rp))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.rp),
        ) {
            RefText("You Win!", 72, theme.onBackground, weight = FontWeight.Bold)
            Box(Modifier.width(700.rp), contentAlignment = Alignment.Center) {
                RefText(
                    winMessage(moves, elapsed),
                    34,
                    theme.onBackground,
                    singleLine = false,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
    // 36 rp (dialog uses 40) so "Endless mode" keeps side margin in the pill at font scale 1.3.
    PillButton(304, 1640, "Endless mode", theme.outline, onEndless, textSize = 36)
    PillButton(684, 1640, "New Game", theme.accent, onNewGame, textSize = 36)
}

@Preview(widthDp = 411, heightDp = 891)
@Composable
private fun GamePreview() {
    RefFrame {
        GameScreen(
            GameState(ShowcaseBoard, 0, 0, false, GameStatus.READY),
            { "0S" },
            onMove = {},
            onUndo = {},
            onBack = {},
            onTimerTap = {},
            selected = null,
            onTileTap = { _, _ -> },
            onExitPrincess = {},
        )
    }
}
