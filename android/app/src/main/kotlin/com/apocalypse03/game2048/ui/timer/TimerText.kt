package com.apocalypse03.game2048.ui.timer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.apocalypse03.game2048.ui.AppViewModel
import kotlinx.coroutines.delay

/**
 * Timer label for [vm], as a lambda so only the scope that calls it recomposes on each tick.
 * Idle: recomputed when the running flag or the game changes.
 * Running: ticks on each whole second, only while the lifecycle is at least STARTED.
 */
@Composable
fun rememberTimerText(vm: AppViewModel): () -> String {
    val running = vm.timerRunning
    val game = vm.game
    val idle = remember(running, game) { formatElapsed(vm.elapsedMs()) }
    if (!running) return remember(idle) { { idle } }

    val ticking = remember { mutableStateOf(idle) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val ms = vm.elapsedMs()
                ticking.value = formatElapsed(ms)
                delay(1000 - ms % 1000)
            }
        }
    }
    return remember(ticking) { { ticking.value } }
}
