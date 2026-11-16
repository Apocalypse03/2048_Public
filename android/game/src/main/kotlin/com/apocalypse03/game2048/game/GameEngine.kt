package com.apocalypse03.game2048.game

import kotlin.random.Random

/**
 * READY: new game, no successful move yet. PLAYING: at least one move made.
 * OVER: no legal moves left.
 * Undo from OVER returns to PLAYING. Undo back to the starting position also yields
 * PLAYING (not READY), to keep the rule simple.
 * In Princess Mode the status is frozen at the value it had on entry; it is recomputed on exit.
 */
enum class GameStatus { READY, PLAYING, OVER }

/**
 * Immutable snapshot of the visible game state. [princess] is true while Princess Mode is active.
 * [moves] counts the moves that changed the board since New Game (swaps excluded; undo restores it).
 */
data class GameState(
    val board: Board,
    val score: Int,
    val best: Int,
    val canUndo: Boolean,
    val status: GameStatus,
    val princess: Boolean = false,
    val moves: Int = 0,
) {
    /** Board side length (one of [GameConfig.SIZES]). */
    val size: Int get() = board.size
}

/** Undo entry: board, score and move count before a successful action (move or Princess swap). */
data class UndoSnapshot(val board: Board, val score: Int, val moves: Int = 0)

/**
 * Mutable game session wrapping [Rules]. Mirrors the logic in main() of 2048_vertical.py (:353-431).
 * No win state: reaching 2048 does not end the game. Best-score persistence is the caller's job.
 *
 * Every successful action since New Game is pushed onto a history stack. In normal mode undo is
 * single-level (one undo after a move, then none until the next move). In Princess Mode
 * (DESIGN_SPEC §11) moves are blocked, [swap] exchanges two tile values, and [undo] pops the
 * history without limit.
 *
 * [size] is the board side (one of [GameConfig.SIZES]); every new game uses it.
 *
 * [lastMove] describes the most recent successful move for animation (DESIGN_SPEC §17). It is
 * cleared by every other board change (undo, new game, swap, entering or leaving Princess Mode)
 * and is null on a fresh or restored engine.
 */
class GameEngine(private val rng: Random, initialBest: Int = 0, val size: Int = GameConfig.SIZE) {
    private var board: Board = Rules.newGameBoard(rng, size)
    private var score = 0
    private var moves = 0
    private var best = initialBest
    private val history = ArrayDeque<UndoSnapshot>()
    private var singleUndoAvailable = false
    private var status = GameStatus.READY
    private var princess = false
    private var entryStatus = GameStatus.READY
    private var entryBoard: Board = board

    /**
     * Per-tile motion and spawn of the last move that changed the board; null after any undo,
     * [newGame], [swap], [enterPrincess], [exitPrincess], on a new engine and after [restore].
     * A move that changes nothing leaves it unchanged. Not part of [GameState].
     */
    var lastMove: MoveTrace? = null
        private set

    private val canUndo: Boolean
        get() = if (princess) history.isNotEmpty() else singleUndoAvailable

    /** Current immutable state. */
    val state: GameState
        get() = GameState(board, score, best, canUndo, status, princess, moves)

    /**
     * The normal-mode single undo slot (last history entry while single undo is available, else
     * null), exposed so callers can save and later restore a session.
     */
    val undoSnapshot: UndoSnapshot?
        get() = if (singleUndoAvailable) history.lastOrNull() else null

    /**
     * Applies a move. Ignored when the game is over or in Princess Mode. If the board changed:
     * pushes an undo snapshot, adds gain to score, counts the move, spawns a tile, updates best, and sets OVER if
     * no moves remain.
     * @return true if the board moved.
     */
    fun move(dir: Direction): Boolean {
        if (princess) return false
        if (!Rules.hasLegalMoves(board)) return false
        val result = Rules.move(board, dir)
        if (!result.moved) return false
        history.addLast(UndoSnapshot(board, score, moves))
        singleUndoAvailable = true
        score += result.gain
        moves++
        val before = board
        board = Rules.spawn(result.board, rng)
        lastMove = MoveTrace(dir, traceMove(before, dir), spawnedTile(result.board, board))
        best = maxOf(best, score)
        status = if (Rules.hasLegalMoves(board)) GameStatus.PLAYING else GameStatus.OVER
        return true
    }

    /**
     * Normal mode: restores board, score and move count from the last history entry if single undo is
     * available, then disables undo until the next move; status becomes PLAYING.
     * Princess Mode: pops one history entry (unlimited); status stays frozen.
     * Best is never reverted. Works when the game is over. Returns false if nothing to undo.
     */
    fun undo(): Boolean {
        if (!canUndo) return false
        val snap = history.removeLast()
        lastMove = null
        board = snap.board
        score = snap.score
        moves = snap.moves
        if (!princess) {
            singleUndoAvailable = false
            status = GameStatus.PLAYING
        }
        return true
    }

    /** Enters Princess Mode. No-op if already active. Records the status and board at entry. */
    fun enterPrincess() {
        if (princess) return
        princess = true
        lastMove = null
        entryStatus = status
        entryBoard = board
    }

    /**
     * Leaves Princess Mode. No-op if not active. Status becomes OVER if the board has no legal
     * moves, READY if it was READY on entry and the board is unchanged, else PLAYING. Undo returns
     * to single-level: available iff the history is non-empty.
     */
    fun exitPrincess() {
        if (!princess) return
        princess = false
        lastMove = null
        status = when {
            !Rules.hasLegalMoves(board) -> GameStatus.OVER
            entryStatus == GameStatus.READY && board == entryBoard -> GameStatus.READY
            else -> GameStatus.PLAYING
        }
        singleUndoAvailable = history.isNotEmpty()
    }

    /**
     * Princess Mode only: swaps the values of two distinct, non-empty cells holding different
     * values. Pushes a history entry; score, Best, status and the move count are unchanged and
     * nothing spawns.
     * Returns false (changing nothing) outside Princess Mode, for out-of-range coordinates, the
     * same cell twice, an empty cell, or equal values.
     */
    fun swap(r1: Int, c1: Int, r2: Int, c2: Int): Boolean {
        if (!princess) return false
        val range = 0 until size
        if (r1 !in range || c1 !in range || r2 !in range || c2 !in range) return false
        if (r1 == r2 && c1 == c2) return false
        val v1 = board[r1, c1]
        val v2 = board[r2, c2]
        if (v1 == 0 || v2 == 0 || v1 == v2) return false
        history.addLast(UndoSnapshot(board, score, moves))
        val cells = board.toIntArray()
        cells[r1 * size + c1] = v2
        cells[r2 * size + c2] = v1
        board = Board.of(cells)
        lastMove = null
        return true
    }

    /** The single cell empty in [moved] and filled in [spawned], or null if there is none. */
    private fun spawnedTile(moved: Board, spawned: Board): Spawn? {
        for (r in 0 until size) for (c in 0 until size) {
            if (moved[r, c] == 0 && spawned[r, c] != 0) return Spawn(r, c, spawned[r, c])
        }
        return null
    }

    /**
     * Starts a new game: new board, score 0, move count 0, history cleared, status READY, Princess Mode exited.
     * Best is kept.
     */
    fun newGame() {
        board = Rules.newGameBoard(rng, size)
        score = 0
        moves = 0
        history.clear()
        singleUndoAvailable = false
        princess = false
        status = GameStatus.READY
        lastMove = null
    }

    companion object {
        /**
         * Rebuilds an engine from saved values (e.g. after process death). The history is seeded
         * with [undo] only; Princess Mode is off. [moves] is the saved move count. The engine size is [board]'s size; [undo] must
         * have a board of the same size.
         */
        fun restore(
            rng: Random,
            board: Board,
            score: Int,
            best: Int,
            undo: UndoSnapshot?,
            status: GameStatus,
            moves: Int = 0,
        ): GameEngine {
            require(undo == null || undo.board.size == board.size) {
                "Undo board size ${undo?.board?.size} does not match board size ${board.size}"
            }
            return GameEngine(rng, best, board.size).also {
                it.board = board
                it.score = score
                it.moves = moves
                it.history.addAll(listOfNotNull(undo))
                it.singleUndoAvailable = undo != null
                it.status = status
                it.lastMove = null
            }
        }
    }
}
