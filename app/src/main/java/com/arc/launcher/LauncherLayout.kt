package com.arc.launcher

import kotlinx.serialization.Serializable

@Serializable
data class LauncherLayout(
    val items: List<LauncherItemSerializable>
)

@Serializable
sealed class LauncherItemSerializable {
    @Serializable
    data class App(val packageName: String) : LauncherItemSerializable()
    @Serializable
    data class Folder(val id: String, val appPackages: List<String>, val name: String) : LauncherItemSerializable()
}
