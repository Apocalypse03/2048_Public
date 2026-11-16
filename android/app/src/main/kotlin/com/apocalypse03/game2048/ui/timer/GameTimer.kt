package com.apocalypse03.game2048.ui.timer

import android.os.SystemClock

/** Monotonic time source in milliseconds; injectable so tests can use a fake. */
interface MonotonicClock {
    fun nowMs(): Long
}

/** Wall-clock-independent time since boot, unaffected by clock changes. */
object SystemMonotonicClock : MonotonicClock {
    override fun nowMs(): Long = SystemClock.elapsedRealtime()
}

/** Pausable stopwatch: elapsed = accumulated + (now - startMark) while running. */
class GameTimer(private val clock: MonotonicClock) {
    private var accumulatedMs = 0L
    private var startMark: Long? = null

    val running: Boolean get() = startMark != null

    /** True once the timer has run at least once since the last [reset]. */
    var started = false
        private set

    /** Back to idle at zero. */
    fun reset() {
        accumulatedMs = 0L
        startMark = null
        started = false
    }

    /** Idempotent: starting keeps an existing mark; stopping folds the segment into the total. */
    fun setRunning(run: Boolean) {
        val mark = startMark
        if (run && mark == null) {
            startMark = clock.nowMs()
            started = true
        } else if (!run && mark != null) {
            accumulatedMs += clock.nowMs() - mark
            startMark = null
        }
    }

    /** Restores a saved stopwatch (e.g. after process death); it is left stopped. */
    fun restore(elapsedMs: Long, started: Boolean) {
        accumulatedMs = elapsedMs
        startMark = null
        this.started = started
    }

    fun elapsedMs(): Long = accumulatedMs + (startMark?.let { clock.nowMs() - it } ?: 0L)
}

/** "42S", "1M 05S", "1H 02M 05S" (whole seconds, floored). */
fun formatElapsed(ms: Long): String {
    val total = ms.coerceAtLeast(0L) / 1000
    val h = total / 3600
    val m = total % 3600 / 60
    val s = total % 60
    fun two(v: Long) = v.toString().padStart(2, '0')
    return when {
        h > 0 -> "${h}H ${two(m)}M ${two(s)}S"
        m > 0 -> "${m}M ${two(s)}S"
        else -> "${s}S"
    }
}
