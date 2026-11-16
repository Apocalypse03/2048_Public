package com.apocalypse03.game2048.ui.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SharedPrefsGameStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences("game2048", Context.MODE_PRIVATE)

    private val cells = intArrayOf(2, 4, 8, 16, 0, 0, 0, 0, 0, 0, 32, 0, 0, 0, 0, 2048)
    private val undoCells = intArrayOf(2, 4, 8, 8, 0, 0, 0, 0, 0, 0, 32, 0, 0, 0, 0, 1024)

    private fun session(withUndo: Boolean = true) = SavedSession(
        cells = cells,
        score = 120,
        undoCells = if (withUndo) undoCells else null,
        undoScore = if (withUndo) 100 else 0,
        status = "PLAYING",
        timerElapsedMs = 12_345L,
        timerStarted = true,
    )

    @Test
    fun roundTrips36Cells() {
        val big = IntArray(36) { if (it % 5 == 0) 2 shl (it % 13) else 0 }
        val undo = IntArray(36) { if (it == 35) 4 else 0 }
        SharedPrefsGameStore(context).save(session().copy(cells = big, undoCells = undo))
        val s = SharedPrefsGameStore(context).load()
        assertNotNull(s)
        assertArrayEquals(big, s!!.cells)
        assertArrayEquals(undo, s.undoCells)
    }

    @Test
    fun emptyLoadsNull() {
        assertNull(SharedPrefsGameStore(context).load())
    }

    @Test
    fun roundTripsWithUndo() {
        SharedPrefsGameStore(context).save(session())
        val s = SharedPrefsGameStore(context).load()
        assertNotNull(s)
        assertArrayEquals(cells, s!!.cells)
        assertEquals(120, s.score)
        assertArrayEquals(undoCells, s.undoCells)
        assertEquals(100, s.undoScore)
        assertEquals("PLAYING", s.status)
        assertEquals(12_345L, s.timerElapsedMs)
        assertTrue(s.timerStarted)
    }

    @Test
    fun roundTripsWithoutUndo() {
        val store = SharedPrefsGameStore(context)
        store.save(session())
        store.save(session(withUndo = false))
        val s = store.load()!!
        assertNull(s.undoCells)
        assertEquals(0, s.undoScore)
        assertFalse(prefs.contains("game_undo_board"))
    }

    @Test
    fun clearRemovesSession() {
        val store = SharedPrefsGameStore(context)
        store.save(session())
        store.clear()
        assertNull(store.load())
    }

    @Test
    fun clearKeepsSettingsKeys() {
        prefs.edit().putInt("best", 99).commit()
        val store = SharedPrefsGameStore(context)
        store.save(session())
        store.clear()
        assertEquals(99, prefs.getInt("best", 0))
    }

    @Test
    fun corruptBoardLoadsNull() {
        SharedPrefsGameStore(context).save(session())
        prefs.edit().putString("game_board", "1,2,x").commit()
        assertNull(SharedPrefsGameStore(context).load())
    }

    @Test
    fun corruptUndoBoardLoadsNull() {
        SharedPrefsGameStore(context).save(session())
        prefs.edit().putString("game_undo_board", "2,,4").commit()
        assertNull(SharedPrefsGameStore(context).load())
    }

    @Test
    fun missingStatusLoadsNull() {
        SharedPrefsGameStore(context).save(session())
        prefs.edit().remove("game_status").commit()
        assertNull(SharedPrefsGameStore(context).load())
    }

    @Test
    fun roundTripsMovesAndWinFlags() {
        val store = SharedPrefsGameStore(context)
        store.save(session().copy(moves = 321, undoMoves = 320, won = true, winPending = true))
        val s = SharedPrefsGameStore(context).load()!!
        assertEquals(321, s.moves)
        assertEquals(320, s.undoMoves)
        assertTrue(s.won)
        assertTrue(s.winPending)
        assertEquals(321, prefs.getInt("game_moves", -1))
        assertTrue(prefs.getBoolean("game_win_pending", false))
        store.save(session(withUndo = false).copy(moves = 5))
        assertFalse(prefs.contains("game_undo_moves"))
        store.clear()
        listOf("game_moves", "game_won", "game_win_pending").forEach { assertFalse(it, prefs.contains(it)) }
    }

    /** A session saved before the move counter and win flags existed. */
    @Test
    fun missingNewKeysReadAsDefaults() {
        SharedPrefsGameStore(context).save(session().copy(moves = 9, won = true))
        prefs.edit()
            .remove("game_moves").remove("game_undo_moves").remove("game_won").remove("game_win_pending")
            .commit()
        val s = SharedPrefsGameStore(context).load()!!
        assertEquals(0, s.moves)
        assertEquals(0, s.undoMoves)
        assertFalse(s.won)
        assertFalse(s.winPending)
        prefs.edit().putInt("game_moves", 12).commit()
        assertEquals(11, SharedPrefsGameStore(context).load()!!.undoMoves)
    }
}
