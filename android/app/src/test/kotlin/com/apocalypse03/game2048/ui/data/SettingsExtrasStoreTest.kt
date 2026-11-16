package com.apocalypse03.game2048.ui.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.apocalypse03.game2048.ui.GameMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings screen values, result lists and the session game id in SharedPreferences (sections 15-16). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsExtrasStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences("game2048", Context.MODE_PRIVATE)

    @Test
    fun defaults() {
        val store = SharedPrefsSettingsStore(context)
        assertEquals(100, store.animSpeed)
        assertTrue(store.vibration)
        assertEquals(50, store.vibrationMs)
        assertNull(store.results)
        assertNull(store.resultsPlus)
        assertEquals(1L, store.resultNextId)
        assertTrue(store.results(GameMode.NORMAL).isEmpty())
    }

    @Test
    fun roundTripsUnderTheSpecKeys() {
        SharedPrefsSettingsStore(context).apply {
            animSpeed = 35
            vibration = false
            vibrationMs = 120
            resultNextId = 9
            setResults(GameMode.PLUS, listOf(GameResult(4, 900, 64, 50, 30_000)))
        }
        assertEquals(35, prefs.getInt("anim_speed", -1))
        assertFalse(prefs.getBoolean("vibration", true))
        assertEquals(120, prefs.getInt("vibration_ms", -1))
        assertEquals(9L, prefs.getLong("result_next_id", -1))
        assertEquals("4,900,64,50,30000", prefs.getString("results_plus", null))
        val again = SharedPrefsSettingsStore(context)
        assertEquals(35, again.animSpeed)
        assertFalse(again.vibration)
        assertEquals(120, again.vibrationMs)
        assertEquals(listOf(GameResult(4, 900, 64, 50, 30_000)), again.results(GameMode.PLUS))
        assertTrue(again.results(GameMode.NORMAL).isEmpty())
    }

    @Test
    fun outOfRangeValuesClamp() {
        prefs.edit().putInt("anim_speed", 400).putInt("vibration_ms", 1).putLong("result_next_id", -3).commit()
        val store = SharedPrefsSettingsStore(context)
        assertEquals(150, store.animSpeed)
        assertEquals(5, store.vibrationMs)
        assertEquals(1L, store.resultNextId)
        prefs.edit().putInt("anim_speed", -10).putInt("vibration_ms", 999).commit()
        assertEquals(0, store.animSpeed)
        assertEquals(200, store.vibrationMs)
    }

    @Test
    fun mistypedValuesReadAsDefaults() {
        prefs.edit()
            .putString("anim_speed", "fast")
            .putString("vibration", "yes")
            .putBoolean("vibration_ms", true)
            .putInt("results", 7)
            .putString("result_next_id", "x")
            .commit()
        val store = SharedPrefsSettingsStore(context)
        assertEquals(100, store.animSpeed)
        assertTrue(store.vibration)
        assertEquals(50, store.vibrationMs)
        assertTrue(store.results(GameMode.NORMAL).isEmpty())
        assertEquals(1L, store.resultNextId)
    }

    @Test
    fun corruptResultEntriesAreSkipped() {
        prefs.edit().putString("results", "1,10,8,3,100;oops;2,5,3,1,1;3,40,4,2,9").commit()
        assertEquals(listOf(3L, 1L), SharedPrefsSettingsStore(context).results(GameMode.NORMAL).map { it.id })
    }

    @Test
    fun inMemoryStoreClamps() {
        val store = InMemorySettingsStore(animSpeed = 999, vibrationMs = 0, resultNextId = 0)
        assertEquals(150, store.animSpeed)
        assertEquals(5, store.vibrationMs)
        assertEquals(1L, store.resultNextId)
    }

    @Test
    fun sessionGameIdRoundTripsAndMissingIsNull() {
        val store = SharedPrefsGameStore(context)
        val session = SavedSession(IntArray(16), 0, null, 0, "READY", 0L, false, gameId = 42L)
        store.save(session)
        assertEquals(42L, prefs.getLong("game_id", -1))
        assertEquals(42L, store.load()?.gameId)
        store.save(session.copy(gameId = null))
        assertFalse(prefs.contains("game_id"))
        assertNull(store.load()?.gameId)
    }

    @Test
    fun mistypedGameIdReadsAsMissingButKeepsTheSession() {
        val store = SharedPrefsGameStore(context)
        store.save(SavedSession(IntArray(16), 0, null, 0, "READY", 0L, false, gameId = 5L))
        prefs.edit().putString("game_id", "five").commit()
        val loaded = store.load()
        assertEquals("READY", loaded?.status)
        assertNull(loaded?.gameId)
        store.clear()
        assertFalse(prefs.contains("game_id"))
    }
}
