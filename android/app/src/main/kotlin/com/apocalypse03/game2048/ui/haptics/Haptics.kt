package com.apocalypse03.game2048.ui.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** One-shot vibration pulses (DESIGN_SPEC section 17). */
interface Haptics {
    /** Plays one pulse of [ms] ms at default amplitude; does nothing when it cannot vibrate. */
    fun pulse(ms: Long)
}

/** [Haptics] that never vibrates: tests, previews and the ViewModel default. */
object NoHaptics : Haptics {
    override fun pulse(ms: Long) = Unit
}

/**
 * [Haptics] on the system vibrator: [VibratorManager] on API 31+, the vibrator service below.
 * `VibrationEffect.createOneShot` on API 26+, the legacy `vibrate(ms)` below. Needs the normal
 * VIBRATE permission. On a device without a vibrator it does nothing.
 */
class AndroidHaptics(context: Context) : Haptics {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    override fun pulse(ms: Long) {
        val v = vibrator ?: return
        if (ms <= 0 || !v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(ms)
        }
    }
}
