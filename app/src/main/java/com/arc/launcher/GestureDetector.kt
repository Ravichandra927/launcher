package com.arc.launcher

import android.util.Log
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
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
    onDoubleTap: () -> Boolean,
    onLongPress: () -> Boolean,
    onSwipe: (GestureDirection) -> Boolean,
    onDragStart: () -> Unit,
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    swipeThreshold: Float,
    doubleTapTimeout: Long
): Modifier = composed {
    val scope = rememberCoroutineScope()
    val gestureState = remember {
        object {
            var lastTapTime = 0L
            var pendingTapJob: Job? = null
        }
    }

    pointerInput(Unit) {
        forEachGesture {
            coroutineScope {
                awaitPointerEventScope {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    gestureState.pendingTapJob?.cancel()

                    var gestureHandled = false
                    var longPressFired = false
                    var dragStarted = false

                    val longPressJob = launch {
                        delay(500L) // ViewConfiguration.getLongPressTimeout()
                        if (!dragStarted) {
                            if (onLongPress()) {
                                longPressFired = true
                                gestureHandled = true
                            }
                        }
                    }

                    var totalDrag = Offset.Zero
                    var loop = true
                    while (loop) {
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
                                    if (totalDrag.getDistance() > viewConfiguration.touchSlop) {
                                        onDragStart()
                                        dragStarted = true
                                        gestureHandled = true
                                    }
                                }
                                if (dragStarted) {
                                    onDrag(dragChange, positionChange)
                                    dragChange.consume()
                                }
                            } else {
                                if (totalDrag.getDistance() > viewConfiguration.touchSlop) {
                                    longPressJob.cancel()
                                    dragChange.consume()
                                }
                            }
                        } else { // UP event
                            longPressJob.cancel()
                            loop = false

                            if (dragStarted) {
                                onDragEnd()
                            } else if (!longPressFired) {
                                if (totalDrag.getDistance() > swipeThreshold) {
                                    if (onSwipe(getSwipeDirection(totalDrag))) {
                                        gestureState.lastTapTime = 0
                                        gestureHandled = true
                                    }
                                } else {
                                    val currentTime = System.currentTimeMillis()
                                    if (currentTime - gestureState.lastTapTime < doubleTapTimeout) {
                                        gestureState.pendingTapJob?.cancel()
                                        if (onDoubleTap()) {
                                            gestureHandled = true
                                        }
                                        gestureState.lastTapTime = 0
                                    } else {
                                        gestureState.lastTapTime = currentTime
                                        gestureState.pendingTapJob = scope.launch {
                                            delay(doubleTapTimeout)
                                            onTap()
                                        }
                                        gestureHandled = true
                                    }
                                }
                            }

                            if (gestureHandled) {
                                down.consume()
                                dragChange.consume()
                            }
                        }
                    }
                }
            }
        }
    }
}