package com.apocalypse03.game2048.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import com.apocalypse03.game2048.game.Board
import com.apocalypse03.game2048.game.MoveTrace
import com.apocalypse03.game2048.game.TileMotion
import com.apocalypse03.game2048.ui.MoveAnimation
import com.apocalypse03.game2048.ui.components.RefScope
import com.apocalypse03.game2048.ui.components.RefText
import com.apocalypse03.game2048.ui.theme.LocalAppTheme
import kotlin.math.roundToInt

const val BOARD_TAG = "board"
private const val BOARD_SIDE = 778f
private const val PAD = 21f
private const val GAP = 22f

/** Cell side at n = 4; tile corner radius, selected border and text scale with cell / this. */
private const val CELL_4 = 167.5f

/** Cell side (rp) of an [n] x [n] board: (778 - 2 * 21 - (n - 1) * 22) / n; 167.5 at n = 4. */
internal fun cellSide(n: Int): Float = (BOARD_SIDE - 2 * PAD - (n - 1) * GAP) / n

private fun tileTextFraction(value: Int): Float = when (value.toString().length) {
    1, 2 -> 0.42f
    3 -> 0.36f
    4 -> 0.30f
    else -> 0.24f
}

private const val SELECTED_SCALE = 0.88f

/** Move animation (DESIGN_SPEC section 17): progress 0..1 is the slide, 1..2 the merge pop / spawn. */
private const val ANIM_END = 2f

/** Duration of the slide phase (progress 0..1) at 100 % animation speed. */
internal const val SLIDE_MS = 120f

/** Duration of the settle phase (progress 1..2: merge pop and spawn) at 100 % animation speed. */
internal const val SETTLE_MS = 150f
internal const val POP_SCALE = 1.14f

/** Part of the settle phase the merge pop spends rising; the rest falls back to 1. */
private const val POP_RISE = 0.4f

/** The spawned tile appears in the settle phase from this fraction of it to its end. */
private const val SPAWN_START = 0.25f
private const val SPAWN_FROM_SCALE = 0.6f
private const val NO_SEQ = Int.MIN_VALUE

/** Ease-out: starts at full speed and decelerates into place (slide, pop rise, spawn). */
private val EaseOut = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
private val EaseInOut = FastOutSlowInEasing

/** Fraction of the slide still to travel at progress [p]: 1 at 0, 0 from 1 on. */
internal fun slideRemaining(p: Float): Float = if (p >= 1f) 0f else 1f - EaseOut.transform(p.coerceAtLeast(0f))

/** Merge pop scale at progress [p]: 1 -> [POP_SCALE] over the first 40 % of 1..2, back to 1 over the rest. */
internal fun popScale(p: Float): Float {
    if (p <= 1f || p >= ANIM_END) return 1f
    val u = p - 1f
    val k = if (u < POP_RISE) {
        EaseOut.transform(u / POP_RISE)
    } else {
        1f - EaseInOut.transform((u - POP_RISE) / (1f - POP_RISE))
    }
    return 1f + (POP_SCALE - 1f) * k
}

/** Position 0..1 in the spawn window (the last 75 % of the settle phase) at progress [p]. */
private fun spawnWindow(p: Float): Float = ((p - 1f - SPAWN_START) / (1f - SPAWN_START)).coerceIn(0f, 1f)

/** Spawned tile scale at progress [p]: 0.6 until its window starts, then eased out to 1. */
internal fun spawnScale(p: Float): Float =
    SPAWN_FROM_SCALE + (1f - SPAWN_FROM_SCALE) * EaseOut.transform(spawnWindow(p))

/** Spawned tile alpha at progress [p]: 0 until its window starts, 1 from the middle of the window. */
internal fun spawnAlpha(p: Float): Float = (spawnWindow(p) * 2f).coerceAtMost(1f)

/**
 * True when this trace describes a move that ends on [board]: every cell in range, each target
 * holds the moved value (doubled when merged) and the spawn cell holds the spawned value.
 */
private fun MoveTrace.endsOn(board: Board): Boolean {
    val n = board.size
    fun inRange(r: Int, c: Int) = r in 0 until n && c in 0 until n
    val motionsOk = motions.all { m ->
        inRange(m.fromRow, m.fromCol) && inRange(m.toRow, m.toCol) &&
            board[m.toRow, m.toCol] == if (m.merged) m.value * 2 else m.value
    }
    val s = spawn
    return motionsOk && (s == null || inRange(s.row, s.col) && board[s.row, s.col] == s.value)
}

/**
 * Maps a tap at [pos] (px) on an [n] x [n] board [sidePx] wide to (row, col), or null in the
 * padding or a gap.
 */
private fun cellAt(pos: Offset, sidePx: Float, n: Int): Pair<Int, Int>? {
    val perRp = sidePx / BOARD_SIDE
    val cell = cellSide(n)
    fun index(px: Float): Int? {
        val local = px / perRp - PAD
        if (local < 0f) return null
        val i = (local / (cell + GAP)).toInt()
        return if (i < n && local - i * (cell + GAP) <= cell) i else null
    }
    val r = index(pos.y) ?: return null
    val c = index(pos.x) ?: return null
    return r to c
}

/**
 * Board (DESIGN_SPEC section 7): top-left (105, 705) rp, square 778 rp, [Board.size] cells a side
 * (4 in Normal, 6 in 2048+ mode); cell side from [cellSide].
 * It handles no swipes: those are window-wide ([swipeToMove], IMPLEMENTATION_PLAN 3.3).
 * In [princess] mode (section 11) a tap on a cell calls [onTileTap];
 * the [selected] cell (r * n + c) is drawn at 0.88 scale with a white border.
 *
 * Move animation (section 17): a [moveAnimation] with a new seq, at [animSpeed] > 0 % and ending on
 * [board] (always the final board), first slides the old tiles from their old cells to their new
 * ones ([SLIDE_MS] * f ms, ease-out, f = animSpeed / 100; the spawn cell shows empty), then
 * settles for [SETTLE_MS] * f ms on [board]: merged cells pop 1 -> 1.14 -> 1 (ease-out rise over
 * the first 40 %, ease-in-out fall) and the spawned tile, invisible for the first 25 %, grows
 * 0.6 -> 1 while fading in. A new seq restarts it from 0 on the new trace. Input is never blocked.
 * One progress value drives it, read only in layer lambdas (float translation, scale, alpha), so
 * frames do not recompose or re-lay-out the board (only the phase change recomposes). Outside an
 * animation the board draws exactly as a static board.
 */
@Composable
fun RefScope.BoardView(
    board: Board,
    princess: Boolean = false,
    selected: Int? = null,
    onTileTap: (Int, Int) -> Unit = { _, _ -> },
    moveAnimation: MoveAnimation? = null,
    animSpeed: Int = 100,
) {
    val theme = LocalAppTheme.current
    val tileTap by rememberUpdatedState(onTileTap)
    val n = board.size
    val cell = cellSide(n)
    val k = cell / CELL_4
    val f = animSpeed / 100f

    // Progress of the current run, and the seq whose run has started / finished here.
    val progress = remember { Animatable(ANIM_END) }
    var startedSeq by remember { mutableIntStateOf(NO_SEQ) }
    var doneSeq by remember { mutableIntStateOf(NO_SEQ) }
    val anim = moveAnimation?.takeIf { f > 0f && it.seq != doneSeq && it.trace.endsOn(board) }
    val seq = anim?.seq ?: NO_SEQ
    LaunchedEffect(seq) {
        if (seq == NO_SEQ) return@LaunchedEffect
        progress.snapTo(0f)
        startedSeq = seq
        // One run, linear within each phase: 0..1 over the slide time, 1..2 over the settle time.
        val slideMs = (SLIDE_MS * f).roundToInt().coerceAtLeast(1)
        val settleMs = (SETTLE_MS * f).roundToInt().coerceAtLeast(1)
        progress.animateTo(
            ANIM_END,
            keyframes {
                durationMillis = slideMs + settleMs
                1f at slideMs
            },
        )
        // Reached only when the run completes: a newer seq cancels this effect before it.
        doneSeq = seq
    }
    // Until the effect starts (the frame the move lands) a new run reads as progress 0.
    val p: () -> Float = { if (seq != NO_SEQ && startedSeq == seq) progress.value else if (seq != NO_SEQ) 0f else ANIM_END }
    val sliding by remember(seq) { derivedStateOf { p() < 1f } }
    val trace = anim?.trace
    // Per target cell: the motions ending there (two for a merge).
    val byTarget = remember(trace) { trace?.motions?.groupBy { it.toRow to it.toCol }.orEmpty() }
    val merged = remember(trace) { trace?.mergedCells.orEmpty() }

    Box(
        Modifier
            .offset(105.rp, 705.rp)
            .size(BOARD_SIDE.rp)
            .testTag(BOARD_TAG)
            .pointerInput(princess, n) {
                if (princess) {
                    detectTapGestures { offset ->
                        cellAt(offset, size.width.toFloat(), n)?.let { (r, c) -> tileTap(r, c) }
                    }
                }
            }
            .background(theme.boardBg, RoundedCornerShape(14.rp)),
    ) {
        val shape = RoundedCornerShape((10f * k).rp)
        // Empty-cell grid. At rest only under empty cells (a selected tile shrinks over the board
        // background, as before); while animating under every cell, since tiles slide off them.
        for (r in 0 until n) {
            for (c in 0 until n) {
                if (anim != null || board[r, c] == 0) {
                    Box(
                        Modifier
                            .offset((PAD + c * (cell + GAP)).rp, (PAD + r * (cell + GAP)).rp)
                            .size(cell.rp)
                            .background(theme.emptyCell, shape),
                    )
                }
            }
        }

        // Second source of each merge: slides onto the target under the first, during the slide only.
        if (sliding) {
            byTarget.values.forEach { ms ->
                ms.drop(1).forEach { m ->
                    BoardTile(m.toRow, m.toCol, m.value, cell, k, shape, null, m, p, pop = false, spawn = false, isSelected = false)
                }
            }
        }
        // One node per cell, tagged tile_r_c: during the slide the (first) tile moving onto it with
        // its old value, else the final board.
        val spawn = trace?.spawn
        for (r in 0 until n) {
            for (c in 0 until n) {
                val motion = if (sliding) byTarget[r to c]?.first() else null
                val v = if (sliding) motion?.value ?: 0 else board[r, c]
                BoardTile(
                    r, c, v, cell, k, shape, "tile_${r}_$c", motion, p,
                    pop = (r to c) in merged,
                    spawn = spawn != null && spawn.row == r && spawn.col == c,
                    isSelected = anim == null && v != 0 && selected == r * n + c,
                )
            }
        }
    }
}

/**
 * One tile of value [v] (0 = transparent) at cell ([r], [c]). With a [motion] its layer is
 * translated back toward the motion's old cell while [p] < 1; with [pop] / [spawn] the same layer
 * scales (and, for a spawn, fades in) during 1 <= [p] <= 2. No layer otherwise.
 */
@Composable
private fun RefScope.BoardTile(
    r: Int,
    c: Int,
    v: Int,
    cell: Float,
    k: Float,
    shape: Shape,
    tag: String?,
    motion: TileMotion?,
    p: () -> Float,
    pop: Boolean,
    spawn: Boolean,
    isSelected: Boolean,
) {
    val theme = LocalAppTheme.current
    Box(
        Modifier
            .offset((PAD + c * (cell + GAP)).rp, (PAD + r * (cell + GAP)).rp)
            .then(
                if (motion != null || pop || spawn) {
                    val step = (cell + GAP).rp
                    Modifier.graphicsLayer {
                        val t = p()
                        if (motion != null && t < 1f) {
                            val px = step.toPx() * slideRemaining(t)
                            translationX = (motion.fromCol - motion.toCol) * px
                            translationY = (motion.fromRow - motion.toRow) * px
                        }
                        if (t >= 1f && t < ANIM_END) {
                            if (spawn) {
                                val s = spawnScale(t)
                                scaleX = s
                                scaleY = s
                                alpha = spawnAlpha(t)
                            } else if (pop) {
                                val s = popScale(t)
                                scaleX = s
                                scaleY = s
                            }
                        }
                    }
                } else {
                    Modifier
                },
            )
            .size(cell.rp)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .then(
                if (isSelected) {
                    Modifier
                        .scale(SELECTED_SCALE)
                        .border((6f * k).rp, Color.White, shape)
                } else {
                    Modifier
                },
            )
            .then(if (v != 0) Modifier.background(theme.tileColor(v), shape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (v != 0) {
            RefText(
                v.toString(),
                cell * tileTextFraction(v),
                theme.tileTextColor(v),
                weight = FontWeight.Bold,
                followFontScale = false,
            )
        }
    }
}
