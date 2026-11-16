package com.apocalypse03.game2048.ui

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.apocalypse03.game2048.game.Board
import com.apocalypse03.game2048.game.Direction
import com.apocalypse03.game2048.game.GameStatus
import com.apocalypse03.game2048.ui.game.ShowcaseBoard
import com.apocalypse03.game2048.ui.game.ShowcaseBoard6
import com.apocalypse03.game2048.ui.timer.FakeClock
import kotlin.random.Random
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class ScreenshotBase(private val size: String, private val fontScale: Float? = null) {
    @get:Rule
    val rule = createComposeRule()

    protected fun shoot(
        game: Boolean,
        themeId: Int,
        themeName: String,
        tag: String,
        openBar: Boolean = false,
    ) {
        val vm = AppViewModel().apply {
            selectTheme(themeId)
            if (game) startNewGame()
        }
        val board = if (game) ShowcaseBoard else null
        if (fontScale != null) {
            rule.setAppWithFontScale(vm, mutableFloatStateOf(fontScale), board)
        } else {
            rule.setApp(vm, board)
        }
        if (openBar) {
            rule.onNodeWithContentDescription("Palette").performClick()
            rule.waitForIdle()
        }
        rule.saveScreenshot("${tag}_${size}_$themeName")
    }

    /** 2048+ mode, Default theme: Home with the selector on 2048+, or a 6x6 showcase game. */
    protected fun shootPlus(game: Boolean) {
        val vm = AppViewModel().apply {
            stepMode(1)
            if (game) startNewGame()
        }
        val board = if (game) ShowcaseBoard6 else null
        if (fontScale != null) {
            rule.setAppWithFontScale(vm, mutableFloatStateOf(fontScale), board)
        } else {
            rule.setApp(vm, board)
        }
        rule.saveScreenshot("${if (game) "game6" else "homeplus"}_${size}_default")
    }

    /**
     * Win screen (DESIGN_SPEC section 13): DOWN (moves only columns without the 1024 pair), then
     * LEFT merges 1024 + 1024 in row 0. [plus] uses the 6x6 board; [dialog] opens the
     * new-game confirmation on top.
     */
    protected fun shootWin(themeId: Int, themeName: String, plus: Boolean = false, dialog: Boolean = false) {
        val clock = FakeClock()
        val vm = AppViewModel(Random(0), clock).apply {
            selectTheme(themeId)
            if (plus) stepMode(1)
            startNewGame()
            restoreGame(if (plus) WinBoard6 else WinBoard4, moves = if (plus) 2_480 else 935)
            move(Direction.DOWN)
            clock.advance(if (plus) 3_725_000L else 754_000L)
            move(Direction.LEFT)
            check(winPending) { "showcase did not win" }
            if (dialog) requestNewGame()
        }
        if (fontScale != null) {
            rule.setAppWithFontScale(vm, mutableFloatStateOf(fontScale))
        } else {
            rule.setApp(vm)
        }
        val tag = when {
            dialog -> "winconfirm"
            plus -> "win6"
            else -> "win"
        }
        rule.saveScreenshot("${tag}_${size}_$themeName")
    }

    @Test
    fun home() = shoot(false, 0, "default", "home")

    @Test
    fun game() = shoot(true, 0, "default", "game")

    @Test
    fun gameThemeBar() = shoot(true, 0, "default", "gamebar", openBar = true)
}

@Config(sdk = [34], qualifiers = "w360dp-h640dp")
class Screenshot360 : ScreenshotBase("360x640")

@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class Screenshot411 : ScreenshotBase("411x891") {
    @Test
    fun homeLightBlue() = shoot(false, 4, "lightblue", "home")

    @Test
    fun gameLightBlue() = shoot(true, 4, "lightblue", "game")

    @Test
    fun homeOrange() = shoot(false, 2, "orange", "home")

    @Test
    fun gameOrange() = shoot(true, 2, "orange", "game")

    @Test
    fun gameLavenderBar() = shoot(true, 7, "lavender", "gamebar", openBar = true)

    @Test
    fun gameRose() = shoot(true, 13, "rose", "game")

    @Test
    fun gameCyan() = shoot(true, 15, "cyan", "game")

    /** Home with a game in progress: Resume pill visible. */
    @Test
    fun homeResume() = shootHomeInProgress(0, "default", false)

    @Test
    fun homeConfirmDialog() = shootHomeInProgress(0, "default", true)

    @Test
    fun homeConfirmDialogLightBlue() = shootHomeInProgress(4, "lightblue", true)

    private fun shootHomeInProgress(themeId: Int, themeName: String, dialog: Boolean) {
        val vm = AppViewModel().apply {
            selectTheme(themeId)
            startNewGame()
            restoreGame(ShowcaseBoard, status = GameStatus.PLAYING)
            goHome()
            if (dialog) requestNewGame()
        }
        rule.setApp(vm)
        rule.saveScreenshot("${if (dialog) "homeconfirm" else "homeresume"}_411x891_$themeName")
    }

    /** Princess Mode (DESIGN_SPEC section 11): crown shown, tile (1,1) selected. */
    @Test
    fun princessSelected() {
        val vm = AppViewModel().apply { startNewGame() }
        rule.setApp(vm, ShowcaseBoard)
        rule.runOnIdle {
            vm.enterPrincess()
            vm.onTileTap(1, 1)
        }
        rule.saveScreenshot("princess_411x891_default")
    }

    @Test
    fun homePlus() = shootPlus(false)

    @Test
    fun game6() = shootPlus(true)

    @Test
    fun win() = shootWin(0, "default")

    @Test
    fun win6() = shootWin(0, "default", plus = true)

    @Test
    fun winLightBlue() = shootWin(4, "lightblue")

    @Test
    fun winConfirmDialog() = shootWin(0, "default", dialog = true)
}

@Config(sdk = [34], qualifiers = "w480dp-h1040dp")
class Screenshot480 : ScreenshotBase("480x1040")

/** UI labels at the 1.3 font-scale clamp (DESIGN_SPEC section 1). */
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class Screenshot411FontScale130 : ScreenshotBase("411x891_fs130", fontScale = 1.3f) {
    @Test
    fun homeConfirmDialog() {
        val vm = AppViewModel().apply {
            startNewGame()
            restoreGame(ShowcaseBoard, status = GameStatus.PLAYING)
            goHome()
            requestNewGame()
        }
        rule.setAppWithFontScale(vm, mutableFloatStateOf(1.3f))
        rule.saveScreenshot("homeconfirm_411x891_fs130_default")
    }

    @Test
    fun game6() = shootPlus(true)

    @Test
    fun win() = shootWin(0, "default")
}

@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land")
class Screenshot1280x800Landscape : ScreenshotBase("1280x800")

/** 4x4 position one move from a win (see [ScreenshotBase.shootWin]). */
private val WinBoard4 = Board.of(
    listOf(
        listOf(1024, 1024, 4, 2),
        listOf(512, 256, 16, 0),
        listOf(128, 64, 8, 0),
        listOf(32, 4, 2, 0),
    ),
)

/** 6x6 position one move from a win; columns 0 and 1 are full without merges, so DOWN keeps the pair. */
private val WinBoard6 = Board.of(
    listOf(
        listOf(1024, 1024, 8, 16, 4, 2),
        listOf(512, 256, 32, 64, 8, 0),
        listOf(128, 64, 4, 2, 16, 0),
        listOf(32, 16, 0, 0, 0, 0),
        listOf(8, 4, 0, 0, 0, 0),
        listOf(2, 8, 0, 0, 0, 0),
    ),
)
