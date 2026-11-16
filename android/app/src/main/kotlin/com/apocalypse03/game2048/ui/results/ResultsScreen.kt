package com.apocalypse03.game2048.ui.results

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.apocalypse03.game2048.R
import com.apocalypse03.game2048.ui.GameMode
import com.apocalypse03.game2048.ui.components.RefFrame
import com.apocalypse03.game2048.ui.components.RefIcon
import com.apocalypse03.game2048.ui.components.RefScope
import com.apocalypse03.game2048.ui.components.RefText
import com.apocalypse03.game2048.ui.components.TapTarget
import com.apocalypse03.game2048.ui.data.GameResult
import com.apocalypse03.game2048.ui.theme.LocalAppTheme
import com.apocalypse03.game2048.ui.timer.formatElapsed

/** Test tag of the scrolling results box (DESIGN_SPEC section 16). */
const val RESULTS_BOX_TAG = "results_box"

/** Test tag shared by every result row (one merged semantics node each). */
const val RESULTS_ROW_TAG = "results_row"

/** Test tag shared by the separator lines between rows. */
const val RESULTS_SEPARATOR_TAG = "results_separator"

/** Results box geometry, rp (DESIGN_SPEC section 16). */
private const val BOX_LEFT = 105
private const val BOX_TOP = 450
private const val BOX_W = 778
private const val BOX_H = 1220
private const val BOX_RADIUS = 40
private const val PAD_X = 40
private const val PAD_Y = 20
private const val ROW_H = 140

/** Second column start, box-relative: the box centre x (494 in frame terms). */
private const val COL2_X = 494 - BOX_LEFT

/** Gap between the two lines of a row, rp. */
private const val LINE_GAP = 8

/** Accessibility reading of a row: "Score 12345, tile 1024, 345 moves, time 4M 05S". */
fun resultDescription(r: GameResult): String =
    "Score ${r.score}, tile ${r.tile}, ${r.moves} ${if (r.moves == 1) "move" else "moves"}, time ${formatElapsed(r.timeMs)}"

/**
 * Results screen (DESIGN_SPEC section 16); the palette button lives in the app root.
 * [mode] is the list shown (its own selector, not Home's), [onStepMode] steps it, [results] is
 * that mode's ordered top-20 list. The rows scroll inside the outlined, clipped results box;
 * an empty list shows "No results yet." instead.
 */
@Composable
fun RefScope.ResultsScreen(
    mode: GameMode,
    onStepMode: (Int) -> Unit,
    results: List<GameResult>,
    onBack: () -> Unit,
) {
    val theme = LocalAppTheme.current
    Box(Modifier.fillMaxSize()) {
        TapTarget(89, 93, 45, 45, "Back", onBack) { RefIcon(R.drawable.ic_arrow_back, 67, theme.onBackground) }
        Box(refBox(494, 236, 800, 120), contentAlignment = Alignment.Center) {
            RefText("Results", 70, theme.onBackground)
        }

        // Mode selector: the Home widget (section 6) at y 362.
        Box(refBox(494, 362, 400, 80), contentAlignment = Alignment.Center) {
            RefText(mode.label, 46, theme.onBackground)
        }
        if (mode.ordinal > 0) {
            TapTarget(219, 362, 40, 40, "Previous mode", { onStepMode(-1) }) {
                RefIcon(R.drawable.ic_chevron_left, 80, theme.onBackground)
            }
        }
        if (mode.ordinal < GameMode.entries.lastIndex) {
            TapTarget(769, 362, 40, 40, "Next mode", { onStepMode(1) }) {
                RefIcon(R.drawable.ic_chevron_right, 80, theme.onBackground)
            }
        }

        val shape = RoundedCornerShape(BOX_RADIUS.rp)
        LazyColumn(
            refBox(BOX_LEFT + BOX_W / 2f, BOX_TOP + BOX_H / 2f, BOX_W, BOX_H)
                .testTag(RESULTS_BOX_TAG)
                .clip(shape)
                .border(3.rp, theme.outline, shape),
            contentPadding = PaddingValues(vertical = PAD_Y.rp),
        ) {
            itemsIndexed(results, key = { _, r -> r.id }) { i, r ->
                ResultRow(r, last = i == results.lastIndex, dim = theme.onBackground.copy(alpha = 0.7f))
            }
        }
        if (results.isEmpty()) {
            Box(refBox(494, 520, BOX_W - 2 * PAD_X, 80), contentAlignment = Alignment.Center) {
                RefText("No results yet.", 34, theme.onBackground)
            }
        }
    }
}

/**
 * One 140 rp row: "Score" / "Tile" (size 34) over "Moves" / "Time" (size 30, [dim]), columns at
 * the left padding and at the box centre, the pair centred vertically [LINE_GAP] apart. A 2 rp
 * `outline` separator, inset by the padding, follows every row but the [last].
 */
@Composable
private fun RefScope.ResultRow(r: GameResult, last: Boolean, dim: Color) {
    val theme = LocalAppTheme.current
    val description = resultDescription(r)
    Box(Modifier.fillMaxWidth().height(ROW_H.rp)) {
        Column(
            Modifier
                .fillMaxSize()
                .testTag(RESULTS_ROW_TAG)
                .semantics(mergeDescendants = true) { contentDescription = description },
            verticalArrangement = Arrangement.spacedBy(LINE_GAP.rp, Alignment.CenterVertically),
        ) {
            RowLine("Score: ${r.score}", "Tile: ${r.tile}", 34, theme.onBackground)
            RowLine("Moves: ${r.moves}", "Time: ${formatElapsed(r.timeMs)}", 30, dim)
        }
        if (!last) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = PAD_X.rp)
                    .fillMaxWidth()
                    .height(2.rp)
                    .testTag(RESULTS_SEPARATOR_TAG)
                    .background(theme.outline),
            )
        }
    }
}

/** One line of a row: [left] at the left padding, [right] at the second column. */
@Composable
private fun RefScope.RowLine(left: String, right: String, size: Int, color: Color) {
    Box(Modifier.fillMaxWidth()) {
        RefText(left, size, color, Modifier.offset(x = PAD_X.rp))
        RefText(right, size, color, Modifier.offset(x = COL2_X.rp))
    }
}

@Preview(widthDp = 411, heightDp = 891)
@Composable
private fun ResultsPreview() {
    RefFrame {
        ResultsScreen(
            GameMode.NORMAL,
            {},
            listOf(GameResult(1, 12345, 1024, 345, 245_000), GameResult(2, 2048, 256, 120, 61_000)),
            {},
        )
    }
}
