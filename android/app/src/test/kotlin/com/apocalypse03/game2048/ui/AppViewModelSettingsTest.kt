package com.apocalypse03.game2048.ui

import androidx.lifecycle.SavedStateHandle
import com.apocalypse03.game2048.game.Board
import com.apocalypse03.game2048.game.Direction
import com.apocalypse03.game2048.ui.data.InMemoryGameStore
import com.apocalypse03.game2048.ui.data.InMemorySettingsStore
import com.apocalypse03.game2048.ui.haptics.Haptics
import com.apocalypse03.game2048.ui.timer.FakeClock
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Settings values and merge vibration in the ViewModel (DESIGN_SPEC sections 15, 17). */
class AppViewModelSettingsTest {
    private class RecordingHaptics : Haptics {
        val pulses = mutableListOf<Long>()
        override fun pulse(ms: Long) {
            pulses += ms
        }
    }

    private val z = listOf(0, 0, 0, 0)
    private val haptics = RecordingHaptics()
    private val settings = InMemorySettingsStore()

    private fun vm(store: InMemorySettingsStore = settings) = AppViewModel(
        Random(7), FakeClock(), SavedStateHandle(), store, InMemoryGameStore(), haptics,
    )

    /** A fresh game on [board]. */
    private fun playing(board: Board) = vm().apply {
        startNewGame()
        restoreGame(board)
    }

    @Test
    fun readsStoredSettings() {
        val vm = vm(InMemorySettingsStore(animSpeed = 40, vibration = false, vibrationMs = 120))
        assertEquals(40, vm.animSpeed)
        assertFalse(vm.vibration)
        assertEquals(120, vm.vibrationMs)
    }

    @Test
    fun settersCoerceAndSave() {
        val vm = vm()
        vm.setAnimSpeed(-5)
        assertEquals(0, vm.animSpeed)
        vm.setAnimSpeed(999)
        assertEquals(150, vm.animSpeed)
        assertEquals(150, settings.animSpeed)
        vm.setAnimSpeed(35)
        assertEquals(35, settings.animSpeed)
        vm.setVibrationMs(1)
        assertEquals(5, vm.vibrationMs)
        vm.setVibrationMs(500)
        assertEquals(200, vm.vibrationMs)
        vm.setVibrationMs(80)
        assertEquals(80, settings.vibrationMs)
        vm.setVibration(false)
        assertFalse(settings.vibration)
        assertEquals(80, vm(settings).vibrationMs) // a new ViewModel reads them back
    }

    @Test
    fun setFontScaleSavesCoercesAndUpdates() {
        val vm = vm()
        assertEquals(100, vm.fontScale)
        vm.setFontScale(85)
        assertEquals(85, vm.fontScale)
        assertEquals(85, settings.fontScale)
        vm.setFontScale(10)
        assertEquals(70, vm.fontScale)
        assertEquals(70, settings.fontScale)
        vm.setFontScale(500)
        assertEquals(130, vm.fontScale)
        assertEquals(130, vm(settings).fontScale) // a new ViewModel reads it back
    }

    @Test
    fun mergeMovePulsesOnceWithVibrationMs() {
        val vm = playing(Board.of(listOf(listOf(2, 2, 4, 4), z, z, z)))
        vm.setVibrationMs(80)
        vm.move(Direction.LEFT) // two merges, one pulse
        assertEquals(listOf(80L), haptics.pulses)
    }

    @Test
    fun nonMergeMoveDoesNotPulse() {
        val vm = playing(Board.of(listOf(listOf(0, 2, 0, 4), z, z, z)))
        vm.move(Direction.LEFT)
        assertEquals(1, vm.game.moves)
        assertTrue(haptics.pulses.isEmpty())
    }

    @Test
    fun noOpMoveDoesNotPulse() {
        val vm = playing(Board.of(listOf(listOf(2, 4, 0, 0), z, z, z)))
        vm.move(Direction.LEFT)
        assertEquals(0, vm.game.moves)
        assertTrue(haptics.pulses.isEmpty())
    }

    @Test
    fun vibrationOffNeverPulses() {
        val vm = playing(Board.of(listOf(listOf(2, 2, 0, 0), z, z, z)))
        vm.setVibration(false)
        vm.move(Direction.LEFT)
        vm.previewVibration()
        assertTrue(haptics.pulses.isEmpty())
    }

    @Test
    fun previewPulsesWhenOn() {
        val vm = vm()
        vm.setVibrationMs(33)
        vm.previewVibration()
        assertEquals(listOf(33L), haptics.pulses)
    }

    @Test
    fun turningVibrationOnPulsesOnce() {
        val vm = vm()
        vm.setVibration(true) // already on: nothing
        assertTrue(haptics.pulses.isEmpty())
        vm.setVibration(false)
        vm.setVibration(true)
        assertEquals(listOf(50L), haptics.pulses)
        assertTrue(settings.vibration)
    }
}
