package com.apocalypse03.game2048.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextLayoutResult
import com.apocalypse03.game2048.game.Board
import com.apocalypse03.game2048.game.Direction
import com.apocalypse03.game2048.game.GameStatus
import com.apocalypse03.game2048.ui.game.BOARD_TAG
import com.apocalypse03.game2048.ui.game.GAME_TAG
import com.apocalypse03.game2048.ui.game.WIN_TAG
import com.apocalypse03.game2048.ui.game.winMessage
import com.apocalypse03.game2048.ui.timer.FakeClock
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private val z4 = listOf(0, 0, 0, 0)

/** UP moves the lone 2 (move 1, timer starts); LEFT then merges 1024 + 1024 (move 2). */
internal val WinPair4 = Board.of(listOf(listOf(1024, 1024, 0, 0), z4, z4, listOf(0, 0, 0, 2)))

/** Plays [WinPair4] to a win: 2 moves in 5 s of game time. Call on the UI thread. */
internal fun AppViewModel.playToWin(clock: FakeClock) {
    restoreGame(WinPair4)
    move(Direction.UP)
    clock.advance(5_000)
    move(Direction.LEFT)
}

/** Win screen through the UI (DESIGN_SPEC section 13). */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class WinScreenUiTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val clock = FakeClock()
    private val message = "You unlocked the 2048 tile with 2 moves in 5S."

    private fun idle() = rule.waitForIdle()
    private val overlay get() = rule.onNodeWithTag(WIN_TAG)
    private val board get() = rule.onNodeWithTag(BOARD_TAG)
    private fun button(label: String) = rule.onNodeWithContentDescription(label)

    private fun wonGame(): AppViewModel {
        val vm = AppViewModel(Random(3), clock).apply { startNewGame() }
        rule.setContent { AppRoot(vm) }
        idle()
        rule.runOnUiThread { vm.playToWin(clock) }
        idle()
        return vm
    }

    private fun back() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        idle()
    }

    @Test
    fun messageWording() {
        assertEquals("You unlocked the 2048 tile with 1 move in 42S.", winMessage(1, "42S"))
        assertEquals("You unlocked the 2048 tile with 37 moves in 1M 05S.", winMessage(37, "1M 05S"))
    }

    @Test
    fun overlayShowsTextsButtonsAndBlocksInput() {
        val vm = wonGame()
        overlay.assertExists()
        rule.onNodeWithText("You Win!").assertExists()
        rule.onNodeWithText(message).assertExists()
        button("Endless mode").assertHasClickAction()
        button("New Game").assertHasClickAction()
        button("Undo").assertIsNotEnabled()
        rule.onNodeWithText("Swipe to begin.").assertDoesNotExist()
        val before = vm.game
        board.performTouchInput { swipeRight() }
        board.performTouchInput { swipeDown() }
        rule.onNodeWithTag(GAME_TAG).performKeyInput { pressKey(Key.DirectionDown) }
        idle()
        assertEquals(before, vm.game)
        assertTrue(vm.winPending)
    }

    @Test
    fun endlessRemovesOverlayAndSwipesWork() {
        val vm = wonGame()
        button("Endless mode").performClick()
        idle()
        overlay.assertDoesNotExist()
        rule.onNodeWithText("You Win!").assertDoesNotExist()
        assertFalse(vm.winPending)
        assertTrue(vm.timerRunning)
        val swipes = listOf<() -> Unit>(
            { board.performTouchInput { swipeDown() } },
            { board.performTouchInput { swipeRight() } },
            { board.performTouchInput { swipeUp() } },
            { board.performTouchInput { swipeLeft() } },
        )
        for (s in swipes) {
            s()
            idle()
            if (vm.game.moves > 2) break
        }
        assertTrue("no swipe moved after Endless", vm.game.moves > 2)
        overlay.assertDoesNotExist()
    }

    @Test
    fun newGameNoKeepsOverlayYesStartsFresh() {
        val vm = wonGame()
        button("New Game").performClick()
        idle()
        rule.onNodeWithTag("confirm_dialog").assertExists()
        button("No").performClick()
        idle()
        rule.onNodeWithTag("confirm_dialog").assertDoesNotExist()
        overlay.assertExists()
        rule.onNodeWithText(message).assertExists()
        // System back on the dialog closes only the dialog.
        button("New Game").performClick()
        idle()
        back()
        rule.onNodeWithTag("confirm_dialog").assertDoesNotExist()
        assertEquals(Screen.GAME, vm.screen)
        overlay.assertExists()
        button("New Game").performClick()
        idle()
        button("Yes").performClick()
        idle()
        overlay.assertDoesNotExist()
        assertEquals(Screen.GAME, vm.screen)
        assertEquals(GameStatus.READY, vm.game.status)
        assertEquals(0, vm.game.moves)
        rule.onNodeWithText("Swipe to begin.").assertExists()
        rule.onNodeWithContentDescription("Elapsed time 0S").assertExists()
    }

    @Test
    fun backGoesHomeWithResumeThatReopensTheWin() {
        val vm = wonGame()
        back()
        assertEquals(Screen.HOME, vm.screen)
        assertTrue(vm.winPending)
        button("Resume").assertExists().performClick()
        idle()
        assertEquals(Screen.GAME, vm.screen)
        overlay.assertExists()
        rule.onNodeWithText(message).assertExists()
    }
}

/** Win screen labels fit at the 1.3 font-scale clamp (DESIGN_SPEC sections 1 and 13). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class WinScreenFontScaleTest {

    @get:Rule
    val rule = createComposeRule()

    private fun SemanticsNodeInteraction.layout(): TextLayoutResult {
        val out = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(out)
        return out.single()
    }

    @Test
    fun labelsFitAt130() {
        val clock = FakeClock()
        val vm = AppViewModel(Random(3), clock).apply {
            startNewGame()
            // Worst case for the summary: many moves and the longest time format.
            restoreGame(WinPair4, moves = 99_997)
            move(Direction.UP)
            clock.advance(99 * 3_600_000L + 59 * 60_000L + 59_000L)
            move(Direction.LEFT)
        }
        rule.setAppWithFontScale(vm, mutableFloatStateOf(1.3f))
        val boardBounds = rule.onNodeWithTag(BOARD_TAG).fetchSemanticsNode().boundsInRoot
        val pxPerRp = boardBounds.width / 778f

        // Buttons: one line, glyphs inside the 330 rp pill minus its 3 rp strokes.
        listOf("Endless mode", "New Game").forEach { label ->
            val l = rule.onNodeWithText(label, useUnmergedTree = true).layout()
            val glyphW = l.getLineRight(0) - l.getLineLeft(0)
            println("win button '$label': glyphs $glyphW px, inner ${324 * pxPerRp} px")
            assertEquals(1, l.lineCount)
            assertTrue("'$label' clipped", glyphW <= l.size.width + 0.5f && l.multiParagraph.height <= l.size.height + 0.5f)
            assertTrue("'$label' wider than the pill", glyphW <= 324 * pxPerRp)
        }

        // Title and summary: unclipped, inside the board rect, centred on its centre.
        val title = rule.onNodeWithText("You Win!", useUnmergedTree = true)
        val msgText = winMessage(99_999, "99H 59M 59S")
        val msg = rule.onNodeWithText(msgText, useUnmergedTree = true)
        val t = title.layout()
        val m = msg.layout()
        assertEquals(1, t.lineCount)
        assertTrue(t.multiParagraph.height <= t.size.height + 0.5f)
        assertTrue(m.multiParagraph.height <= m.size.height + 0.5f)
        (0 until m.lineCount).forEach { assertTrue(m.getLineRight(it) - m.getLineLeft(it) <= 700 * pxPerRp + 0.5f) }
        val top = title.fetchSemanticsNode().boundsInRoot.top
        val bottom = msg.fetchSemanticsNode().boundsInRoot.bottom
        println("win text block $top..$bottom px (${m.lineCount} summary lines), board $boardBounds")
        assertTrue(top >= boardBounds.top && bottom <= boardBounds.bottom)
        assertEquals(boardBounds.center.y, (top + bottom) / 2f, 2f)
    }
}
