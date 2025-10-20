package com.arc.launcher

import kotlinx.serialization.Serializable

@Serializable
enum class GestureDirection(val description: String) {
    UP("Swipe Up"),
    DOWN("Swipe Down"),
    LEFT("Swipe Left"),
    RIGHT("Swipe Right"),
    UP_LEFT("Swipe Up-Left"),
    UP_RIGHT("Swipe Up-Right"),
    DOWN_LEFT("Swipe Down-Left"),
    DOWN_RIGHT("Swipe Down-Right"),
    SINGLE_TAP("Single Tap"),
    DOUBLE_TAP("Double Tap")
}

@Serializable
sealed class GestureAction {
    @Serializable
    data class LaunchApp(val packageName: String) : GestureAction()
    @Serializable
    data class LaunchShortcut(val packageName: String, val shortcutId: String) : GestureAction()
}

@Serializable
data class GestureConfig(
    val packageName: String,
    val gesture: GestureDirection,
    val action: GestureAction
)
