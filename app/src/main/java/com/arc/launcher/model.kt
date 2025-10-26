package com.arc.launcher

import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class AppInfo(
    val label: String,
    val packageName: String,
    @Transient var icon: Drawable? = null
)

data class AppShortcutInfo(
    val label: String,
    val icon: Drawable? = null,
    val info: ShortcutInfo? = null
)
