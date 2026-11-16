package com.apocalypse03.game2048.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.apocalypse03.game2048.game.Board
import com.apocalypse03.game2048.game.Direction
import com.apocalypse03.game2048.game.GameEngine
import com.apocalypse03.game2048.game.GameState
import com.apocalypse03.game2048.game.GameStatus
import com.apocalypse03.game2048.game.MoveTrace
import com.apocalypse03.game2048.game.Rules
import com.apocalypse03.game2048.game.UndoSnapshot
import com.apocalypse03.game2048.ui.data.GameResult
import com.apocalypse03.game2048.ui.data.GameResults
import com.apocalypse03.game2048.ui.data.GameStore
import com.apocalypse03.game2048.ui.data.InMemoryGameStore
import com.apocalypse03.game2048.ui.data.InMemorySettingsStore
import com.apocalypse03.game2048.ui.data.SavedSession
import com.apocalypse03.game2048.ui.data.SettingsStore
import com.apocalypse03.game2048.ui.data.SharedPrefsGameStore
import com.apocalypse03.game2048.ui.data.SharedPrefsSettingsStore
import com.apocalypse03.game2048.ui.data.WinRecord
import com.apocalypse03.game2048.ui.haptics.AndroidHaptics
import com.apocalypse03.game2048.ui.haptics.Haptics
import com.apocalypse03.game2048.ui.haptics.NoHaptics
import com.apocalypse03.game2048.ui.timer.GameTimer
import com.apocalypse03.game2048.ui.timer.MonotonicClock
import com.apocalypse03.game2048.ui.timer.SystemMonotonicClock
import kotlin.random.Random

enum class Screen { HOME, GAME, SETTINGS, RESULTS }

/**
 * Input of the board move animation (DESIGN_SPEC section 19): the [trace] of one successful move.
 * [seq] grows with every successful move, so two identical traces still restart the animation.
 */
data class MoveAnimation(val seq: Int, val trace: MoveTrace)

/**
 * UI state, the game engine and the game timer.
 *
 * Timer rule (single source of truth: [shouldTimerRun]): it starts on the first move that
 * moves tiles and runs only while the game is shown, PLAYING and the app is in the foreground.
 * Game over freezes it; undo out of OVER resumes it. Undo while playing never resets it.
 * Princess Mode (DESIGN_SPEC section 11) freezes it too.
 *
 * Persistence (P8): Best (per mode), theme id and the selected [mode] go to [settings]. The game
 * session (board, score, undo slot, status, timer elapsed/started, move counts, win flags) is
 * written to [gameStore] after every state change and restored on creation, so it survives the app being closed; the board size
 * (4x4 or 6x6) follows from its cell count. [savedState] holds only the screen, so after process
 * death the user lands on the same screen. Invalid saved data is
 * ignored as a whole and cleared from [gameStore] (fresh game on Home). A restored game opens on
 * Home, with its timer stopped until the next timer sync (e.g. [resumeGame] after [onAppStart]).
 * Princess Mode and its history are not persisted: the current board, score and (frozen entry)
 * status are saved as they are and the session restores with Princess Mode off.
 *
 * Modes: [mode] is the Home selector; New Game starts a game of its size, while Resume continues
 * the saved game in its own size. The Best shown is always that of the current game's mode.
 *
 * Win (DESIGN_SPEC section 13): the first move of a game that leaves a tile >= 2048 on the board
 * sets [won] and [winPending]. While the win screen is pending the timer, input, undo and
 * Princess entry are frozen; [continueEndless] dismisses it for good. Each win is offered to the
 * settings as the mode's best win (fewest moves, then shortest time). Both flags are persisted.
 *
 * Settings and Results (DESIGN_SPEC sections 15-16) open from Home only; [goHome] returns from
 * them. Animation speed and vibration settings are saved on change. A move that merges (score
 * gain > 0) plays one [haptics] pulse while vibration is on (section 17).
 *
 * Results: every game has a game id (assigned at New Game, saved in the session). A game is
 * upserted into its mode's result list when a move ends it and when New Game replaces it after at
 * least one move.
 */
class AppViewModel @JvmOverloads constructor(
    private val random: Random = Random.Default,
    private val clock: MonotonicClock = SystemMonotonicClock,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    private val settings: SettingsStore = InMemorySettingsStore(),
    private val gameStore: GameStore = InMemoryGameStore(),
    private val haptics: Haptics = NoHaptics,
) : ViewModel() {
    private val restored = gameStore.load()?.let { saved ->
        validate(saved).also { if (it == null) gameStore.clear() }
    }

    /** Home selector; New Game uses its size. */
    var mode by mutableStateOf(settings.mode)
        private set

    // A restored score above the stored Best of its mode (e.g. Best lost or reset) raises it, and
    // is written back.
    private val initialMode = restored?.let { GameMode.forSize(it.board.size) } ?: mode
    private val initialBest = maxOf(settings.best(initialMode), restored?.score ?: 0).also {
        if (it > settings.best(initialMode)) settings.setBest(initialMode, it)
    }
    private var engine = restored
        ?.let { GameEngine.restore(random, it.board, it.score, initialBest, it.undo, it.status, it.moves) }
        ?: GameEngine(random, initialBest, initialMode.size)
    private val timer = GameTimer(clock).apply {
        if (restored != null) restore(restored.elapsedMs, restored.timerStarted)
    }
    /** Id of the current game in the result lists; a session saved without one gets a new one. */
    private var gameId: Long = restored?.gameId ?: newGameId()
    private var inForeground = true
    private var lastTapMs = 0L
    private var tapCount = 0

    var screen by mutableStateOf(readScreen(restored != null))
        private set
    var themeId by mutableIntStateOf(settings.themeId)
        private set
    /** Current game snapshot; refreshed after every engine call. */
    var game by mutableStateOf(engine.state)
        private set

    /**
     * Last successful move to animate (section 17), or null when the board last changed any other
     * way (undo, new game, restore, Princess Mode, resume, Home): those redraw instantly.
     * Drawing only; [game] is always the final state.
     */
    var moveAnimation by mutableStateOf<MoveAnimation?>(null)
        private set
    private var moveSeq = 0

    /** True once this game has reached 2048; never reset before New Game. */
    var won by mutableStateOf(restored?.won ?: false)
        private set

    /** True while the win screen is shown (until [continueEndless] or New Game). */
    var winPending by mutableStateOf(restored?.winPending ?: false)
        private set

    /**
     * A game that can be resumed: PLAYING (READY has no move yet, OVER is finished), or a pending
     * win screen (even if the winning move ended the game).
     */
    val hasGameInProgress: Boolean
        get() = game.status == GameStatus.PLAYING || winPending

    /** True while the "start a new game?" confirmation is pending. */
    var confirmNewGame by mutableStateOf(false)
        private set

    /** True while the Info panel is shown over Home (DESIGN_SPEC section 19). Not persisted. */
    var infoOpen by mutableStateOf(false)
        private set

    /** Whether the game timer is counting; drives the UI tick loop. */
    var timerRunning by mutableStateOf(false)
        private set

    /** Princess Mode tile selection: cell index r * size + c (current board size), or null. */
    var selected by mutableStateOf<Int?>(null)
        private set

    // Plain backing states: a `private set` would clash on the JVM with setAnimSpeed() and friends.
    private var animSpeedState by mutableIntStateOf(settings.animSpeed)
    private var fontScaleState by mutableIntStateOf(settings.fontScale)
    private var vibrationState by mutableStateOf(settings.vibration)
    private var vibrationMsState by mutableIntStateOf(settings.vibrationMs)

    /** Animation speed in percent, 0..150 (DESIGN_SPEC section 15). */
    val animSpeed: Int get() = animSpeedState

    /** User Font Size in percent, 70..130 (DESIGN_SPEC section 15). */
    val fontScale: Int get() = fontScaleState

    /** Whether merges vibrate. */
    val vibration: Boolean get() = vibrationState

    /** Vibration pulse length in ms, 5..200. */
    val vibrationMs: Int get() = vibrationMsState

    /** Mode shown on the Results screen; set to [mode] by [openResults], never written back to it. */
    var resultsMode by mutableStateOf(read<String>(KEY_RESULTS_MODE)?.let(::modeOrNull) ?: mode)
        private set

    private val resultLists = mutableStateMapOf<GameMode, List<GameResult>>().apply {
        GameMode.entries.forEach { put(it, settings.results(it)) }
    }

    init {
        // A restored session saved without a game id keeps the one just assigned.
        if (restored != null && restored.gameId == null) persist()
    }

    fun elapsedMs(): Long = timer.elapsedMs()

    /**
     * Applies a move; ignored when the game is over, in Princess Mode or while the win screen is
     * pending. A move that moves tiles starts the timer. The first move that leaves a tile
     * >= 2048 wins (the timer then freezes) and is offered as the mode's best win.
     */
    fun move(dir: Direction) {
        if (game.status == GameStatus.OVER || game.princess || winPending) return
        val scoreBefore = engine.state.score
        val moved = engine.move(dir)
        game = engine.state
        if (moved) moveAnimation = engine.lastMove?.let { MoveAnimation(++moveSeq, it) }
        if (moved && game.score > scoreBefore && vibration) haptics.pulse(vibrationMs.toLong())
        val wins = moved && !won && game.board.toIntArray().max() >= WIN_TILE
        if (wins) {
            won = true
            winPending = true
        }
        syncTimer(starting = moved)
        if (wins) settings.recordWin(GameMode.forSize(game.size), WinRecord(game.moves, timer.elapsedMs()))
        if (moved && game.status == GameStatus.OVER) recordResult()
        if (moved) persist() // a move that moves nothing changes nothing to save
    }

    /** Win screen "Endless mode": hides it for the rest of this game; play and the timer resume. */
    fun continueEndless() {
        if (!winPending) return
        winPending = false
        syncTimer()
        persist()
    }

    /** Undoes one step (unlimited in Princess Mode); clears the selection. Ignored while the win screen is pending. */
    fun undo() {
        if (winPending) return
        engine.undo()
        game = engine.state
        moveAnimation = null
        selected = null
        syncTimer()
        persist()
    }

    /**
     * Timer-row tap: 5 taps, each within [TAP_BURST_MS] of the previous one, enter Princess Mode.
     * Ignored off the Game screen, when already in Princess Mode or while the win screen is pending.
     */
    fun onTimerTap() {
        if (screen != Screen.GAME || game.princess || winPending) return
        val now = clock.nowMs()
        tapCount = if (tapCount > 0 && now - lastTapMs <= TAP_BURST_MS) tapCount + 1 else 1
        lastTapMs = now
        if (tapCount >= PRINCESS_TAPS) {
            tapCount = 0
            enterPrincess()
        }
    }

    /** Ignored while the win screen is pending. */
    fun enterPrincess() {
        if (winPending) return
        engine.enterPrincess()
        game = engine.state
        moveAnimation = null
        selected = null
        syncTimer()
        persist()
    }

    fun exitPrincess() {
        engine.exitPrincess()
        game = engine.state
        moveAnimation = null
        selected = null
        syncTimer()
        persist()
    }

    /**
     * Princess Mode tile tap: selects a numbered tile, deselects it on a second tap, or swaps it
     * with the selected one (equal values only deselect). Empty cells are ignored.
     */
    fun onTileTap(r: Int, c: Int) {
        if (!game.princess || game.board[r, c] == 0) return
        val n = game.size
        val index = r * n + c
        val sel = selected
        when (sel) {
            null -> selected = index
            index -> selected = null
            else -> {
                engine.swap(sel / n, sel % n, r, c)
                game = engine.state
                moveAnimation = null
                selected = null
                syncTimer()
                persist()
            }
        }
    }

    /**
     * Always starts a fresh game of the selected [mode] (that mode's Best is kept) and shows the
     * Game screen; exits Princess Mode. Writes the selected mode to [settings].
     */
    fun startNewGame() {
        settings.mode = mode
        newGameOf(mode)
    }

    /**
     * Fresh game of [gameMode] (its Best kept) on the Game screen; the selector is untouched.
     * The replaced game is recorded first when it has at least one move; the new one gets a new id.
     */
    private fun newGameOf(gameMode: GameMode) {
        if (engine.state.moves > 0) recordResult()
        gameId = newGameId()
        if (engine.size == gameMode.size) {
            engine.newGame()
        } else {
            engine = GameEngine(random, settings.best(gameMode), gameMode.size)
        }
        game = engine.state
        moveAnimation = null
        won = false
        winPending = false
        selected = null
        screen = Screen.GAME
        timer.reset()
        syncTimer()
        persist()
    }

    /** Shows the game in progress again; ignored when there is none. */
    fun resumeGame() {
        if (!hasGameInProgress) return
        moveAnimation = null
        screen = Screen.GAME
        syncTimer()
        persist()
    }

    /**
     * New Game button (Home, or the win screen): asks for confirmation when a game is in progress,
     * else starts directly.
     */
    fun requestNewGame() {
        if (hasGameInProgress) confirmNewGame = true else startNewGame()
    }

    /** From Home: a game of the selected [mode]. From the win screen: one of the current game's mode. */
    fun confirmNewGameYes() {
        confirmNewGame = false
        if (screen == Screen.GAME) newGameOf(GameMode.forSize(engine.size)) else startNewGame()
    }

    fun confirmNewGameNo() {
        confirmNewGame = false
    }

    /** Home info icon: shows the Info panel. Ignored off Home. */
    fun openInfo() {
        if (screen != Screen.HOME) return
        infoOpen = true
    }

    fun closeInfo() {
        infoOpen = false
    }

    /** Returns Home and exits Princess Mode; the game stays in memory and the timer pauses. */
    fun goHome() {
        engine.exitPrincess()
        game = engine.state
        moveAnimation = null
        selected = null
        screen = Screen.HOME
        syncTimer()
        persist()
    }

    /** Home gear icon: shows Settings. Ignored off Home. */
    fun openSettings() {
        if (screen != Screen.HOME) return
        screen = Screen.SETTINGS
        saveScreen()
    }

    /** Home list icon: shows Results, opened on the Home selector's [mode]. Ignored off Home. */
    fun openResults() {
        if (screen != Screen.HOME) return
        resultsMode = mode
        screen = Screen.RESULTS
        saveScreen()
    }

    /** Results selector arrows: moves [resultsMode] by [delta] within the mode list; [mode] is untouched. */
    fun stepResultsMode(delta: Int) {
        resultsMode = GameMode.entries[(resultsMode.ordinal + delta).coerceIn(0, GameMode.entries.lastIndex)]
        saveScreen()
    }

    /** Result list of [mode] (top 20, ordered); Compose state, so readers update on a new result. */
    fun results(mode: GameMode): List<GameResult> = resultLists[mode].orEmpty()

    /** Sets and saves the animation speed, coerced to 0..150. */
    fun setAnimSpeed(value: Int) {
        animSpeedState = value.coerceIn(SettingsStore.ANIM_SPEED_MIN, SettingsStore.ANIM_SPEED_MAX)
        settings.animSpeed = animSpeedState
    }

    /** Sets and saves the user Font Size, coerced to 70..130 %. */
    fun setFontScale(percent: Int) {
        fontScaleState = percent.coerceIn(SettingsStore.FONT_SCALE_MIN, SettingsStore.FONT_SCALE_MAX)
        settings.fontScale = fontScaleState
    }

    /** Sets and saves the vibration switch; turning it on (from off) plays one preview pulse. */
    fun setVibration(on: Boolean) {
        val turnedOn = on && !vibration
        vibrationState = on
        settings.vibration = on
        if (turnedOn) haptics.pulse(vibrationMs.toLong())
    }

    /** Sets and saves the vibration length, coerced to 5..200 ms. */
    fun setVibrationMs(value: Int) {
        vibrationMsState = value.coerceIn(SettingsStore.VIBRATION_MS_MIN, SettingsStore.VIBRATION_MS_MAX)
        settings.vibrationMs = vibrationMsState
    }

    /** One pulse of [vibrationMs] (Settings intensity preview); nothing while vibration is off. */
    fun previewVibration() {
        if (vibration) haptics.pulse(vibrationMs.toLong())
    }

    /** Lifecycle ON_START: the timer may resume. */
    fun onAppStart() {
        inForeground = true
        syncTimer()
    }

    /** Lifecycle ON_STOP: the timer pauses and the session (with its elapsed time) is saved. */
    fun onAppStop() {
        inForeground = false
        syncTimer()
        persist()
    }

    fun selectTheme(id: Int) {
        themeId = id
        settings.themeId = id
    }

    /** Home selector arrows: moves [mode] by [delta] within the mode list and saves it. */
    fun stepMode(delta: Int) {
        mode = GameMode.entries[(mode.ordinal + delta).coerceIn(0, GameMode.entries.lastIndex)]
        settings.mode = mode
    }

    /** Test/preview hook: replaces the engine with a restored session; the timer is not reset. */
    internal fun restoreGame(
        board: Board,
        score: Int = 0,
        best: Int = 0,
        undo: UndoSnapshot? = null,
        status: GameStatus = GameStatus.READY,
        moves: Int = 0,
        won: Boolean = false,
        winPending: Boolean = false,
    ) {
        engine = GameEngine.restore(random, board, score, best, undo, status, moves)
        game = engine.state
        moveAnimation = null
        this.won = won || winPending
        this.winPending = winPending
        selected = null
        syncTimer()
        persist()
    }

    /**
     * [starting] is true for a move that moved tiles; it lets an idle timer start.
     * The engine reports PLAYING after any undo, so undo never stops a running timer.
     * A pending win screen freezes it.
     */
    private fun shouldTimerRun(starting: Boolean): Boolean =
        (timer.started || starting) &&
            screen == Screen.GAME &&
            game.status == GameStatus.PLAYING &&
            !game.princess &&
            !winPending &&
            inForeground

    private fun syncTimer(starting: Boolean = false) {
        timer.setRunning(shouldTimerRun(starting))
        timerRunning = timer.running
    }

    /** Saves the session to [gameStore] and the screen to [savedState]; raises the stored Best of the game's mode. */
    private fun persist() {
        val state = engine.state
        val undo = engine.undoSnapshot
        gameStore.save(
            SavedSession(
                cells = state.board.toIntArray(),
                score = state.score,
                undoCells = undo?.board?.toIntArray(),
                undoScore = undo?.score ?: 0,
                status = state.status.name,
                timerElapsedMs = timer.elapsedMs(),
                timerStarted = timer.started,
                moves = state.moves,
                undoMoves = undo?.moves ?: 0,
                won = won,
                winPending = winPending,
                gameId = gameId,
            ),
        )
        saveScreen()
        val gameMode = GameMode.forSize(state.size)
        if (state.best > settings.best(gameMode)) settings.setBest(gameMode, state.best)
    }

    /** Writes the screen and the Results mode to [savedState]. */
    private fun saveScreen() {
        savedState[KEY_SCREEN] = screen.name
        savedState[KEY_RESULTS_MODE] = resultsMode.name
    }

    /**
     * Upserts the current game into its mode's result list: score, highest tile, moves and timer
     * elapsed. Called with the timer already frozen when the game is over.
     */
    private fun recordResult() {
        val state = engine.state
        val gameMode = GameMode.forSize(state.size)
        val result = GameResult(gameId, state.score, state.board.toIntArray().max(), state.moves, timer.elapsedMs())
        val list = GameResults.upsert(resultLists[gameMode].orEmpty(), result)
        resultLists[gameMode] = list
        settings.setResults(gameMode, list)
    }

    /**
     * Hands out the next game id and advances the stored counter. It never reuses an id still in a
     * result list (e.g. after the counter was lost), so an upsert cannot overwrite another game.
     */
    private fun newGameId(): Long {
        val inUse = GameMode.entries.maxOf { m -> settings.results(m).maxOfOrNull { it.id } ?: 0L }
        val id = maxOf(settings.resultNextId, inUse + 1)
        settings.resultNextId = id + 1
        return id
    }

    private fun modeOrNull(name: String): GameMode? = GameMode.entries.firstOrNull { it.name == name }

    /** Type-safe read: a value of the wrong type reads as null. */
    private inline fun <reified T> read(key: String): T? = savedState.get<Any?>(key) as? T

    /** Saved screen; GAME needs a restored session, else Home. Settings and Results restore as they are. */
    private fun readScreen(hasSession: Boolean): Screen {
        val saved = read<String>(KEY_SCREEN)?.let { name -> Screen.entries.firstOrNull { it.name == name } }
        return when (saved) {
            Screen.GAME -> if (hasSession) Screen.GAME else Screen.HOME
            Screen.SETTINGS, Screen.RESULTS -> saved
            else -> Screen.HOME
        }
    }

    /** The session as game values, or null when any core value is invalid. */
    private fun validate(s: SavedSession): SavedGame? {
        val board = s.cells.toBoardOrNull() ?: return null
        if (s.score < 0 || s.moves < 0) return null
        if (s.winPending && !s.won) return null
        val savedStatus = GameStatus.entries.firstOrNull { it.name == s.status } ?: return null
        // Keep status consistent with the board (e.g. a Princess rescue saved with its frozen OVER).
        val status = when {
            !Rules.hasLegalMoves(board) -> GameStatus.OVER
            savedStatus == GameStatus.OVER -> GameStatus.PLAYING
            else -> savedStatus
        }
        val undo = if (s.undoCells == null) {
            null
        } else {
            val undoBoard = s.undoCells.toBoardOrNull() ?: return null
            if (undoBoard.size != board.size) return null
            if (s.undoScore < 0 || s.undoMoves < 0) return null
            UndoSnapshot(undoBoard, s.undoScore, s.undoMoves)
        }
        return SavedGame(
            board, s.score, undo, status, s.timerElapsedMs.coerceAtLeast(0L), s.timerStarted,
            s.moves, s.won, s.winPending, s.gameId?.takeIf { it >= 1 },
        )
    }

    private class SavedGame(
        val board: Board,
        val score: Int,
        val undo: UndoSnapshot?,
        val status: GameStatus,
        val elapsedMs: Long,
        val timerStarted: Boolean,
        val moves: Int,
        val won: Boolean,
        val winPending: Boolean,
        /** Null when the session had none (or an invalid one); a new id is then assigned. */
        val gameId: Long?,
    )

    companion object {
        /** Smallest tile that wins (DESIGN_SPEC section 13). */
        const val WIN_TILE = 2048

        /** Max gap between two timer taps of one burst, inclusive. */
        const val TAP_BURST_MS = 400L
        const val PRINCESS_TAPS = 5

        private const val KEY_SCREEN = "screen"
        private const val KEY_RESULTS_MODE = "results_mode"

        /**
         * Production factory: SavedStateHandle from the owner, settings and game in
         * SharedPreferences, the system vibrator.
         */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                AppViewModel(
                    savedState = createSavedStateHandle(),
                    settings = SharedPrefsSettingsStore(app),
                    gameStore = SharedPrefsGameStore(app),
                    haptics = AndroidHaptics(app),
                )
            }
        }

        /** Board from saved cells: n*n values (n a mode size), each 0 or a power of two >= 2; else null. */
        private fun IntArray.toBoardOrNull(): Board? {
            if (GameMode.entries.none { it.size * it.size == size }) return null
            if (!all { it == 0 || (it >= 2 && it and (it - 1) == 0) }) return null
            return Board.of(this)
        }
    }
}
