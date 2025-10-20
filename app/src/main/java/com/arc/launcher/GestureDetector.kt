package com.arc.launcher

import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.atan2

fun getSwipeDirection(dragAmount: Offset): GestureDirection {
    val (dx, dy) = dragAmount
    val angle = atan2(dy.toDouble(), dx.toDouble()) * (180 / Math.PI)
    return when {
        angle > -22.5 && angle <= 22.5 -> GestureDirection.RIGHT
        angle > 22.5 && angle <= 67.5 -> GestureDirection.DOWN_RIGHT
        angle > 67.5 && angle <= 112.5 -> GestureDirection.DOWN
        angle > 112.5 && angle <= 157.5 -> GestureDirection.DOWN_LEFT
        angle > 157.5 || angle <= -157.5 -> GestureDirection.LEFT
        angle > -157.5 && angle <= -112.5 -> GestureDirection.UP_LEFT
        angle > -112.5 && angle <= -67.5 -> GestureDirection.UP
        angle > -67.5 && angle <= -22.5 -> GestureDirection.UP_RIGHT
        else -> GestureDirection.UP // Default to UP
    }
}

fun Modifier.unifiedGestureDetector(
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onLongPress: () -> Unit,
    onSwipe: (GestureDirection) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    swipeThreshold: Float,
    doubleTapTimeout: Long
): Modifier = this.pointerInput(Unit) {
    var lastTapTime = 0L
    var pendingTapJob: Job? = null

    forEachGesture {
        coroutineScope {
            awaitPointerEventScope {
                val down = awaitFirstDown(requireUnconsumed = false)
                pendingTapJob?.cancel() // Cancel any pending tap from previous gesture
                down.consume()

                var gestureConsumed = false
                var longPressFired = false
                var dragStarted = false

                val longPressJob = launch {
                    delay(500L) // ViewConfiguration.getLongPressTimeout()
                    if (!dragStarted) {
                        onLongPress()
                        longPressFired = true
                    }
                }

                var totalDrag = Offset.Zero

                while (!gestureConsumed) {
                    val event = awaitPointerEvent()
                    val dragChange = event.changes.firstOrNull { it.id == down.id }

                    if (dragChange == null) {
                        longPressJob.cancel()
                        if (dragStarted) onDragCancel()
                        break
                    }

                    if (dragChange.pressed) {
                        val positionChange = dragChange.positionChange()
                        totalDrag += positionChange

                        if (longPressFired) {
                            if (!dragStarted) {
                                onDragStart()
                                dragStarted = true
                            }
                            onDrag(dragChange, positionChange)
                            dragChange.consume()
                        } else {
                            if (totalDrag.getDistance() > viewConfiguration.touchSlop) {
                                longPressJob.cancel()
                            }
                        }
                    } else { // UP event
                        longPressJob.cancel()

                        if (dragStarted) {
                            onDragEnd()
                        } else if (!longPressFired) {
                            if (totalDrag.getDistance() > swipeThreshold) {
                                onSwipe(getSwipeDirection(totalDrag))
                                lastTapTime = 0
                            } else {
                                // Tap or Double Tap
                                val currentTime = System.currentTimeMillis()
                                if (currentTime - lastTapTime < doubleTapTimeout) {
                                    pendingTapJob?.cancel()
                                    onDoubleTap()
                                    lastTapTime = 0
                                } else {
                                    lastTapTime = currentTime
                                    pendingTapJob = launch {
                                        delay(doubleTapTimeout)
                                        onTap()
                                    }
                                }
                            }
                        }

                        event.changes.forEach { if (it.pressed.not()) it.consume() }
                        gestureConsumed = true
                    }
                }
            }
        }
    }
}
