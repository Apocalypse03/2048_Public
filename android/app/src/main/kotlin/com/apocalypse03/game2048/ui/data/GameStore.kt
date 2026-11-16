package com.apocalypse03.game2048.ui.data

import android.content.Context

/**
 * Raw durable game session. Values are stored as they were saved; the ViewModel validates them.
 * Best and theme live in [SettingsStore]. [status] is a GameStatus name. [moves] is the hidden move
 * counter and [undoMoves] the undo slot's; [won] and [winPending] are the win flags
 * (DESIGN_SPEC section 13). [gameId] identifies the game in the result lists (section 16).
 * The last five default for sessions saved before they existed (a missing game id is null).
 */
class SavedSession(
    val cells: IntArray,
    val score: Int,
    val undoCells: IntArray?,
    val undoScore: Int,
    val status: String,
    val timerElapsedMs: Long,
    val timerStarted: Boolean,
    val moves: Int = 0,
    val undoMoves: Int = maxOf(moves - 1, 0),
    val won: Boolean = false,
    val winPending: Boolean = false,
    val gameId: Long? = null,
) {
    fun copy(
        cells: IntArray = this.cells,
        score: Int = this.score,
        undoCells: IntArray? = this.undoCells,
        undoScore: Int = this.undoScore,
        status: String = this.status,
        timerElapsedMs: Long = this.timerElapsedMs,
        timerStarted: Boolean = this.timerStarted,
        moves: Int = this.moves,
        undoMoves: Int = this.undoMoves,
        won: Boolean = this.won,
        winPending: Boolean = this.winPending,
        gameId: Long? = this.gameId,
    ) = SavedSession(
        cells, score, undoCells, undoScore, status, timerElapsedMs, timerStarted,
        moves, undoMoves, won, winPending, gameId,
    )
}

/** Durable store for the game session, so it outlives the app being closed. */
interface GameStore {
    /** The saved session, or null when there is none or it cannot be parsed. */
    fun load(): SavedSession?
    fun save(s: SavedSession)
    fun clear()
}

/**
 * [GameStore] in the app's private SharedPreferences (file "game2048", keys prefixed "game_").
 * Boards are comma-separated ints. Each save is a single edit()...apply(). Keys added later
 * (moves, undo moves, win flags, game id) read as their [SavedSession] defaults when missing;
 * a mistyped game id reads as missing.
 */
class SharedPrefsGameStore(context: Context) : GameStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): SavedSession? {
        return try {
            val board = prefs.getString(KEY_BOARD, null)?.toCells() ?: return null
            val status = prefs.getString(KEY_STATUS, null) ?: return null
            val undoText = prefs.getString(KEY_UNDO_BOARD, null)
            val undo = if (undoText == null) null else undoText.toCells() ?: return null
            val moves = prefs.getInt(KEY_MOVES, 0)
            SavedSession(
                cells = board,
                score = prefs.getInt(KEY_SCORE, 0),
                undoCells = undo,
                undoScore = prefs.getInt(KEY_UNDO_SCORE, 0),
                status = status,
                timerElapsedMs = prefs.getLong(KEY_TIMER_ELAPSED, 0L),
                timerStarted = prefs.getBoolean(KEY_TIMER_STARTED, false),
                moves = moves,
                undoMoves = prefs.getInt(KEY_UNDO_MOVES, maxOf(moves - 1, 0)),
                won = prefs.getBoolean(KEY_WON, false),
                winPending = prefs.getBoolean(KEY_WIN_PENDING, false),
                gameId = readGameId(),
            )
        } catch (e: ClassCastException) {
            null
        }
    }

    override fun save(s: SavedSession) {
        prefs.edit().apply {
            putString(KEY_BOARD, s.cells.joinToString(","))
            putInt(KEY_SCORE, s.score)
            if (s.undoCells != null) {
                putString(KEY_UNDO_BOARD, s.undoCells.joinToString(","))
                putInt(KEY_UNDO_SCORE, s.undoScore)
                putInt(KEY_UNDO_MOVES, s.undoMoves)
            } else {
                remove(KEY_UNDO_BOARD)
                remove(KEY_UNDO_SCORE)
                remove(KEY_UNDO_MOVES)
            }
            putString(KEY_STATUS, s.status)
            putLong(KEY_TIMER_ELAPSED, s.timerElapsedMs)
            putBoolean(KEY_TIMER_STARTED, s.timerStarted)
            putInt(KEY_MOVES, s.moves)
            putBoolean(KEY_WON, s.won)
            putBoolean(KEY_WIN_PENDING, s.winPending)
            if (s.gameId != null) putLong(KEY_GAME_ID, s.gameId) else remove(KEY_GAME_ID)
        }.apply()
    }

    private fun readGameId(): Long? {
        if (!prefs.contains(KEY_GAME_ID)) return null
        return try {
            prefs.getLong(KEY_GAME_ID, 0L)
        } catch (e: ClassCastException) {
            null
        }
    }

    override fun clear() {
        prefs.edit().apply { KEYS.forEach(::remove) }.apply()
    }

    /** Comma-separated ints, or null when any part is not an int. */
    private fun String.toCells(): IntArray? {
        val parts = split(",")
        val out = IntArray(parts.size)
        for ((i, p) in parts.withIndex()) out[i] = p.toIntOrNull() ?: return null
        return out
    }

    private companion object {
        const val PREFS_NAME = "game2048"
        const val KEY_BOARD = "game_board"
        const val KEY_SCORE = "game_score"
        const val KEY_UNDO_BOARD = "game_undo_board"
        const val KEY_UNDO_SCORE = "game_undo_score"
        const val KEY_STATUS = "game_status"
        const val KEY_TIMER_ELAPSED = "game_timer_elapsed"
        const val KEY_TIMER_STARTED = "game_timer_started"
        const val KEY_MOVES = "game_moves"
        const val KEY_UNDO_MOVES = "game_undo_moves"
        const val KEY_WON = "game_won"
        const val KEY_WIN_PENDING = "game_win_pending"
        const val KEY_GAME_ID = "game_id"
        val KEYS = listOf(
            KEY_BOARD, KEY_SCORE, KEY_UNDO_BOARD, KEY_UNDO_SCORE,
            KEY_STATUS, KEY_TIMER_ELAPSED, KEY_TIMER_STARTED,
            KEY_MOVES, KEY_UNDO_MOVES, KEY_WON, KEY_WIN_PENDING, KEY_GAME_ID,
        )
    }
}

/** Non-durable [GameStore] for tests and previews. */
class InMemoryGameStore : GameStore {
    private var session: SavedSession? = null

    override fun load(): SavedSession? = session
    override fun save(s: SavedSession) {
        session = s
    }

    override fun clear() {
        session = null
    }
}
