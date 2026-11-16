package com.apocalypse03.game2048.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.apocalypse03.game2048.game.Board
import com.apocalypse03.game2048.ui.game.BOARD_TAG
import com.apocalypse03.game2048.ui.timer.FakeClock
import kotlin.random.Random
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class TimerLabelTest {

    @get:Rule
    val rule = createComposeRule()

    private val z = listOf(0, 0, 0, 0)
    private val row = Board.of(listOf(listOf(2, 2, 4, 8), z, z, z))

    @Test
    fun labelTicksWhilePlayingAndFreezesAtHome() {
        val clock = FakeClock()
        val vm = AppViewModel(Random(3), clock).apply { startNewGame() }
        rule.setApp(vm, row)
        rule.onNodeWithText("0S").assertExists()

        rule.onNodeWithTag(BOARD_TAG).performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertTrue(vm.timerRunning)

        clock.advance(3_000)
        rule.mainClock.advanceTimeBy(3_000)
        rule.onNodeWithText("3S").assertExists()

        clock.advance(62_000)
        rule.mainClock.advanceTimeBy(62_000)
        rule.onNodeWithText("1M 05S").assertExists()

        rule.runOnIdle { vm.goHome() }
        clock.advance(10_000)
        rule.mainClock.advanceTimeBy(10_000)
        rule.runOnIdle { vm.startNewGame() }
        rule.waitForIdle()
        rule.onNodeWithText("0S").assertExists()
    }

    @Test
    fun timerReadsElapsedTimeWithoutButtonRole() {
        val clock = FakeClock()
        val vm = AppViewModel(Random(3), clock).apply { startNewGame() }
        rule.setApp(vm, row)
        rule.onNodeWithTag(BOARD_TAG).performTouchInput { swipeLeft() }
        rule.waitForIdle()
        clock.advance(65_000)
        rule.mainClock.advanceTimeBy(65_000)

        rule.onNodeWithContentDescription("Elapsed time", substring = true)
            .assertContentDescriptionEquals("Elapsed time 1M 05S")
            .assert(hasClickAction())
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Role))
    }
}
