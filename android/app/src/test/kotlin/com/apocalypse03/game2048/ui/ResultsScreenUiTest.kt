package com.apocalypse03.game2048.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.lifecycle.SavedStateHandle
import com.apocalypse03.game2048.game.Board
import com.apocalypse03.game2048.game.Direction
import com.apocalypse03.game2048.game.GameStatus
import com.apocalypse03.game2048.ui.components.RefFrame
import com.apocalypse03.game2048.ui.data.GameResult
import com.apocalypse03.game2048.ui.data.InMemoryGameStore
import com.apocalypse03.game2048.ui.data.InMemorySettingsStore
import com.apocalypse03.game2048.ui.results.RESULTS_BOX_TAG
import com.apocalypse03.game2048.ui.results.RESULTS_ROW_TAG
import com.apocalypse03.game2048.ui.results.RESULTS_SEPARATOR_TAG
import com.apocalypse03.game2048.ui.results.ResultsScreen
import com.apocalypse03.game2048.ui.results.resultDescription
import com.apocalypse03.game2048.ui.timer.FakeClock
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** [n] results with distinct, descending scores (id i + 1). */
private fun sampleResults(n: Int) = List(n) { i ->
    GameResult(id = i + 1L, score = 10_000 - i * 100, tile = 1024, moves = 300 + i, timeMs = 245_000L + i * 1_000L)
}

/** Results screen in isolation (DESIGN_SPEC section 16). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class ResultsScreenUiTest {

    @get:Rule
    val rule = createComposeRule()

    private val steps = mutableListOf<Int>()
    private var backs = 0

    private fun show(results: List<GameResult>, mode: GameMode = GameMode.NORMAL) {
        val state = mutableStateOf(mode)
        rule.setContent {
            RefFrame {
                ResultsScreen(
                    mode = state.value,
                    onStepMode = { d ->
                        steps += d
                        state.value = GameMode.entries[(state.value.ordinal + d).coerceIn(0, GameMode.entries.lastIndex)]
                    },
                    results = results,
                    onBack = { backs++ },
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun emptyStateShowsMessageInsideTheBox() {
        show(emptyList())
        rule.onNodeWithText("Results").assertExists()
        rule.onNodeWithText("No results yet.").assertIsDisplayed()
        rule.onNodeWithTag(RESULTS_BOX_TAG).assertExists()
        assertEquals(0, rule.onAllNodesWithTag(RESULTS_ROW_TAG).fetchSemanticsNodes().size)
        val box = rule.onNodeWithTag(RESULTS_BOX_TAG).fetchSemanticsNode().boundsInRoot
        val msg = rule.onNodeWithText("No results yet.").fetchSemanticsNode().boundsInRoot
        assertTrue(msg.top >= box.top && msg.bottom <= box.bottom && msg.left >= box.left && msg.right <= box.right)
        assertEquals(box.center.x, msg.center.x, 2f)
    }

    @Test
    fun rowsShowTextsAndDescriptionsWithSeparatorsBetween() {
        val results = listOf(
            GameResult(1, 12345, 1024, 345, 245_000),
            GameResult(2, 2048, 256, 1, 42_000),
            GameResult(3, 16, 8, 2, 3_725_000),
        )
        show(results)
        rule.onNodeWithText("No results yet.").assertDoesNotExist()
        assertEquals(3, rule.onAllNodesWithTag(RESULTS_ROW_TAG).fetchSemanticsNodes().size)
        rule.onNodeWithContentDescription("Score 12345, tile 1024, 345 moves, time 4M 05S").assertExists()
        rule.onNodeWithContentDescription("Score 2048, tile 256, 1 move, time 42S").assertExists()
        rule.onNodeWithContentDescription("Score 16, tile 8, 2 moves, time 1H 02M 05S").assertExists()
        listOf("Score: 12345", "Tile: 1024", "Moves: 345", "Time: 4M 05S", "Time: 1H 02M 05S").forEach {
            rule.onNodeWithText(it, useUnmergedTree = true).assertExists()
        }
        // Each row is one merged node: its texts are found on the row, not as separate nodes.
        rule.onNodeWithText("Score: 12345").assertExists()
            .assert(hasContentDescription("Score 12345, tile 1024, 345 moves, time 4M 05S"))
        assertEquals(2, rule.onAllNodesWithTag(RESULTS_SEPARATOR_TAG, useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun rowsAre140RpAndColumnsStartAtThePaddingAndTheCentre() {
        show(sampleResults(2))
        val box = rule.onNodeWithTag(RESULTS_BOX_TAG).fetchSemanticsNode().boundsInRoot
        val pxPerRp = box.width / 778f
        val rows = rule.onAllNodesWithTag(RESULTS_ROW_TAG).fetchSemanticsNodes().map { it.boundsInRoot }
        assertEquals(140 * pxPerRp, rows[0].height, 1f)
        assertEquals(box.top + 20 * pxPerRp, rows[0].top, 1f)
        assertEquals(rows[0].bottom, rows[1].top, 1f)
        val score = rule.onNodeWithText("Score: 10000", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val time = rule.onNodeWithText("Time: 4M 05S", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(box.left + 40 * pxPerRp, score.left, 1f)
        assertEquals(box.left + 389 * pxPerRp, time.left, 1f)
        assertTrue(score.top >= rows[0].top && time.bottom <= rows[0].bottom)
    }

    @Test
    fun scrollsToTheTwentiethRow() {
        val results = sampleResults(20)
        show(results)
        val last = resultDescription(results.last())
        rule.onNodeWithContentDescription(last).assertDoesNotExist()
        rule.onNodeWithTag(RESULTS_BOX_TAG).performScrollToIndex(19)
        rule.waitForIdle()
        rule.onNodeWithContentDescription(last).assertIsDisplayed()
        rule.onNodeWithText("Score: ${results.last().score}").assertIsDisplayed()
        // The last row has no separator below it, and the box clips the rows.
        val box = rule.onNodeWithTag(RESULTS_BOX_TAG).fetchSemanticsNode().boundsInRoot
        val row = rule.onNodeWithContentDescription(last).fetchSemanticsNode().boundsInRoot
        assertTrue(row.bottom <= box.bottom + 1f)
    }

    @Test
    fun chevronsHideAtTheEndsAndStepTheMode() {
        show(emptyList(), GameMode.NORMAL)
        rule.onNodeWithText("Normal").assertExists()
        rule.onNodeWithContentDescription("Previous mode").assertDoesNotExist()
        rule.onNodeWithContentDescription("Next mode").assertHasClickAction().performClick()
        rule.waitForIdle()
        assertEquals(listOf(1), steps)
        rule.onNodeWithText(GameMode.PLUS.label).assertExists()
        rule.onNodeWithContentDescription("Next mode").assertDoesNotExist()
        rule.onNodeWithContentDescription("Previous mode").assertHasClickAction().performClick()
        rule.waitForIdle()
        assertEquals(listOf(1, -1), steps)
        rule.onNodeWithText("Normal").assertExists()
        rule.onNodeWithContentDescription("Back").performClick()
        rule.waitForIdle()
        assertEquals(1, backs)
    }
}

/** Results screen through AppRoot: a finished game shows up in its mode's list (section 16). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class ResultsFlowTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    /** One RIGHT fills the only gap and leaves no merges: game over. */
    private val lastMove = Board.of(
        listOf(
            listOf(8, 16, 8, 16),
            listOf(16, 8, 16, 8),
            listOf(8, 16, 8, 16),
            listOf(32, 64, 128, 0),
        ),
    )

    @Test
    fun overGameAppearsInResultsAndModeSwitchLeavesHomeMode() {
        val clock = FakeClock()
        val vm = AppViewModel(Random(7), clock, SavedStateHandle(), InMemorySettingsStore(), InMemoryGameStore())
        rule.setApp(vm)
        rule.runOnUiThread {
            vm.startNewGame()
            vm.restoreGame(lastMove, score = 300, status = GameStatus.PLAYING, moves = 4)
            clock.advance(65_000)
            vm.move(Direction.RIGHT)
        }
        rule.waitForIdle()
        assertEquals(GameStatus.OVER, vm.game.status)
        val result = vm.results(GameMode.NORMAL).single()

        rule.runOnUiThread { vm.goHome() }
        rule.waitForIdle()
        assertEquals(Screen.HOME, vm.screen)

        rule.mainClock.autoAdvance = false
        rule.onNodeWithContentDescription("Leaderboard").performClick()
        rule.mainClock.advanceTimeBy(200)
        rule.waitForIdle()
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertEquals(Screen.RESULTS, vm.screen)

        rule.onNodeWithText("Normal").assertExists()
        rule.onNodeWithContentDescription(resultDescription(result)).assertIsDisplayed()
        rule.onNodeWithText("Score: 300", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Tile: 128", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Moves: 5", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("No results yet.").assertDoesNotExist()

        rule.onNodeWithContentDescription("Next mode").performClick()
        rule.waitForIdle()
        assertEquals(GameMode.PLUS, vm.resultsMode)
        assertEquals(GameMode.NORMAL, vm.mode)
        rule.onNodeWithText(GameMode.PLUS.label).assertExists()
        rule.onNodeWithText("No results yet.").assertIsDisplayed()
        rule.onNodeWithContentDescription(resultDescription(result)).assertDoesNotExist()

        rule.onNodeWithContentDescription("Previous mode").performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription(resultDescription(result)).assertExists()

        rule.onNodeWithContentDescription("Back").performClick()
        rule.waitForIdle()
        assertEquals(Screen.HOME, vm.screen)
        assertEquals(GameMode.NORMAL, vm.mode)
        rule.onNodeWithText("New Game").assertExists()
    }
}

/** Longest row values at the 1.3 font-scale clamp: no overlap with the second column (sections 1, 16). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class ResultsFontScaleTest {

    @get:Rule
    val rule = createComposeRule()

    private fun SemanticsNodeInteraction.layout(): TextLayoutResult {
        val out = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(out)
        return out.single()
    }

    @Test
    fun longestValuesDoNotOverlapAt130() {
        val worst = GameResult(1, 9_999_999, 131_072, 99_999, 99 * 3_600_000L + 59 * 60_000L + 59_000L)
        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, 1.3f)) {
                RefFrame { ResultsScreen(GameMode.NORMAL, {}, listOf(worst, worst.copy(id = 2)), {}) }
            }
        }
        rule.waitForIdle()
        val box = rule.onNodeWithTag(RESULTS_BOX_TAG).fetchSemanticsNode().boundsInRoot
        val pxPerRp = box.width / 778f
        val col2 = box.left + 389 * pxPerRp
        val innerRight = box.right - 40 * pxPerRp
        val row = rule.onAllNodesWithTag(RESULTS_ROW_TAG)[0].fetchSemanticsNode().boundsInRoot

        fun measure(text: String): Pair<Float, Float> {
            val node = rule.onAllNodes(hasText(text), useUnmergedTree = true)[0]
            val l = node.layout()
            val left = node.fetchSemanticsNode().boundsInRoot.left
            val glyphW = l.getLineRight(0) - l.getLineLeft(0)
            val bounds = node.fetchSemanticsNode().boundsInRoot
            println(
                "results '$text': left ${(left - box.left) / pxPerRp} rp, glyphs ${glyphW / pxPerRp} rp, " +
                    "right ${(left + glyphW - box.left) / pxPerRp} rp (box-relative), lines ${l.lineCount}, " +
                    "top ${(bounds.top - row.top) / pxPerRp} bottom ${(bounds.bottom - row.top) / pxPerRp} rp (row-relative)",
            )
            assertEquals(1, l.lineCount)
            assertTrue("'$text' outside its row", bounds.top >= row.top - 0.5f && bounds.bottom <= row.bottom + 0.5f)
            return left to left + glyphW
        }

        println("results box $box, ${pxPerRp} px/rp, col2 ${col2 - box.left} px, row $row")
        listOf("Score: 9999999", "Moves: 99999").forEach {
            val (_, right) = measure(it)
            assertTrue("'$it' reaches the second column", right <= col2)
        }
        listOf("Tile: 131072", "Time: 99H 59M 59S").forEach {
            val (left, right) = measure(it)
            assertEquals(col2, left, 1f)
            assertTrue("'$it' passes the right padding", right <= innerRight)
        }
    }
}
