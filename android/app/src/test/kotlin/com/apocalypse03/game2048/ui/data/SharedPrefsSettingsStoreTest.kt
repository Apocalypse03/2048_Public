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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SharedPrefsSettingsStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences("game2048", Context.MODE_PRIVATE)

    @Test
    fun defaultsToZero() {
        val store = SharedPrefsSettingsStore(context)
        assertEquals(0, store.best)
        assertEquals(0, store.bestPlus)
        assertEquals(0, store.themeId)
        assertEquals(GameMode.NORMAL, store.mode)
    }

    @Test
    fun roundTrips() {
        val store = SharedPrefsSettingsStore(context)
        store.best = 2048
        store.themeId = 4
        assertEquals(2048, store.best)
        assertEquals(4, store.themeId)
    }

    @Test
    fun persistsAcrossInstances() {
        SharedPrefsSettingsStore(context).apply {
            best = 512
            themeId = 2
        }
        val again = SharedPrefsSettingsStore(context)
        assertEquals(512, again.best)
        assertEquals(2, again.themeId)
    }

    @Test
    fun negativeBestIsSanitised() {
        prefs.edit().putInt("best", -5).commit()
        assertEquals(0, SharedPrefsSettingsStore(context).best)
    }

    @Test
    fun paletteThemeIdsPersist() {
        for (id in 7..16) {
            SharedPrefsSettingsStore(context).themeId = id
            assertEquals(id, prefs.getInt("theme_id", -1))
            assertEquals(id, SharedPrefsSettingsStore(context).themeId)
        }
    }

    @Test
    fun oldDarkThemeIdsMigrateAndAreWrittenBack() {
        for (id in 17..26) {
            prefs.edit().putInt("theme_id", id).commit()
            assertEquals(id - 10, SharedPrefsSettingsStore(context).themeId)
            assertEquals(id - 10, prefs.getInt("theme_id", -1))
            assertEquals(id - 10, SharedPrefsSettingsStore(context).themeId)
        }
    }

    @Test
    fun unknownThemeIdIsNotWrittenBack() {
        prefs.edit().putInt("theme_id", 99).commit()
        assertEquals(0, SharedPrefsSettingsStore(context).themeId)
        assertEquals(99, prefs.getInt("theme_id", -1))
    }

    @Test
    fun unknownThemeIdIsSanitised() {
        prefs.edit().putInt("theme_id", 27).commit()
        assertEquals(0, SharedPrefsSettingsStore(context).themeId)
        prefs.edit().putInt("theme_id", 99).commit()
        assertEquals(0, SharedPrefsSettingsStore(context).themeId)
        prefs.edit().putInt("theme_id", -1).commit()
        assertEquals(0, SharedPrefsSettingsStore(context).themeId)
    }

    @Test
    fun modeRoundTrips() {
        SharedPrefsSettingsStore(context).mode = GameMode.PLUS
        assertEquals("PLUS", prefs.getString("mode", null))
        assertEquals(GameMode.PLUS, SharedPrefsSettingsStore(context).mode)
        SharedPrefsSettingsStore(context).mode = GameMode.NORMAL
        assertEquals(GameMode.NORMAL, SharedPrefsSettingsStore(context).mode)
    }

    @Test
    fun unknownModeIsSanitised() {
        prefs.edit().putString("mode", "EXPERT").commit()
        assertEquals(GameMode.NORMAL, SharedPrefsSettingsStore(context).mode)
        prefs.edit().putInt("mode", 1).commit()
        assertEquals(GameMode.NORMAL, SharedPrefsSettingsStore(context).mode)
    }

    @Test
    fun bestPlusRoundTripsSeparately() {
        val store = SharedPrefsSettingsStore(context)
        store.best = 300
        store.bestPlus = 9000
        val again = SharedPrefsSettingsStore(context)
        assertEquals(300, again.best)
        assertEquals(9000, again.bestPlus)
        assertEquals(9000, prefs.getInt("best_plus", -1))
        assertEquals(300, again.best(GameMode.NORMAL))
        assertEquals(9000, again.best(GameMode.PLUS))
        again.setBest(GameMode.PLUS, 9100)
        assertEquals(9100, SharedPrefsSettingsStore(context).bestPlus)
        assertEquals(300, SharedPrefsSettingsStore(context).best)
    }

    @Test
    fun negativeBestPlusIsSanitised() {
        prefs.edit().putInt("best_plus", -5).commit()
        assertEquals(0, SharedPrefsSettingsStore(context).bestPlus)
    }

    @Test
    fun winRecordsDefaultToNoneAndRoundTripPerMode() {
        val store = SharedPrefsSettingsStore(context)
        assertNull(store.win)
        assertNull(store.winPlus)
        assertTrue(store.recordWin(GameMode.NORMAL, WinRecord(900, 600_000)))
        assertTrue(store.recordWin(GameMode.PLUS, WinRecord(1500, 900_000)))
        assertEquals(900, prefs.getInt("win_moves", -1))
        assertEquals(600_000L, prefs.getLong("win_time_ms", -1))
        assertEquals(1500, prefs.getInt("win_moves_plus", -1))
        assertEquals(900_000L, prefs.getLong("win_time_ms_plus", -1))
        val again = SharedPrefsSettingsStore(context)
        assertEquals(WinRecord(900, 600_000), again.bestWin(GameMode.NORMAL))
        assertEquals(WinRecord(1500, 900_000), again.bestWin(GameMode.PLUS))
        assertFalse(again.recordWin(GameMode.NORMAL, WinRecord(901, 1)))
        assertTrue(again.recordWin(GameMode.NORMAL, WinRecord(900, 599_999)))
        assertEquals(WinRecord(900, 599_999), SharedPrefsSettingsStore(context).win)
        again.win = null
        assertFalse(prefs.contains("win_moves"))
        assertFalse(prefs.contains("win_time_ms"))
        assertNull(SharedPrefsSettingsStore(context).win)
    }

    @Test
    fun fontScaleDefaultsTo100AndRoundTrips() {
        assertEquals(100, SharedPrefsSettingsStore(context).fontScale)
        SharedPrefsSettingsStore(context).fontScale = 115
        assertEquals(115, prefs.getInt("font_scale", -1))
        assertEquals(115, SharedPrefsSettingsStore(context).fontScale)
    }

    @Test
    fun fontScaleClampsAndMistypedReadsAsDefault() {
        prefs.edit().putInt("font_scale", 400).commit()
        assertEquals(130, SharedPrefsSettingsStore(context).fontScale)
        prefs.edit().putInt("font_scale", 1).commit()
        assertEquals(70, SharedPrefsSettingsStore(context).fontScale)
        prefs.edit().putString("font_scale", "big").commit()
        assertEquals(100, SharedPrefsSettingsStore(context).fontScale)
    }

    @Test
    fun badWinRecordsAreSanitised() {
        prefs.edit().putInt("win_moves", -1).putLong("win_time_ms", 10).commit()
        assertNull(SharedPrefsSettingsStore(context).win)
        prefs.edit().putInt("win_moves", 5).putLong("win_time_ms", -10).commit()
        assertNull(SharedPrefsSettingsStore(context).win)
        prefs.edit().putInt("win_moves", 5).remove("win_time_ms").commit()
        assertNull(SharedPrefsSettingsStore(context).win)
        prefs.edit().putString("win_moves_plus", "x").putLong("win_time_ms_plus", 10).commit()
        assertNull(SharedPrefsSettingsStore(context).winPlus)
    }
}
