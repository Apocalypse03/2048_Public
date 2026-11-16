package com.apocalypse03.game2048.ui.data

import android.content.Context
import com.apocalypse03.game2048.ui.GameMode
import com.apocalypse03.game2048.ui.theme.AppThemes

/**
 * Best win of a mode (DESIGN_SPEC section 13): [moves] moves in [timeMs] of game time.
 * Recorded only; not shown anywhere yet.
 */
data class WinRecord(val moves: Int, val timeMs: Long) {
    /** Fewer moves wins; with equal moves, the shorter time. */
    fun isBetterThan(other: WinRecord?): Boolean =
        other == null || moves < other.moves || (moves == other.moves && timeMs < other.timeMs)
}

/**
 * Durable settings that outlive the game session: Best per mode ([best] for Normal, [bestPlus]
 * for 2048+), theme id, the selected game mode, the best win per mode ([win], [winPlus]), the
 * Settings screen values (DESIGN_SPEC section 15) and the result lists (section 16).
 */
interface SettingsStore {
    var best: Int
    var bestPlus: Int
    var themeId: Int
    var mode: GameMode
    var win: WinRecord?
    var winPlus: WinRecord?

    /** Animation speed in percent, [ANIM_SPEED_MIN]..[ANIM_SPEED_MAX]. */
    var animSpeed: Int

    /** User Font Size in percent, [FONT_SCALE_MIN]..[FONT_SCALE_MAX] (DESIGN_SPEC section 15). */
    var fontScale: Int

    /** Vibration on merges. */
    var vibration: Boolean

    /** Vibration pulse length in ms, [VIBRATION_MS_MIN]..[VIBRATION_MS_MAX]. */
    var vibrationMs: Int

    /** Raw Normal result list ([GameResults.encode] format), or null when none. */
    var results: String?

    /** Raw 2048+ result list, or null when none. */
    var resultsPlus: String?

    /** Next game id to hand out (>= 1). */
    var resultNextId: Long

    /** Result list of [mode], sanitised and ordered. */
    fun results(mode: GameMode): List<GameResult> = GameResults.decode(
        when (mode) {
            GameMode.NORMAL -> results
            GameMode.PLUS -> resultsPlus
        },
    )

    /** Writes the result list of [mode]. */
    fun setResults(mode: GameMode, list: List<GameResult>) {
        val text = GameResults.encode(list)
        when (mode) {
            GameMode.NORMAL -> results = text
            GameMode.PLUS -> resultsPlus = text
        }
    }

    /** Best of [mode]. */
    fun best(mode: GameMode): Int = when (mode) {
        GameMode.NORMAL -> best
        GameMode.PLUS -> bestPlus
    }

    /** Writes the Best of [mode]. */
    fun setBest(mode: GameMode, value: Int) {
        when (mode) {
            GameMode.NORMAL -> best = value
            GameMode.PLUS -> bestPlus = value
        }
    }

    /** Best win of [mode], or null when that mode was never won. */
    fun bestWin(mode: GameMode): WinRecord? = when (mode) {
        GameMode.NORMAL -> win
        GameMode.PLUS -> winPlus
    }

    /** Stores [record] as the best win of [mode] if it beats the stored one; returns whether it did. */
    fun recordWin(mode: GameMode, record: WinRecord): Boolean {
        if (!record.isBetterThan(bestWin(mode))) return false
        when (mode) {
            GameMode.NORMAL -> win = record
            GameMode.PLUS -> winPlus = record
        }
        return true
    }

    companion object {
        const val ANIM_SPEED_MIN = 0
        const val ANIM_SPEED_MAX = 150
        const val ANIM_SPEED_DEFAULT = 100
        const val FONT_SCALE_MIN = 70
        const val FONT_SCALE_MAX = 130
        const val FONT_SCALE_DEFAULT = 100
        const val FONT_SCALE_STEP = 5
        const val VIBRATION_MS_MIN = 5
        const val VIBRATION_MS_MAX = 200
        const val VIBRATION_MS_DEFAULT = 50
    }
}

/**
 * [SettingsStore] in the app's private SharedPreferences (replaces best_score.txt).
 * Only the theme id is stored, never the variant. Reads are sanitised: a negative Best reads as 0,
 * an old dark theme id 17..26 migrates to its spec id ([AppThemes.migrateId], written back), an
 * unknown theme id reads as 0, an unknown or mistyped mode as Normal, a missing, negative or mistyped win record as none. Animation speed,
 * font size and vibration length clamp to their ranges; any mistyped value reads as its default (results: none,
 * next id: 1, a value below 1 also reads as 1). Writes use apply().
 */
class SharedPrefsSettingsStore(context: Context) : SettingsStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override var best: Int
        get() = prefs.getInt(KEY_BEST, 0).coerceAtLeast(0)
        set(value) = prefs.edit().putInt(KEY_BEST, value).apply()

    override var bestPlus: Int
        get() = prefs.getInt(KEY_BEST_PLUS, 0).coerceAtLeast(0)
        set(value) = prefs.edit().putInt(KEY_BEST_PLUS, value).apply()

    override var themeId: Int
        get() {
            val raw = readInt(KEY_THEME_ID, 0)
            // Ids 17..26 (the old separate dark themes) become their spec id; written back once.
            val migrated = AppThemes.migrateId(raw)
            if (migrated != raw) prefs.edit().putInt(KEY_THEME_ID, migrated).apply()
            return migrated.takeIf { id -> AppThemes.all.any { it.id == id } } ?: 0
        }
        set(value) = prefs.edit().putInt(KEY_THEME_ID, value).apply()

    override var mode: GameMode
        get() {
            val name = try {
                prefs.getString(KEY_MODE, null)
            } catch (e: ClassCastException) {
                null
            }
            return GameMode.entries.firstOrNull { it.name == name } ?: GameMode.NORMAL
        }
        set(value) = prefs.edit().putString(KEY_MODE, value.name).apply()

    override var win: WinRecord?
        get() = readWin(KEY_WIN_MOVES, KEY_WIN_TIME)
        set(value) = writeWin(KEY_WIN_MOVES, KEY_WIN_TIME, value)

    override var winPlus: WinRecord?
        get() = readWin(KEY_WIN_MOVES_PLUS, KEY_WIN_TIME_PLUS)
        set(value) = writeWin(KEY_WIN_MOVES_PLUS, KEY_WIN_TIME_PLUS, value)

    override var animSpeed: Int
        get() = readInt(KEY_ANIM_SPEED, SettingsStore.ANIM_SPEED_DEFAULT)
            .coerceIn(SettingsStore.ANIM_SPEED_MIN, SettingsStore.ANIM_SPEED_MAX)
        set(value) = prefs.edit().putInt(KEY_ANIM_SPEED, value).apply()

    override var fontScale: Int
        get() = readInt(KEY_FONT_SCALE, SettingsStore.FONT_SCALE_DEFAULT)
            .coerceIn(SettingsStore.FONT_SCALE_MIN, SettingsStore.FONT_SCALE_MAX)
        set(value) = prefs.edit().putInt(KEY_FONT_SCALE, value).apply()

    override var vibration: Boolean
        get() = try {
            prefs.getBoolean(KEY_VIBRATION, true)
        } catch (e: ClassCastException) {
            true
        }
        set(value) = prefs.edit().putBoolean(KEY_VIBRATION, value).apply()

    override var vibrationMs: Int
        get() = readInt(KEY_VIBRATION_MS, SettingsStore.VIBRATION_MS_DEFAULT)
            .coerceIn(SettingsStore.VIBRATION_MS_MIN, SettingsStore.VIBRATION_MS_MAX)
        set(value) = prefs.edit().putInt(KEY_VIBRATION_MS, value).apply()

    override var results: String?
        get() = readString(KEY_RESULTS)
        set(value) = prefs.edit().putString(KEY_RESULTS, value).apply()

    override var resultsPlus: String?
        get() = readString(KEY_RESULTS_PLUS)
        set(value) = prefs.edit().putString(KEY_RESULTS_PLUS, value).apply()

    override var resultNextId: Long
        get() = try {
            prefs.getLong(KEY_RESULT_NEXT_ID, 1L).coerceAtLeast(1L)
        } catch (e: ClassCastException) {
            1L
        }
        set(value) = prefs.edit().putLong(KEY_RESULT_NEXT_ID, value).apply()

    private fun readInt(key: String, default: Int): Int = try {
        prefs.getInt(key, default)
    } catch (e: ClassCastException) {
        default
    }

    private fun readString(key: String): String? = try {
        prefs.getString(key, null)
    } catch (e: ClassCastException) {
        null
    }

    private fun readWin(movesKey: String, timeKey: String): WinRecord? {
        if (!prefs.contains(movesKey) || !prefs.contains(timeKey)) return null
        return try {
            WinRecord(prefs.getInt(movesKey, -1), prefs.getLong(timeKey, -1L))
                .takeIf { it.moves >= 0 && it.timeMs >= 0 }
        } catch (e: ClassCastException) {
            null
        }
    }

    private fun writeWin(movesKey: String, timeKey: String, value: WinRecord?) {
        prefs.edit().apply {
            if (value == null) {
                remove(movesKey)
                remove(timeKey)
            } else {
                putInt(movesKey, value.moves)
                putLong(timeKey, value.timeMs)
            }
        }.apply()
    }

    private companion object {
        const val PREFS_NAME = "game2048"
        const val KEY_BEST = "best"
        const val KEY_BEST_PLUS = "best_plus"
        const val KEY_THEME_ID = "theme_id"
        const val KEY_MODE = "mode"
        const val KEY_WIN_MOVES = "win_moves"
        const val KEY_WIN_TIME = "win_time_ms"
        const val KEY_WIN_MOVES_PLUS = "win_moves_plus"
        const val KEY_WIN_TIME_PLUS = "win_time_ms_plus"
        const val KEY_ANIM_SPEED = "anim_speed"
        const val KEY_FONT_SCALE = "font_scale"
        const val KEY_VIBRATION = "vibration"
        const val KEY_VIBRATION_MS = "vibration_ms"
        const val KEY_RESULTS = "results"
        const val KEY_RESULTS_PLUS = "results_plus"
        const val KEY_RESULT_NEXT_ID = "result_next_id"
    }
}

/**
 * Non-durable [SettingsStore] for tests and previews. Animation speed, font size and vibration length clamp
 * like [SharedPrefsSettingsStore]; result strings are kept raw and sanitised on read.
 */
class InMemorySettingsStore(
    override var best: Int = 0,
    override var themeId: Int = 0,
    override var bestPlus: Int = 0,
    override var mode: GameMode = GameMode.NORMAL,
    override var win: WinRecord? = null,
    override var winPlus: WinRecord? = null,
    animSpeed: Int = SettingsStore.ANIM_SPEED_DEFAULT,
    fontScale: Int = SettingsStore.FONT_SCALE_DEFAULT,
    override var vibration: Boolean = true,
    vibrationMs: Int = SettingsStore.VIBRATION_MS_DEFAULT,
    override var results: String? = null,
    override var resultsPlus: String? = null,
    resultNextId: Long = 1L,
) : SettingsStore {
    override var animSpeed: Int = animSpeed
        get() = field.coerceIn(SettingsStore.ANIM_SPEED_MIN, SettingsStore.ANIM_SPEED_MAX)

    override var fontScale: Int = fontScale
        get() = field.coerceIn(SettingsStore.FONT_SCALE_MIN, SettingsStore.FONT_SCALE_MAX)

    override var vibrationMs: Int = vibrationMs
        get() = field.coerceIn(SettingsStore.VIBRATION_MS_MIN, SettingsStore.VIBRATION_MS_MAX)

    override var resultNextId: Long = resultNextId
        get() = field.coerceAtLeast(1L)
}
