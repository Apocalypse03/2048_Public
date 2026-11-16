package com.apocalypse03.game2048.ui.timer

/** Test clock: time moves only when [now] is set or [advance] is called. */
class FakeClock(var now: Long = 1_000L) : MonotonicClock {
    override fun nowMs(): Long = now

    fun advance(ms: Long) {
        now += ms
    }
}
