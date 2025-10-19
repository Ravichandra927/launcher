package com.arc.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Build
import android.os.Process
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

@Serializable
sealed class LauncherItem {
    @Serializable
    data class App(val appInfo: AppInfo) : LauncherItem()
    @Serializable
    data class Folder(val folderInfo: FolderInfo) : LauncherItem()
}

@Serializable
data class FolderInfo(
    val id: String = UUID.randomUUID().toString(),
    var apps: MutableList<AppInfo>,
    var name: String = "Folder"
)

class LauncherViewModel : ViewModel() {

    private val _items = MutableStateFlow<List<LauncherItem>>(emptyList())
    val items = _items.asStateFlow()

    private val _allApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val allApps = _allApps.asStateFlow()

    private val _allShortcuts = MutableStateFlow<Map<String, List<AppShortcutInfo>>>(emptyMap())
    val allShortcuts = _allShortcuts.asStateFlow()

    private val _gestureConfigs = MutableStateFlow<Map<String, GestureConfig>>(emptyMap())
    val gestureConfigs = _gestureConfigs.asStateFlow()

    var showShortcutsMenu by mutableStateOf<AppInfo?>(null)
    var shortcuts by mutableStateOf<List<AppShortcutInfo>>(emptyList())
    var showGestureConfig by mutableStateOf<AppInfo?>(null)

    fun loadApps(context: Context) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val pm = context.packageManager

                val sharedPrefs =
                    context.getSharedPreferences("launcher_layout", Context.MODE_PRIVATE)
                val layoutJson = sharedPrefs.getString("layout", null)

                if (layoutJson != null) {
                    val serializableLayout = Json.decodeFromString<List<LauncherItem>>(layoutJson)
                    _items.value = serializableLayout.map { item ->
                        when (item) {
                            is LauncherItem.App -> {
                                val intent = Intent(Intent.ACTION_MAIN, null).apply {
                                    addCategory(Intent.CATEGORY_LAUNCHER)
                                    setPackage(item.appInfo.packageName)
                                }
                                val resolveInfo =
                                    pm.queryIntentActivities(intent, 0).firstOrNull()
                                if (resolveInfo != null) {
                                    item.appInfo.icon = resolveInfo.loadIcon(pm)
                                }
                            }
                            is LauncherItem.Folder -> {
                                item.folderInfo.apps.forEach { app ->
                                    val intent = Intent(Intent.ACTION_MAIN, null).apply {
                                        addCategory(Intent.CATEGORY_LAUNCHER)
                                        setPackage(app.packageName)
                                    }
                                    val resolveInfo =
                                        pm.queryIntentActivities(intent, 0).firstOrNull()
                                    if (resolveInfo != null) {
                                        app.icon = resolveInfo.loadIcon(pm)
                                    }
                                }
                            }
                        }
                        item
                    }
                } else {
                    val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                        addCategory(Intent.CATEGORY_LAUNCHER)
                    }
                    val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
                    val apps = resolveInfos.map {
                        LauncherItem.App(
                            AppInfo(
                                label = it.loadLabel(pm).toString(),
                                packageName = it.activityInfo.packageName,
                                icon = it.loadIcon(pm)
                            )
                        )
                    }.sortedBy { (it.appInfo.label) }
                    _items.value = apps
                }
                loadAllAppsAndShortcuts(context)
                loadGestureConfigs(context)
            }
        }
    }

    private suspend fun loadAllAppsAndShortcuts(context: Context) {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
        _allApps.value = resolveInfos.map {
            AppInfo(
                label = it.loadLabel(pm).toString(),
                packageName = it.activityInfo.packageName,
                icon = it.loadIcon(pm)
            )
        }.sortedBy { it.label }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            val launcherApps =
                context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val shortcuts = mutableMapOf<String, List<AppShortcutInfo>>()
            for (app in _allApps.value) {
                val query = LauncherApps.ShortcutQuery()
                    .setQueryFlags(
                        LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                                LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                                LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                    )
                    .setPackage(app.packageName)

                val result: List<ShortcutInfo> = try {
                    launcherApps.getShortcuts(query, Process.myUserHandle()) ?: emptyList()
                } catch (e: Exception) {
                    Log.e("Launcher", "Error getting shortcuts", e)
                    emptyList()
                }

                shortcuts[app.packageName] =
                    result.filter { it.isEnabled }.mapNotNull { shortcut ->
                        try {
                            AppShortcutInfo(
                                label = shortcut.shortLabel?.toString() ?: "",
                                icon = launcherApps.getShortcutIconDrawable(shortcut, 0),
                                info = shortcut
                            )
                        } catch (e: Exception) {
                            Log.e("Launcher", "Failed to map shortcut", e)
                            null
                        }
                    }
            }
            _allShortcuts.value = shortcuts
        }
    }

    fun saveLayout(context: Context) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val layoutJson = Json.encodeToString(_items.value)
                val sharedPrefs =
                    context.getSharedPreferences("launcher_layout", Context.MODE_PRIVATE)
                sharedPrefs.edit().putString("layout", layoutJson).apply()
            }
        }
    }

    fun saveGestureConfigs(context: Context) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val gestureConfigsJson = Json.encodeToString(_gestureConfigs.value)
                val sharedPrefs =
                    context.getSharedPreferences("gesture_configs", Context.MODE_PRIVATE)
                sharedPrefs.edit().putString("configs", gestureConfigsJson).apply()
            }
        }
    }

    private fun loadGestureConfigs(context: Context) {
        val sharedPrefs = context.getSharedPreferences("gesture_configs", Context.MODE_PRIVATE)
        val gestureConfigsJson = sharedPrefs.getString("configs", null)
        if (gestureConfigsJson != null) {
            _gestureConfigs.value = Json.decodeFromString(gestureConfigsJson)
        }
    }

    fun setGestureConfig(packageName: String, gesture: GestureDirection, action: GestureAction) {
        val key = "$packageName:${gesture.name}"
        val newConfig = GestureConfig(packageName, gesture, action)
        _gestureConfigs.value = _gestureConfigs.value.toMutableMap().apply {
            this[key] = newConfig
        }
    }

    fun getGestureConfig(packageName: String, gesture: GestureDirection): GestureConfig? {
        val key = "$packageName:${gesture.name}"
        return _gestureConfigs.value[key]
    }

    fun showShortcuts(context: Context, appInfo: AppInfo) {
        showShortcutsMenu = appInfo
        viewModelScope.launch {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                withContext(Dispatchers.IO) {
                    val launcherApps =
                        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                    val query = LauncherApps.ShortcutQuery()
                        .setQueryFlags(
                            LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                        )
                        .setPackage(appInfo.packageName)

                    val result: List<ShortcutInfo> = try {
                        launcherApps.getShortcuts(query, Process.myUserHandle()) ?: emptyList()
                    } catch (e: Exception) {
                        Log.e("Launcher", "Error getting shortcuts", e)
                        emptyList()
                    }

                    shortcuts = result.filter { it.isEnabled }.mapNotNull { shortcut ->
                        try {
                            AppShortcutInfo(
                                label = shortcut.shortLabel?.toString() ?: "",
                                icon = launcherApps.getShortcutIconDrawable(shortcut, 0),
                                info = shortcut
                            )
                        } catch (e: Exception) {
                            Log.e("Launcher", "Failed to map shortcut", e)
                            null
                        }
                    }
                }
            }
        }
    }

    fun hideShortcuts() {
        showShortcutsMenu = null
        shortcuts = emptyList()
    }

    fun showGestureConfig(appInfo: AppInfo) {
        showGestureConfig = appInfo
    }

    fun hideGestureConfig() {
        showGestureConfig = null
    }

    fun createFolder(context: Context, draggedItem: LauncherItem.App, targetItem: LauncherItem) {
        Log.d("DragDrop", "createFolder: draggedItem=$draggedItem, targetItem=$targetItem")
        val currentItems = _items.value.toMutableList()

        when (targetItem) {
            is LauncherItem.App -> {
                val draggedIndex = currentItems.indexOf(draggedItem)
                val targetIndex = currentItems.indexOf(targetItem)
                if (draggedIndex == -1 || targetIndex == -1) {
                    Log.d("DragDrop", "createFolder: item not found in list")
                    return
                }

                val newFolder = LauncherItem.Folder(
                    FolderInfo(
                        apps = mutableListOf(draggedItem.appInfo, targetItem.appInfo),
                        name = "Folder"
                    )
                )

                val firstIndex = min(draggedIndex, targetIndex)
                val secondIndex = max(draggedIndex, targetIndex)

                currentItems.removeAt(secondIndex)
                currentItems.removeAt(firstIndex)

                currentItems.add(firstIndex, newFolder)
                Log.d("DragDrop", "createFolder: created new folder=$newFolder")
            }

            is LauncherItem.Folder -> {
                val draggedIndex = currentItems.indexOf(draggedItem)
                if (draggedIndex != -1) {
                    targetItem.folderInfo.apps.add(draggedItem.appInfo)
                    currentItems.removeAt(draggedIndex)
                    Log.d("DragDrop", "createFolder: added to existing folder=$targetItem")
                } else {
                    Log.d("DragDrop", "createFolder: draggedItem not found for folder drop")
                }
            }
        }
        _items.value = currentItems
        saveLayout(context)
    }

    fun moveItem(context: Context, draggedItem: LauncherItem, targetIndex: Int) {
        Log.d("DragDrop", "moveItem: draggedItem=$draggedItem, targetIndex=$targetIndex")
        val currentItems = _items.value.toMutableList()
        val fromIndex = currentItems.indexOf(draggedItem)
        if (fromIndex != -1) {
            val item = currentItems.removeAt(fromIndex)
            val newTargetIndex = targetIndex.coerceIn(0, currentItems.size)
            currentItems.add(newTargetIndex, item)
            _items.value = currentItems
            saveLayout(context)
        } else {
            Log.d("DragDrop", "moveItem: draggedItem not found in list")
        }
    }
}
