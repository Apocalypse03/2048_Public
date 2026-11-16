package com.apocalypse03.game2048.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.apocalypse03.game2048.ui.game.BOARD_TAG
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
abstract class BoardGeometryBase {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun boardIsSquareAndInsideWindow() {
        rule.setApp(AppViewModel().apply { startNewGame() })
        val b = rule.onNodeWithTag(BOARD_TAG).fetchSemanticsNode().boundsInRoot
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("not square: ${b.width} x ${b.height}", abs(b.width - b.height) <= 1f)
        assertTrue(
            "board $b outside root $root",
            b.left >= root.left && b.top >= root.top && b.right <= root.right && b.bottom <= root.bottom,
        )
        assertTrue(b.width > 0f)
    }
}

@Config(sdk = [34], qualifiers = "w360dp-h640dp")
class BoardGeometry360 : BoardGeometryBase()

@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class BoardGeometry411 : BoardGeometryBase()

@Config(sdk = [34], qualifiers = "w480dp-h1040dp")
class BoardGeometry480 : BoardGeometryBase()

@Config(sdk = [34], qualifiers = "w800dp-h1280dp")
class BoardGeometryTablet : BoardGeometryBase()

@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land")
class BoardGeometryLandscapeTablet : BoardGeometryBase()

@Config(sdk = [34], qualifiers = "w411dp-h600dp")
class BoardGeometrySquatPhone : BoardGeometryBase()
