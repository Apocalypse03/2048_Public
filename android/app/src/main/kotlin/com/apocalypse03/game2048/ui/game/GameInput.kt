package com.apocalypse03.game2048.ui.game

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.unit.dp
import com.apocalypse03.game2048.game.Direction
import kotlin.math.abs

/** Travel from the touch-down point at which a drag becomes a move (DESIGN_SPEC section 7). */
internal val SWIPE_THRESHOLD = 20.dp

/** A flick: released before [SWIPE_THRESHOLD] with at least this travel and [FLICK_MIN_VELOCITY]. */
internal val FLICK_MIN_TRAVEL = 10.dp

/** Minimum release speed of a flick along its dominant axis, in dp per second. */
internal val FLICK_MIN_VELOCITY = 400.dp

/**
 * Maps an accumulated drag (screen px, y down) to a direction once the dominant axis reaches
 * [threshold]; null below it. Ties go to the horizontal axis.
 */
fun swipeDirection(dx: Float, dy: Float, threshold: Float): Direction? {
    val ax = abs(dx)
    val ay = abs(dy)
    return when {
        ax >= ay && ax >= threshold -> if (dx < 0) Direction.LEFT else Direction.RIGHT
        ay > ax && ay >= threshold -> if (dy < 0) Direction.UP else Direction.DOWN
        else -> null
    }
}

/** Arrow keys and D-pad to directions; null for any other key. */
fun keyDirection(key: Key): Direction? = when (key) {
    Key.DirectionLeft -> Direction.LEFT
    Key.DirectionRight -> Direction.RIGHT
    Key.DirectionUp -> Direction.UP
    Key.DirectionDown -> Direction.DOWN
    else -> null
}

/**
 * Swipe-to-move over the whole area of this modifier (DESIGN_SPEC section 7). Adds nothing when
 * [enabled] is false.
 *
 * Per gesture it tracks the first pointer from its down position, in the Main pass, so children
 * (buttons, scrollers, scrims) see every event first:
 * - a move or release that a child already consumed abandons the gesture (the down itself is
 *   ignored: tap targets always consume it);
 * - once the total travel reaches [SWIPE_THRESHOLD] on the dominant axis, [onSwipe] is called once
 *   and this and all later changes of the gesture are consumed, so a button under the finger does
 *   not also click;
 * - a release before that with at least [FLICK_MIN_TRAVEL] of travel and [FLICK_MIN_VELOCITY]
 *   along the dominant axis, in the direction of travel, also calls [onSwipe] once;
 * - a gesture that never fires consumes nothing, so taps keep working.
 */
fun Modifier.swipeToMove(enabled: Boolean, onSwipe: (Direction) -> Unit): Modifier =
    if (!enabled) {
        this
    } else {
        composed {
            val swipe by rememberUpdatedState(onSwipe)
            pointerInput(Unit) {
                val threshold = SWIPE_THRESHOLD.toPx()
                val flickTravel = FLICK_MIN_TRAVEL.toPx()
                val flickVelocity = FLICK_MIN_VELOCITY.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val tracker = VelocityTracker()
                    tracker.addPointerInputChange(down)
                    var fired = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (fired) {
                            event.changes.forEach { it.consume() }
                            if (!change.pressed) break
                            continue
                        }
                        // A child (scroller, slider, scrim, a tapped button) took it: not a move.
                        if (change.isConsumed) break
                        tracker.addPointerInputChange(change)
                        val dx = change.position.x - down.position.x
                        val dy = change.position.y - down.position.y
                        var dir = swipeDirection(dx, dy, threshold)
                        if (dir == null && !change.pressed) {
                            val v = tracker.calculateVelocity()
                            // Speed along the dominant axis, positive in the direction of travel.
                            val along = if (abs(dx) >= abs(dy)) v.x * dx else v.y * dy
                            val travel = maxOf(abs(dx), abs(dy))
                            if (travel > 0f && along / travel >= flickVelocity) {
                                dir = swipeDirection(dx, dy, flickTravel)
                            }
                        }
                        if (dir != null) {
                            fired = true
                            swipe(dir)
                            event.changes.forEach { it.consume() }
                        }
                        if (!change.pressed) break
                    }
                }
            }
        }
    }
