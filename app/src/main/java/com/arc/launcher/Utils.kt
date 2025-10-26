package com.arc.launcher

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.ceil

fun findTargetKeyAndSide(
    finalPosition: Offset,
    itemBounds: Map<Any, Rect>
): Pair<Any?, Boolean> {
    var closestKey: Any? = null
    var smallestDistance = Float.MAX_VALUE
    var insertAfter = false

    itemBounds.entries.forEach { entry ->
        val bounds = entry.value
        val distance = (finalPosition - bounds.center).getDistanceSquared()
        if (distance < smallestDistance) {
            smallestDistance = distance
            closestKey = entry.key
            insertAfter = finalPosition.x > bounds.center.x
        }
    }

    return Pair(closestKey, insertAfter)
}

fun calculateSpan(sizeInDp: Int): Int {
    val cellSizeInDp = 96
    return ceil(sizeInDp.toFloat() / cellSizeInDp).toInt().coerceAtLeast(1)
}

fun calculateVerticalSpan(sizeInDp: Int): Int {
    val cellSizeInDp = 96 // This should be the same as the horizontal cell size
    return ceil(sizeInDp.toFloat() / cellSizeInDp).toInt().coerceAtLeast(1)
}
