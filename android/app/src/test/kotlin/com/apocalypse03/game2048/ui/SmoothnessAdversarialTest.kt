package com.apocalypse03.game2048.ui

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.apocalypse03.game2048.game.Board
import com.apocalypse03.game2048.game.Direction
import com.apocalypse03.game2048.game.GameStatus
import com.apocalypse03.game2048.ui.timer.FakeClock
import kotlin.math.abs
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Adversarial checks of window-wide swipes and the move animation (independent tester).
 * Gestures are injected on the window root so they exercise AppRoot's swipeToMove, not a board
 * handler.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class SmoothnessAdversarialTest {

    @get:Rule
    val rule = createComposeRule()

    private val z = listOf(0, 0, 0, 0)

    /** One 2 at (1,1): every direction is a valid move. */
    private val single = Board.of(listOf(z, listOf(0, 2, 0, 0), z, z))

    /** 2,2 at (0,2),(0,3): LEFT merges into a 4 at (0,0). */
    private val pair = Board.of(listOf(listOf(0, 0, 2, 2), z, z, z))

    /** Full board without any legal move. */
    private val dead = Board.of(
        listOf(listOf(2, 4, 2, 4), listOf(4, 2, 4, 2), listOf(2, 4, 2, 4), listOf(4, 2, 4, 2)),
    )

    private var n = 4
    private lateinit var rest: Map<Pair<Int, Int>, Rect>

    private fun vm(speed: Int = 100, clock: FakeClock? = null): AppViewModel =
        (if (clock != null) AppViewModel(Random(3), clock) else AppViewModel(Random(3))).apply {
            setAnimSpeed(speed)
        }

    /** Shows a game on [board] and records every cell's rest bounds. */
    private fun game(board: Board, speed: Int = 100, status: GameStatus = GameStatus.READY): AppViewModel {
        val vm = vm(speed).apply { startNewGame() }
        if (status == GameStatus.READY) rule.setApp(vm, board) else {
            vm.restoreGame(board, status = status, moves = 3)
            rule.setApp(vm)
        }
        n = board.size
        rest = cells().associateWith { bounds(it.first, it.second) }
        return vm
    }

    private fun cells() = (0 until n).flatMap { r -> (0 until n).map { c -> r to c } }

    private fun bounds(r: Int, c: Int): Rect =
        rule.onNodeWithTag("tile_${r}_$c").fetchSemanticsNode().boundsInRoot

    private fun text(r: Int, c: Int): String =
        rule.onAllNodes(hasAnyAncestor(hasTestTag("tile_${r}_$c")) and hasText("", substring = true))
            .fetchSemanticsNodes()
            .flatMap { it.config.getOrElse(SemanticsProperties.Text) { emptyList() } }
            .joinToString("") { it.text }

    private fun frame(ms: Long = 16) {
        Snapshot.sendApplyNotifications()
        rule.mainClock.advanceTimeBy(ms)
    }

    private fun assertShowsBoard(vm: AppViewModel) {
        val b = vm.game.board
        for ((r, c) in cells()) {
            assertEquals("cell $r,$c", if (b[r, c] == 0) "" else b[r, c].toString(), text(r, c))
            assertEquals("bounds $r,$c", rest.getValue(r to c), bounds(r, c))
        }
    }

    private fun TouchInjectionScope.px(dp: Float): Float = dp.dp.toPx()

    /** A 3-step drag on the window root from ([fx],[fy]) of its size by ([dxDp],[dyDp]) dp. */
    private fun rootSwipe(fx: Float, fy: Float, dxDp: Float, dyDp: Float) {
        rule.onRoot().performTouchInput {
            down(Offset(width * fx, height * fy))
            repeat(3) { moveBy(Offset(px(dxDp / 3), px(dyDp / 3)), delayMillis = 16) }
            up()
        }
    }

    /** Twin VM with the same seed and the same start, driven directly: the expected result. */
    private fun twin(board: Board, vararg dirs: Direction): AppViewModel =
        vm().apply {
            startNewGame()
            restoreGame(board)
            dirs.forEach { move(it) }
        }

    // ---- C1 diagonal ----

    @Test
    fun diagonalSwipeFollowsDominantAxisOnce() {
        val vm = game(single)
        rootSwipe(0.5f, 0.3f, dxDp = 60f, dyDp = -45f)
        rule.waitForIdle()
        assertEquals(1, vm.game.moves)
        assertEquals(twin(single, Direction.RIGHT).game.board, vm.game.board)
    }

    @Test
    fun exactDiagonalTieGoesHorizontalOnce() {
        val vm = game(single)
        rootSwipe(0.5f, 0.3f, dxDp = -45f, dyDp = 45f)
        rule.waitForIdle()
        assertEquals(1, vm.game.moves)
        assertEquals(twin(single, Direction.LEFT).game.board, vm.game.board)
    }

    // ---- threshold is in dp, not px (default Robolectric density is 1, which hides that) ----

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun thresholdIsDensityIndependent() {
        val vm = game(single)
        // 15 dp = 45 px at xxhdpi, slowly: past a 20 px threshold, under the 20 dp one.
        rule.onRoot().performTouchInput {
            assertEquals(3f, density, 0.01f)
            down(Offset(width * 0.5f, height * 0.15f))
            repeat(3) { moveBy(Offset(px(5f), 0f), delayMillis = 200) }
            up()
        }
        rule.waitForIdle()
        assertEquals(0, vm.game.moves)
        rootSwipe(0.5f, 0.15f, dxDp = 30f, dyDp = 0f)
        rule.waitForIdle()
        assertEquals(1, vm.game.moves)
    }

    @Test
    fun flickBackAgainstTravelDoesNotFire() {
        val vm = game(single)
        rule.onRoot().performTouchInput {
            down(Offset(width * 0.5f, height * 0.85f))
            // Peak travel 19 dp left (never the 20 dp threshold), then a fast burst back to the right.
            repeat(2) { moveBy(Offset(-px(9.5f), 0f), delayMillis = 8) }
            repeat(7) { moveBy(Offset(px(1f), 0f), delayMillis = 3) }
            // Net travel 12 dp left (over 10 dp); least-squares release velocity about +980 dp/s.
            up()
        }
        rule.waitForIdle()
        assertEquals(0, vm.game.moves)
    }

    /** Control for [flickBackAgainstTravelDoesNotFire]: a late fast burst along travel fires. */
    @Test
    fun flickBurstAlongTravelFires() {
        val vm = game(single)
        rule.onRoot().performTouchInput {
            down(Offset(width * 0.5f, height * 0.85f))
            // 14 dp left; least-squares release velocity about -470 dp/s (along travel).
            repeat(2) { moveBy(Offset(-px(2f), 0f), delayMillis = 30) }
            repeat(4) { moveBy(Offset(-px(2.5f), 0f), delayMillis = 2) }
            up()
        }
        rule.waitForIdle()
        assertEquals(1, vm.game.moves)
        assertEquals(2, vm.game.board[1, 0])
    }

    /** A 15 dp flick at 1000 dp/s (below the drag threshold) starting on the node with [desc]. */
    private fun flickOn(desc: String) {
        rule.onNodeWithContentDescription(desc, substring = true).performTouchInput {
            down(Offset(px(4f), centerY))
            repeat(3) { moveBy(Offset(px(5f), 0f), delayMillis = 5) }
            up()
        }
        rule.waitForIdle()
    }

    @Test
    fun flickInEmptyAreaFires() {
        val vm = game(single)
        rule.onRoot().performTouchInput {
            down(Offset(width * 0.5f, height * 0.85f))
            repeat(3) { moveBy(Offset(px(5f), 0f), delayMillis = 5) }
            up()
        }
        rule.waitForIdle()
        assertEquals(1, vm.game.moves)
        assertEquals(2, vm.game.board[1, 3])
    }

    // A flick shorter than the 20 dp swipe threshold that starts on a button is a sloppy tap:
    // the button wins and the board does not move (a full swipe on a button moves instead).
    @Test
    fun shortFlickStartingOnBackTapsBackAndDoesNotMove() {
        val vm = game(single)
        flickOn("Back")
        assertEquals("short flick on Back is a tap", Screen.HOME, vm.screen)
        assertEquals(0, vm.game.moves)
    }

    @Test
    fun shortFlickStartingOnUndoUndoesAndDoesNotMove() {
        val vm = game(single)
        rootSwipe(0.5f, 0.85f, dxDp = -60f, dyDp = 0f)
        rule.waitForIdle()
        assertEquals(1, vm.game.moves)
        flickOn("Undo")
        assertEquals("short flick on Undo is a tap", 0, vm.game.moves)
    }

    @Test
    fun shortFlickStartingOnPaletteToggleOpensBarAndDoesNotMove() {
        val vm = game(single)
        flickOn("Palette")
        rule.onNodeWithContentDescription("Theme Lavender").assertExists()
        assertEquals(0, vm.game.moves)
    }

    // ---- C2 letterbox (bottom band; the top band is covered by FullScreenSwipeTest) ----

    @Test
    fun swipeInBottomLetterboxMoves() {
        val vm = game(single)
        rule.onRoot().performTouchInput {
            val y = height - 5f
            down(Offset(width * 0.7f, y))
            repeat(3) { moveBy(Offset(-px(20f), 0f), delayMillis = 16) }
            up()
        }
        rule.waitForIdle()
        assertEquals(1, vm.game.moves)
        assertEquals(2, vm.game.board[1, 0])
    }

    // ---- C3 two rapid swipes ----

    @Test
    fun twoRapidSwipesMakeTwoMovesAndEndAtRest() {
        val vm = game(single)
        rule.mainClock.autoAdvance = false
        rootSwipe(0.5f, 0.85f, dxDp = -60f, dyDp = 0f)
        rootSwipe(0.5f, 0.85f, dxDp = 0f, dyDp = 60f)
        assertEquals(2, vm.game.moves)
        assertEquals(twin(single, Direction.LEFT, Direction.DOWN).game.board, vm.game.board)
        frame()
        frame(600)
        assertShowsBoard(vm)
    }

    // ---- C4 second move mid-slide / mid-settle ----

    private fun secondMoveAt(delayMs: Long) {
        val vm = game(pair)
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread { vm.move(Direction.LEFT) }
        frame()
        frame(delayMs)
        assertNotNull(vm.moveAnimation)
        rule.runOnUiThread { vm.move(Direction.DOWN) }
        frame()
        // Restarted from the new trace: the 4 now slides from (0,0) toward (3,0).
        val start = bounds(3, 0).top
        assertTrue(
            "restart: tile at ${start}, old cell ${rest.getValue(0 to 0).top}",
            abs(start - rest.getValue(0 to 0).top) < 2f,
        )
        frame(600)
        assertShowsBoard(vm)
        assertEquals(4, vm.game.board[3, 0])
    }

    @Test
    fun secondMoveMidSlideEndsAtRest() = secondMoveAt(50)

    @Test
    fun secondMoveMidSettleEndsAtRest() = secondMoveAt(180)

    @Test
    fun secondSwipeMidSettleThroughGestureEndsAtRest() {
        val vm = game(pair)
        rule.mainClock.autoAdvance = false
        rootSwipe(0.5f, 0.2f, dxDp = -60f, dyDp = 0f)
        frame()
        frame(150)
        rootSwipe(0.5f, 0.2f, dxDp = 0f, dyDp = 60f)
        assertEquals(2, vm.game.moves)
        frame()
        frame(600)
        assertShowsBoard(vm)
    }

    // ---- C5 multi-touch ----

    @Test
    fun secondFingerDuringSwipeMakesNoSecondMove() {
        val vm = game(single)
        rule.onRoot().performTouchInput {
            val a = Offset(width * 0.3f, height * 0.85f)
            val b = Offset(width * 0.7f, height * 0.2f)
            down(0, a)
            moveBy(0, Offset(px(30f), 0f), delayMillis = 16)
            down(1, b)
            moveBy(1, Offset(0f, px(80f)), delayMillis = 16)
            moveBy(1, Offset(0f, px(80f)), delayMillis = 16)
            up(1)
            moveBy(0, Offset(px(30f), 0f), delayMillis = 16)
            up(0)
        }
        rule.waitForIdle()
        assertEquals(1, vm.game.moves)
        assertEquals(twin(single, Direction.RIGHT).game.board, vm.game.board)
    }

    @Test
    fun secondFingerAloneMovingDoesNotMoveWhileFirstIsStill() {
        val vm = game(single)
        rule.onRoot().performTouchInput {
            down(0, Offset(width * 0.3f, height * 0.85f))
            down(1, Offset(width * 0.7f, height * 0.2f))
            repeat(3) { moveBy(1, Offset(0f, px(40f)), delayMillis = 16) }
            up(1)
            up(0)
        }
        rule.waitForIdle()
        // Only the first pointer is tracked (by design): a two-finger gesture where the second
        // finger swipes does not move.
        assertEquals(0, vm.game.moves)
    }

    @Test
    fun firstFingerLiftsSecondKeepsSwipingNoMove() {
        val vm = game(single)
        rule.onRoot().performTouchInput {
            down(0, Offset(width * 0.3f, height * 0.85f))
            down(1, Offset(width * 0.7f, height * 0.2f))
            up(0)
            repeat(3) { moveBy(1, Offset(px(-40f), 0f), delayMillis = 16) }
            up(1)
        }
        rule.waitForIdle()
        assertEquals(0, vm.game.moves)
    }

    // ---- C6 leaving the game ----

    @Test
    fun swipeRightAfterBackDoesNotMove() {
        val vm = game(single)
        rule.mainClock.autoAdvance = false
        rule.onNodeWithContentDescription("Back").performClick()
        frame()
        // Mid exit transition: the game screen is still on screen, fading out.
        rootSwipe(0.5f, 0.15f, dxDp = -60f, dyDp = 0f)
        frame(400)
        assertEquals(Screen.HOME, vm.screen)
        assertEquals(0, vm.game.moves)
    }

    @Test
    fun goHomeMidGestureCancelsTheSwipe() {
        val vm = game(single)
        rule.onRoot().performTouchInput {
            down(Offset(width * 0.5f, height * 0.15f))
            moveBy(Offset(px(5f), 0f), delayMillis = 16)
        }
        rule.runOnIdle { vm.goHome() }
        rule.waitForIdle()
        rule.onRoot().performTouchInput {
            moveBy(Offset(px(60f), 0f), delayMillis = 16)
            up()
        }
        rule.waitForIdle()
        assertEquals(0, vm.game.moves)
    }

    // ---- C7 swipe during the 250 ms transition into the game ----

    @Test
    fun swipeDuringTransitionIntoGame() {
        val vm = vm()
        rule.setApp(vm)
        assertEquals(Screen.HOME, vm.screen)
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread {
            vm.startNewGame()
            vm.restoreGame(single)
        }
        frame()
        frame(50)
        rootSwipe(0.5f, 0.15f, dxDp = -60f, dyDp = 0f)
        frame(400)
        // Observed contract: screen == GAME already, so the swipe applies exactly once.
        assertEquals(Screen.GAME, vm.screen)
        assertEquals(1, vm.game.moves)
        assertEquals(2, vm.game.board[1, 0])
    }

    // ---- C8 modals / overlays ----

    @Test
    fun swipeUnderConfirmNewGameDialogDoesNotMove() {
        val vm = game(single, status = GameStatus.PLAYING)
        rule.runOnIdle { vm.requestNewGame() }
        rule.waitForIdle()
        assertTrue(vm.confirmNewGame)
        val before = vm.game.board
        // On the dialog body (window centre).
        rootSwipe(0.5f, 0.5f, dxDp = 0f, dyDp = 60f)
        rule.waitForIdle()
        assertEquals(3, vm.game.moves)
        assertEquals(before, vm.game.board)
        val openAfterBodySwipe = vm.confirmNewGame
        // On the dim scrim (top of the window).
        rootSwipe(0.5f, 0.15f, dxDp = -60f, dyDp = 0f)
        rule.waitForIdle()
        assertEquals(3, vm.game.moves)
        assertEquals(before, vm.game.board)
        println("TESTER confirmNewGame after body swipe=$openAfterBodySwipe, after scrim swipe=${vm.confirmNewGame}")
    }

    @Test
    fun swipeUnderWinOverlayDoesNotMove() {
        val vm = vm().apply { startNewGame() }
        vm.restoreGame(single, status = GameStatus.PLAYING, moves = 7, won = true, winPending = true)
        rule.setApp(vm)
        rootSwipe(0.5f, 0.5f, dxDp = -60f, dyDp = 0f)
        rootSwipe(0.5f, 0.15f, dxDp = 0f, dyDp = 60f)
        rule.waitForIdle()
        assertTrue(vm.winPending)
        assertEquals(7, vm.game.moves)
        assertEquals(single, vm.game.board)
    }

    // ---- C9 princess ----

    @Test
    fun princessSwipeThenTapSelects() {
        val clock = FakeClock()
        val row = Board.of(listOf(listOf(2, 4, 8, 0), z, z, z))
        val vm = vm(clock = clock).apply { startNewGame() }
        rule.setApp(vm, row)
        val timer = rule.onNodeWithContentDescription("Elapsed time", substring = true)
        repeat(5) {
            timer.performClick()
            clock.advance(300)
        }
        rule.waitForIdle()
        assertTrue(vm.game.princess)
        rootSwipe(0.5f, 0.15f, dxDp = 60f, dyDp = 0f)
        // Swipe starting on a tile, too.
        rule.onNodeWithTag("tile_0_0").performTouchInput {
            down(center)
            repeat(3) { moveBy(Offset(px(20f), 0f), delayMillis = 16) }
            up()
        }
        rule.waitForIdle()
        assertEquals(row, vm.game.board)
        assertEquals("a swipe is not a tap", null, vm.selected)
        rule.onNodeWithTag("tile_0_1").performTouchInput { click() }
        rule.waitForIdle()
        assertEquals(1, vm.selected)
    }

    // ---- C10 animation speed ----

    @Test
    fun speed150ScalesDurationsAndEndsAtRest() {
        val vm = game(pair, speed = 150)
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread { vm.move(Direction.LEFT) }
        frame()
        // At 150 %: slide 180 ms, settle 225 ms. ~140 ms in: still sliding (the old value shows).
        frame(124)
        assertEquals("still sliding at 150 %", "2", text(0, 0))
        // ~330 ms in: settle phase, still popping (at 100 % it would be over at 270 ms).
        frame(190)
        assertEquals("4", text(0, 0))
        assertTrue("still popping", bounds(0, 0).width > rest.getValue(0 to 0).width)
        frame(200)
        assertShowsBoard(vm)
    }

    @Test
    fun lowSpeedEndsAtRestQuickly() {
        val vm = game(pair, speed = 5)
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread { vm.move(Direction.LEFT) }
        frame()
        // 6 + 8 ms total.
        frame(48)
        assertShowsBoard(vm)
    }

    @Test
    fun speedOneEndsAtRest() {
        val vm = game(pair, speed = 1)
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread { vm.move(Direction.LEFT) }
        frame()
        frame(32)
        assertShowsBoard(vm)
    }

    // ---- C11 game over ----

    @Test
    fun gameOverBoardIgnoresSwipes() {
        val vm = game(dead, status = GameStatus.OVER)
        for ((dx, dy) in listOf(-60f to 0f, 60f to 0f, 0f to -60f, 0f to 60f)) rootSwipe(0.5f, 0.85f, dx, dy)
        rule.waitForIdle()
        assertEquals(GameStatus.OVER, vm.game.status)
        assertEquals(3, vm.game.moves)
        assertEquals(dead, vm.game.board)
        assertFalse(vm.game.canUndo)
    }

    // ---- C12 undo mid-animation ----

    @Test
    fun undoMidAnimationShowsStaticRestoredBoard() {
        val vm = game(pair)
        rule.mainClock.autoAdvance = false
        rootSwipe(0.5f, 0.85f, dxDp = -60f, dyDp = 0f)
        assertEquals(1, vm.game.moves)
        frame()
        frame(40)
        rule.onNodeWithContentDescription("Undo").performClick()
        frame()
        assertEquals(pair, vm.game.board)
        assertShowsBoard(vm)
        frame(600)
        assertShowsBoard(vm)
    }

    @Test
    fun undoMidSettleShowsStaticRestoredBoard() {
        val vm = game(pair)
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread { vm.move(Direction.LEFT) }
        frame()
        frame(170)
        rule.runOnUiThread { vm.undo() }
        frame()
        assertShowsBoard(vm)
    }

    // ---- C13 6x6 ----

    @Test
    fun sixBySixSwipeFromOutsideTheBoardMoves() {
        val z6 = List(6) { 0 }
        val board6 = Board.of(listOf(z6, listOf(0, 0, 2, 0, 0, 0), z6, z6, z6, z6))
        val vm = vm().apply {
            stepMode(1)
            startNewGame()
        }
        assertEquals(6, vm.game.size)
        rule.setApp(vm, board6)
        n = 6
        rest = cells().associateWith { bounds(it.first, it.second) }
        rule.mainClock.autoAdvance = false
        rootSwipe(0.5f, 0.15f, dxDp = 60f, dyDp = 0f)
        assertEquals(1, vm.game.moves)
        assertEquals(2, vm.game.board[1, 5])
        frame()
        frame(600)
        assertShowsBoard(vm)
    }
}
