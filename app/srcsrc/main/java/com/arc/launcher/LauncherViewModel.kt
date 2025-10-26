package com.arc.launcher

import android.appwidget.AppWidgetHost
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNames
import java.io.File
import java.util.UUID

sealed class LauncherItem {
    data class App(val appInfo: AppInfo) : LauncherItem()
    data class Folder(val folderInfo: FolderInfo) : LauncherItem()
    data class Widget(val widgetId: Int) : LauncherItem()
    data class WidgetStack(val widgets: List<Widget>) : LauncherItem()
}

enum class GestureMode {
    DEFAULT,
    CUSTOM
}

@Serializable
data class FolderInfo(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    val apps: List<AppInfo>,
    var gestureMode: GestureMode = GestureMode.DEFAULT
)

@Serializable
data class WidgetInfo(
    val provider: ComponentName,
    val label: String,
    val targetWidth: Int,
    val targetHeight: Int,
)

class LauncherViewModel : ViewModel() {
    private val json = Json {
        ignoreUnknownKeys = true
        allowStructuredMapKeys = true
    }

    private val _items = MutableStateFlow<List<LauncherItem>>(emptyList())
    val items: StateFlow<List<LauncherItem>> = _items.asStateFlow()

    private val _allApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val allApps: StateFlow<List<AppInfo>> = _allApps.asStateFlow()

    private val _allShortcuts = MutableStateFlow<Map<String, List<AppShortcutInfo>>>(emptyMap())
    val allShortcuts: StateFlow<Map<String, List<AppShortcutInfo>>> = _allShortcuts.asStateFlow()

    var showShortcutsMenu by mutableStateOf<AppInfo?>(null)
        private set

    var showFolderMenu by mutableStateOf<String?>(null)
        private set

    val shortcuts = mutableListOf<AppShortcutInfo>()

    var showGestureConfig by mutableStateOf<Triple<AppInfo, FolderInfo?, Int?>?>(null)
        private set

    var draggedAppFromFolder by mutableStateOf<Pair<AppInfo, FolderInfo>?>(null)
        private set

    var folderToRename by mutableStateOf<FolderInfo?>(null)
        private set

    private val _gestureConfigs = MutableStateFlow<Map<String, GestureConfig>>(emptyMap())
    val gestureConfigs: StateFlow<Map<String, GestureConfig>> = _gestureConfigs.asStateFlow()

    var showWidgetPicker by mutableStateOf(false)
        private set

    var draggedWidget by mutableStateOf<WidgetInfo?>(null)
        private set

    var isDraggingWidget by mutableStateOf(false)
        private set

    fun loadApps(context: Context) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val pm = context.packageManager
                val intent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
                val allAppInfo = pm.queryIntentActivities(intent, 0).mapNotNull {
                    AppInfo(
                        label = it.loadLabel(pm).toString(),
                        packageName = it.activityInfo.packageName,
                        icon = it.loadIcon(pm)
                    )
                }
                _allApps.value = allAppInfo

                loadItems(context, allAppInfo)
                refreshGestureConfigs(context) // Call refresh here to ensure gestures are loaded
                loadAllShortcuts(context, allAppInfo)
            }
        }
    }

    private fun loadItems(context: Context, allAppInfo: List<AppInfo>) {
        val file = File(context.filesDir, "items.json")
        if (file.exists()) {
            val jsonText = file.readText()
            val loadedItems = json.decodeFromString<ItemsWrapper>(jsonText).items
            _items.value = loadedItems.mapNotNull { item ->
                when (item.type) {
                    "app" -> allAppInfo.find { it.packageName == item.packageName }
                        ?.let { LauncherItem.App(it) }

                    "folder" -> {
                        val folderApps = item.apps?.mapNotNull { pkgName ->
                            allAppInfo.find { it.packageName == pkgName }
                        }
                        if (folderApps != null) {
                            LauncherItem.Folder(
                                FolderInfo(
                                    id = item.id ?: UUID.randomUUID().toString(),
                                    name = item.name ?: "Folder",
                                    apps = folderApps,
                                    gestureMode = item.gestureMode ?: GestureMode.DEFAULT
                                )
                            )
                        } else null
                    }
                    "widget" -> item.widgetId?.let { LauncherItem.Widget(it) }
                    "widget_stack" -> item.widgetIds?.let { ids -> LauncherItem.WidgetStack(ids.map { LauncherItem.Widget(it) }) }


                    else -> null
                }
            }
        } else {
            _items.value = allAppInfo.map { LauncherItem.App(it) }
        }
    }

    fun saveItems(context: Context) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val serializableItems = _items.value.map {
                    when (it) {
                        is LauncherItem.App -> LauncherItemSerializable(
                            type = "app",
                            packageName = it.appInfo.packageName
                        )

                        is LauncherItem.Folder -> LauncherItemSerializable(
                            type = "folder",
                            id = it.folderInfo.id,
                            name = it.folderInfo.name,
                            apps = it.folderInfo.apps.map { app -> app.packageName },
                            gestureMode = it.folderInfo.gestureMode
                        )
                        is LauncherItem.Widget -> LauncherItemSerializable(
                            type = "widget",
                            widgetId = it.widgetId
                        )
                        is LauncherItem.WidgetStack -> LauncherItemSerializable(
                            type = "widget_stack",
                            widgetIds = it.widgets.map { widget -> widget.widgetId }
                        )
                    }
                }
                val wrapper = ItemsWrapper(serializableItems)
                val jsonText = json.encodeToString(wrapper)
                File(context.filesDir, "items.json").writeText(jsonText)
            }
        }
    }

    fun moveItem(context: Context, item: LauncherItem, toIndex: Int) {
        val currentList = _items.value.toMutableList()
        val fromIndex = currentList.indexOf(item)
        if (fromIndex != -1 && fromIndex != toIndex) {
            val itemToMove = currentList.removeAt(fromIndex)
            currentList.add(if (toIndex > fromIndex) toIndex - 1 else toIndex, itemToMove)
            _items.value = currentList.toImmutableList()
            saveItems(context)
        }
    }

    fun createFolder(context: Context, app1: LauncherItem.App, app2: LauncherItem) {
        val currentList = _items.value.toMutableList()
        val app1Info = app1.appInfo

        when (app2) {
            is LauncherItem.App -> {
                val app2Info = app2.appInfo
                val newFolder = FolderInfo(
                    name = "Folder",
                    apps = listOf(app1Info, app2Info)
                )
                val app2Index = currentList.indexOf(app2)
                currentList.remove(app1)
                currentList[app2Index] = LauncherItem.Folder(newFolder)
            }

            is LauncherItem.Folder -> {
                val folderInfo = app2.folderInfo
                val folderIndex = currentList.indexOf(app2)
                if (folderIndex != -1) {
                    val updatedApps = folderInfo.apps + app1Info
                    val newFolderInfo = folderInfo.copy(apps = updatedApps)
                    currentList[folderIndex] = LauncherItem.Folder(newFolderInfo)
                    currentList.remove(app1)
                }
            }
            else -> {}
        }

        _items.value = currentList.toImmutableList()
        saveItems(context)
    }

    fun showShortcuts(context: Context, appInfo: AppInfo): Boolean {
        shortcuts.clear()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val shortcutQuery = LauncherApps.ShortcutQuery()
            shortcutQuery.setPackage(appInfo.packageName)
            shortcutQuery.setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC
                        or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
                        or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
            )
            try {
                val shortcutList = launcherApps.getShortcuts(shortcutQuery, android.os.Process.myUserHandle())
                if (shortcutList != null) {
                    shortcuts.addAll(shortcutList.map {
                        AppShortcutInfo(
                            label = it.shortLabel.toString(),
                            icon = launcherApps.getShortcutIconDrawable(it, 0),
                            info = it
                        )
                    })
                }
            } catch (e: SecurityException) {
                showShortcutsMenu = appInfo
                return false
            }
        }
        showShortcutsMenu = appInfo
        return true
    }

    fun hideShortcuts() {
        showShortcutsMenu = null
    }

    fun showFolderMenu(folderInfo: FolderInfo): Boolean {
        showFolderMenu = folderInfo.id
        return true
    }

    fun hideFolderMenu() {
        showFolderMenu = null
    }

    fun toggleGestureMode(context: Context, folderInfo: FolderInfo) {
        val newGestureMode = if (folderInfo.gestureMode == GestureMode.DEFAULT) {
            GestureMode.CUSTOM
        } else {
            GestureMode.DEFAULT
        }
        val currentList = _items.value.toMutableList()
        val folderIndex = currentList.indexOfFirst { it is LauncherItem.Folder && it.folderInfo.id == folderInfo.id }
        if (folderIndex != -1) {
            val oldFolderItem = currentList[folderIndex] as LauncherItem.Folder
            val newFolderInfo = oldFolderItem.folderInfo.copy(gestureMode = newGestureMode)
            currentList[folderIndex] = LauncherItem.Folder(newFolderInfo)
            _items.value = currentList.toImmutableList()
            saveItems(context)
        }
    }

    fun showGestureConfig(appInfo: AppInfo, folderInfo: FolderInfo?, indexInFolder: Int?) {
        showGestureConfig = Triple(appInfo, folderInfo, indexInFolder)
    }

    fun hideGestureConfig() {
        showGestureConfig = null
    }

    private fun refreshGestureConfigs(context: Context) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val file = File(context.filesDir, "gestures.json")
                if (file.exists()) {
                    val jsonText = file.readText()
                    _gestureConfigs.value = json.decodeFromString<Map<String, GestureConfig>>(jsonText)
                } else {
                    _gestureConfigs.value = emptyMap()
                }
            }
        }
    }

    private fun saveGestureConfigs(context: Context) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val jsonText = json.encodeToString(_gestureConfigs.value)
                File(context.filesDir, "gestures.json").writeText(jsonText)
            }
        }
    }

    fun getGestureConfig(key: String): GestureConfig? {
        return _gestureConfigs.value[key]
    }

    fun setGestureConfig(context: Context, key: String, gesture: GestureDirection, action: GestureAction?) {
        val newConfigs = _gestureConfigs.value.toMutableMap()
        if (action != null) {
            newConfigs[key] = GestureConfig(gesture, action)
        } else {
            newConfigs.remove(key)
        }
        _gestureConfigs.value = newConfigs
        saveGestureConfigs(context)
    }

    fun clearAllGestures(context: Context, keyPrefix: String) {
        val newConfigs = _gestureConfigs.value.toMutableMap()
        val keysToRemove = newConfigs.keys.filter { it.startsWith(keyPrefix) }
        keysToRemove.forEach { newConfigs.remove(it) }
        _gestureConfigs.value = newConfigs
        saveGestureConfigs(context)
    }

    private fun loadAllShortcuts(context: Context, allApps: List<AppInfo>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val shortcutsMap = mutableMapOf<String, List<AppShortcutInfo>>()
            allApps.forEach { appInfo ->
                val shortcutQuery = LauncherApps.ShortcutQuery()
                    .setPackage(appInfo.packageName)
                    .setQueryFlags(
                        LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC
                                or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
                                or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                    )
                try {
                    val shortcuts = launcherApps.getShortcuts(shortcutQuery, android.os.Process.myUserHandle())
                    if (shortcuts != null && shortcuts.isNotEmpty()) {
                        shortcutsMap[appInfo.packageName] = shortcuts.map {
                            AppShortcutInfo(
                                label = it.shortLabel.toString(),
                                icon = launcherApps.getShortcutIconDrawable(it, 0),
                                info = it
                            )
                        }
                    }
                } catch (e: SecurityException) {
                    // Ignore
                }
            }
            _allShortcuts.value = shortcutsMap
        }
    }
    fun startDragFromFolder(appInfo: AppInfo, folderInfo: FolderInfo) {
        draggedAppFromFolder = appInfo to folderInfo
    }

    fun endDragFromFolder() {
        draggedAppFromFolder = null
    }

    fun reorderAppInFolder(context: Context, folderId: String, fromIndex: Int, toIndex: Int) {
        val currentList = _items.value.toMutableList()
        val folderIndex = currentList.indexOfFirst { it is LauncherItem.Folder && it.folderInfo.id == folderId }

        if (folderIndex != -1) {
            val oldFolderItem = currentList[folderIndex] as LauncherItem.Folder
            val updatedApps = oldFolderItem.folderInfo.apps.toMutableList()
            if (fromIndex >= 0 && fromIndex < updatedApps.size && fromIndex != toIndex) {
                val appToMove = updatedApps.removeAt(fromIndex)
                val newToIndex = if (toIndex > fromIndex) toIndex - 1 else toIndex
                if (newToIndex >= 0 && newToIndex <= updatedApps.size) {
                    updatedApps.add(newToIndex, appToMove)
                    val newFolderInfo = oldFolderItem.folderInfo.copy(apps = updatedApps)
                    currentList[folderIndex] = LauncherItem.Folder(newFolderInfo)
                    _items.value = currentList.toImmutableList()
                    saveItems(context)
                }
            }
        }
    }

    fun moveAppFromFolderToHome(context: Context, appToMove: AppInfo, fromFolder: FolderInfo, targetIndex: Int) {
        val currentList = _items.value.toMutableList()
        val folderIndex = currentList.indexOfFirst { it is LauncherItem.Folder && it.folderInfo.id == fromFolder.id }
        if (folderIndex != -1) {
            val oldFolderItem = currentList[folderIndex] as LauncherItem.Folder
            val updatedApps = oldFolderItem.folderInfo.apps.filter { it.packageName != appToMove.packageName }
            if (updatedApps.isNotEmpty()) {
                val newFolderInfo = oldFolderItem.folderInfo.copy(apps = updatedApps)
                currentList[folderIndex] = LauncherItem.Folder(newFolderInfo)
            } else {
                currentList.removeAt(folderIndex)
            }
        }

        currentList.add(targetIndex, LauncherItem.App(appToMove))
        _items.value = currentList.toImmutableList()
        saveItems(context)
    }

    fun moveAppFromFolderToApp(context: Context, appToMove: AppInfo, fromFolder: FolderInfo, targetApp: AppInfo) {
        val currentList = _items.value.toMutableList()

        val fromFolderIndex = currentList.indexOfFirst { it is LauncherItem.Folder && it.folderInfo.id == fromFolder.id }
        if (fromFolderIndex != -1) {
            val oldFolderItem = currentList[fromFolderIndex] as LauncherItem.Folder
            val updatedApps = oldFolderItem.folderInfo.apps.filter { it.packageName != appToMove.packageName }
            if (updatedApps.isNotEmpty()) {
                val newFolderInfo = oldFolderItem.folderInfo.copy(apps = updatedApps)
                currentList[fromFolderIndex] = LauncherItem.Folder(newFolderInfo)
            } else {
                currentList.removeAt(fromFolderIndex)
            }
        }

        val targetIndex = currentList.indexOfFirst { it is LauncherItem.App && it.appInfo.packageName == targetApp.packageName }
        if (targetIndex != -1) {
            val newFolder = FolderInfo(name = "Folder", apps = listOf(targetApp, appToMove))
            currentList[targetIndex] = LauncherItem.Folder(newFolder)
        }

        _items.value = currentList.toImmutableList()
        saveItems(context)
    }

    fun moveAppBetweenFolders(context: Context, appToMove: AppInfo, fromFolder: FolderInfo, toFolder: FolderInfo) {
        val currentList = _items.value.toMutableList()
        val fromFolderIndex = currentList.indexOfFirst { it is LauncherItem.Folder && it.folderInfo.id == fromFolder.id }
        var toFolderIndex = currentList.indexOfFirst { it is LauncherItem.Folder && it.folderInfo.id == toFolder.id }

        if (fromFolderIndex != -1 && toFolderIndex != -1) {
            // Remove from source folder
            val fromFolderItem = currentList[fromFolderIndex] as LauncherItem.Folder
            val updatedFromApps = fromFolderItem.folderInfo.apps.filter { it.packageName != appToMove.packageName }

            if (updatedFromApps.isNotEmpty()) {
                val newFromFolder = fromFolderItem.folderInfo.copy(apps = updatedFromApps)
                currentList[fromFolderIndex] = LauncherItem.Folder(newFromFolder)
            } else {
                currentList.removeAt(fromFolderIndex)
                if (fromFolderIndex < toFolderIndex) {
                    toFolderIndex--
                }
            }

            // Add to destination folder
            val toFolderItem = currentList[toFolderIndex] as LauncherItem.Folder
            val updatedToApps = toFolderItem.folderInfo.apps + appToMove
            val newToFolder = toFolderItem.folderInfo.copy(apps = updatedToApps)
            currentList[toFolderIndex] = LauncherItem.Folder(newToFolder)
        }

        _items.value = currentList.toImmutableList()
        saveItems(context)
    }

    fun performFolderGesture(context: Context, folderInfo: FolderInfo, gesture: GestureDirection): Boolean {
        if (folderInfo.gestureMode == GestureMode.DEFAULT) {
            val appToLaunch = if (folderInfo.apps.size <= 4) {
                when (gesture) {
                    GestureDirection.UP_LEFT -> folderInfo.apps.getOrNull(0)
                    GestureDirection.UP_RIGHT -> folderInfo.apps.getOrNull(1)
                    GestureDirection.DOWN_LEFT -> folderInfo.apps.getOrNull(2)
                    GestureDirection.DOWN_RIGHT -> folderInfo.apps.getOrNull(3)
                    GestureDirection.DOUBLE_TAP -> null // Explicitly do nothing for double tap in default mode if <= 4 apps
                    else -> null
                }
            } else {
                when (gesture) {
                    GestureDirection.UP_LEFT -> folderInfo.apps.getOrNull(0)
                    GestureDirection.UP -> folderInfo.apps.getOrNull(1)
                    GestureDirection.UP_RIGHT -> folderInfo.apps.getOrNull(2)
                    GestureDirection.LEFT -> folderInfo.apps.getOrNull(3)
                    GestureDirection.DOUBLE_TAP -> folderInfo.apps.getOrNull(4)
                    GestureDirection.RIGHT -> folderInfo.apps.getOrNull(5)
                    GestureDirection.DOWN_LEFT -> folderInfo.apps.getOrNull(6)
                    GestureDirection.DOWN -> folderInfo.apps.getOrNull(7)
                    GestureDirection.DOWN_RIGHT -> folderInfo.apps.getOrNull(8)
                    else -> null
                }
            }

            appToLaunch?.let {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(it.packageName)
                context.startActivity(launchIntent)
                return true
            }
        } else {
            val key = "folder:${folderInfo.id}:${gesture.name}"
            val config = getGestureConfig(key)
            config?.action?.let { action ->
                executeGestureAction(context, action)
                return true
            }
        }
        return false
    }

    fun hasCustomGestures(appInfo: AppInfo, folderInfo: FolderInfo?, indexInFolder: Int?): Boolean {
        val gestures = GestureDirection.entries.filter { it != GestureDirection.SINGLE_TAP }
        return gestures.any { gesture ->
            val key = if (folderInfo != null && indexInFolder != null) {
                "folder:${folderInfo.id}:$indexInFolder:${gesture.name}"
            } else if (folderInfo != null) {
                "folder:${folderInfo.id}:${gesture.name}"
            } else {
                "app:${appInfo.packageName}:${gesture.name}"
            }
            _gestureConfigs.value.containsKey(key)
        }
    }

    fun executeGestureAction(context: Context, action: GestureAction) {
        when (action) {
            is GestureAction.LaunchApp -> {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(action.packageName)
                context.startActivity(launchIntent)
            }
            is GestureAction.LaunchShortcut -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                    val launcherApps =
                        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                    val shortcutQuery = LauncherApps.ShortcutQuery()
                        .setPackage(action.packageName)
                        .setShortcutIds(listOf(action.shortcutId))
                        .setQueryFlags(
                            LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC
                                    or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
                                    or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                        )
                    val shortcuts =
                        launcherApps.getShortcuts(shortcutQuery, android.os.Process.myUserHandle())
                    if (shortcuts != null && shortcuts.isNotEmpty()) {
                        launcherApps.startShortcut(shortcuts[0], null, null)
                    }
                }
            }
        }
    }

    fun updateFolderName(context: Context, folderId: String, newName: String) {
        val currentList = _items.value.toMutableList()
        val folderIndex = currentList.indexOfFirst { it is LauncherItem.Folder && it.folderInfo.id == folderId }
        if (folderIndex != -1) {
            val oldFolderItem = currentList[folderIndex] as LauncherItem.Folder
            val newFolderInfo = oldFolderItem.folderInfo.copy(name = newName)
            currentList[folderIndex] = LauncherItem.Folder(newFolderInfo)
            _items.value = currentList.toImmutableList()
            saveItems(context)
        }
    }

    fun startRenameFolder(folderInfo: FolderInfo) {
        folderToRename = folderInfo
    }

    fun finishRenameFolder() {
        folderToRename = null
    }

    fun addWidget(context: Context, widgetId: Int, provider: ComponentName, targetIndex: Int) {
        // Not implemented
    }

    fun removeWidget(context: Context, widgetId: Int) {
        // Not implemented
    }

    fun stackWidgets(context: Context, widget1: LauncherItem.Widget, widget2: LauncherItem.Widget) {
        // Not implemented
    }

    fun showWidgetPicker(context: Context) {
        showWidgetPicker = true
    }

    fun hideWidgetPicker() {
        showWidgetPicker = false
    }

    fun startDragWidget(widgetInfo: WidgetInfo) {
        draggedWidget = widgetInfo
        isDraggingWidget = true
    }

    fun endDragWidget() {
        draggedWidget = null
        isDraggingWidget = false
    }

    fun removeAppFromFolder(context: Context, appToMove: AppInfo, fromFolder: FolderInfo) {
        // Not implemented
    }
    fun removeItem(context: Context, item: LauncherItem) {
        val currentList = _items.value.toMutableList()
        if (currentList.remove(item)) {
            _items.value = currentList.toImmutableList()
            saveItems(context)
        }
    }


    @Serializable
    private data class LauncherItemSerializable(
        val type: String,
        val packageName: String? = null,
        val id: String? = null,
        val name: String? = null,
        @JsonNames("appPackages")
        val apps: List<String>? = null,
        val gestureMode: GestureMode? = null,
        val widgetId: Int? = null,
        val widgetIds: List<Int>? = null,
    )

    @Serializable
    private data class ItemsWrapper(val items: List<LauncherItemSerializable>)
}
