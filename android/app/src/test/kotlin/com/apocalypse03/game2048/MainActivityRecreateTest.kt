package com.apocalypse03.game2048

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import com.apocalypse03.game2048.ui.AppViewModel
import com.apocalypse03.game2048.ui.Screen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityRecreateTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun vm(): AppViewModel =
        ViewModelProvider(composeRule.activity, AppViewModel.Factory)[AppViewModel::class.java]

    @Test
    fun boardSurvivesRecreation() {
        composeRule.onNodeWithText("New Game").performClick()
        composeRule.waitForIdle()
        val board = vm().game.board
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        assertEquals(Screen.GAME, vm().screen)
        assertEquals(board, vm().game.board)
    }
}
