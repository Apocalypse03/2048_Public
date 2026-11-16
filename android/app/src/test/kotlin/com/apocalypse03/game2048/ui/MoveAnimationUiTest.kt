package com.apocalypse03.game2048.ui

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import com.apocalypse03.game2048.game.Board
import com.apocalypse03.game2048.game.Direction
import com.apocalypse03.game2048.ui.game.BOARD_TAG
import kotlin.math.abs
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Board move animation (DESIGN_SPEC section 17), driven by the test clock. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class MoveAnimationUiTest {

    @get:Rule
    val rule = createComposeRule()

    private val z = listOf(0, 0, 0, 0)

    /** One 2 at (0,3). */
    private val single = Board.of(listOf(listOf(0, 0, 0, 2), z, z, z))

    /** 2,2 at (0,2),(0,3): LEFT merges them into a 4 at (0,0). */
    private val pair = Board.of(listOf(listOf(0, 0, 2, 2), z, z, z))

    private lateinit var rest: Map<Pair<Int, Int>, Rect>

    private fun game(board: Board, speed: Int = 100): AppViewModel {
        val vm = AppViewModel(Random(3)).apply {
            setAnimSpeed(speed)
            startNewGame()
        }
        rule.setApp(vm, board)
        rest = (0 until 4).flatMap { r -> (0 until 4).map { c -> r to c } }.associateWith { bounds(it.first, it.second) }
        rule.mainClock.autoAdvance = false
        return vm
    }

    private fun bounds(r: Int, c: Int): Rect =
        rule.onNodeWithTag("tile_${r}_$c").fetchSemanticsNode().boundsInRoot

    /** Text drawn in cell (r, c)'s tile node, or "" when empty. */
    private fun text(r: Int, c: Int): String =
        rule.onAllNodes(hasAnyAncestor(hasTestTag("tile_${r}_$c")) and hasText("", substring = true))
            .fetchSemanticsNodes()
            .flatMap { it.config.getOrElse(SemanticsProperties.Text) { emptyList() } }
            .joinToString("") { it.text }

    /** Applies VM state writes made on the test thread, then advances the clock by [ms]. */
    private fun frame(ms: Long = 16) {
        Snapshot.sendApplyNotifications()
        rule.mainClock.advanceTimeBy(ms)
    }

    /** Every cell shows the VM board at its rest position. */
    private fun assertShowsBoard(vm: AppViewModel) {
        val b = vm.game.board
        for (r in 0 until 4) for (c in 0 until 4) {
            assertEquals("cell $r,$c", if (b[r, c] == 0) "" else b[r, c].toString(), text(r, c))
            assertEquals("bounds $r,$c", rest.getValue(r to c), bounds(r, c))
        }
    }

    @Test
    fun slideMovesTileOverTime() {
        val vm = game(single)
        val from = rest.getValue(0 to 3).left
        val to = rest.getValue(0 to 0).left
        vm.move(Direction.LEFT)
        assertEquals(2, vm.game.board[0, 0]) // the game state is already final
        frame()
        // The tile node of the target cell starts at the old cell.
        assertEquals("2", text(0, 0))
        assertTrue("start ${bounds(0, 0).left} vs $from", abs(bounds(0, 0).left - from) < 1f)
        frame(50)
        val mid = bounds(0, 0).left
        assertTrue("mid $mid not between $to and $from", mid > to + 1f && mid < from - 1f)
        frame(300)
        assertEquals(to, bounds(0, 0).left)
        assertShowsBoard(vm)
    }

    @Test
    fun mergeShowsOldValuesWhileSlidingThenMergedValue() {
        val vm = game(pair)
        vm.move(Direction.LEFT)
        assertEquals(4, vm.game.board[0, 0])
        frame()
        frame(32)
        assertEquals("2", text(0, 0)) // still sliding: old value
        frame(300)
        assertEquals("4", text(0, 0))
        assertShowsBoard(vm)
    }

    @Test
    fun speedZeroIsInstant() {
        val vm = game(single, speed = 0)
        vm.move(Direction.LEFT)
        frame()
        assertShowsBoard(vm)
    }

    /** (row, col) of the tile spawned by the last move. */
    private fun spawnCell(vm: AppViewModel): Pair<Int, Int> =
        vm.moveAnimation!!.trace.spawn!!.let { it.row to it.col }

    @Test
    fun spawnedTileIsAbsentDuringTheSlideThenGrowsToRest() {
        val vm = game(single)
        vm.move(Direction.LEFT)
        val (r, c) = spawnCell(vm)
        val value = vm.game.board[r, c].toString()
        val restBounds = rest.getValue(r to c)
        frame()
        assertEquals("spawn cell at the start of the slide", "", text(r, c))
        frame(50)
        assertEquals("spawn cell mid slide", "", text(r, c))
        // About 166 ms in: the slide (120 ms) is over, the settle phase (150 ms) is in its first third.
        frame(100)
        assertEquals(value, text(r, c))
        val w = bounds(r, c).width
        assertTrue("spawn width $w, rest ${restBounds.width}", w < restBounds.width * 0.9f)
        assertTrue("spawn width $w not below 0.6 scale", w > restBounds.width * 0.59f)
        frame(500)
        assertEquals(restBounds, bounds(r, c))
        assertShowsBoard(vm)
    }

    @Test
    fun mergedTilePopsPastOneInTheSettlePhaseThenRests() {
        val vm = game(pair)
        val restBounds = rest.getValue(0 to 0)
        vm.move(Direction.LEFT)
        frame()
        frame(50)
        assertEquals("no pop while sliding", restBounds.width, bounds(0, 0).width, 0.01f)
        frame(100)
        // Settle phase: merged value, at its cell (same centre), larger than the cell.
        assertEquals("4", text(0, 0))
        val b = bounds(0, 0)
        assertTrue("pop width ${b.width}, rest ${restBounds.width}", b.width > restBounds.width * 1.03f)
        assertTrue("pop width ${b.width} past the 1.14 peak", b.width <= restBounds.width * 1.141f)
        assertTrue("pop centre", abs(b.center.x - restBounds.center.x) < 0.5f)
        frame(500)
        assertEquals(restBounds, bounds(0, 0))
        assertShowsBoard(vm)
    }

    @Test
    fun animationEndsAfterSlidePlusSettleTime() {
        val vm = game(pair)
        val restBounds = rest.getValue(0 to 0)
        vm.move(Direction.LEFT)
        frame()
        // 120 + 150 ms at 100 %: still popping at about 216 ms, at rest from about 316 ms.
        frame(200)
        assertTrue("still animating", bounds(0, 0).width > restBounds.width)
        frame(100)
        assertShowsBoard(vm)
    }

    @Test
    fun speedScalesBothPhases() {
        val vm = game(pair, speed = 50)
        vm.move(Direction.LEFT)
        frame()
        // 60 + 75 ms at 50 %: over well before the 270 ms of 100 %.
        frame(170)
        assertShowsBoard(vm)
    }

    @Test
    fun secondMoveMidAnimationEndsOnFinalBoard() {
        val vm = game(single)
        vm.move(Direction.LEFT)
        frame()
        frame(50)
        vm.move(Direction.DOWN)
        frame()
        frame(500)
        assertShowsBoard(vm)
    }

    @Test
    fun undoShowsNoAnimation() {
        val vm = game(single)
        vm.move(Direction.LEFT)
        frame()
        frame(500)
        vm.undo()
        frame()
        assertShowsBoard(vm)
        assertEquals("2", text(0, 3))
    }

    @Test
    fun swipeIsAcceptedDuringAnimation() {
        val vm = game(single)
        vm.move(Direction.LEFT)
        frame()
        frame(30)
        rule.onNodeWithTag(BOARD_TAG).performTouchInput { swipeDown() }
        // The swipe mid-animation is applied (the 2 at (0,0) moves down), not blocked or queued.
        assertEquals(2, vm.game.moves)
        frame(500)
        assertShowsBoard(vm)
    }
}
