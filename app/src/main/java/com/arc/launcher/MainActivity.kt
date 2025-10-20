package com.arc.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.view.ViewConfiguration
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arc.launcher.ui.theme.LauncherTheme
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.math.pow
import kotlin.math.sqrt

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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LauncherTheme {
                AppList()
            }
        }
    }
}

private fun findTargetIndex(
    finalPosition: Offset,
    itemBounds: Map<Any, Rect>,
    items: List<LauncherItem>
): Int {
    val closestItem = itemBounds.minByOrNull { (_, bounds) ->
        sqrt((bounds.left - finalPosition.x).pow(2) + (bounds.top - finalPosition.y).pow(2))
    }
    return closestItem?.key?.let { key ->
        items.indexOfFirst { item ->
            val currentKey = when (item) {
                is LauncherItem.App -> "app_${item.appInfo.packageName}"
                is LauncherItem.Folder -> "folder_${item.folderInfo.id}"
            }
            currentKey == key
        }
    } ?: items.size
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AppList(viewModel: LauncherViewModel = viewModel()) {
    val context = LocalContext.current
    val items by viewModel.items.collectAsState()
    val appToShowGestureConfig by remember { derivedStateOf { viewModel.showGestureConfig } }

    LaunchedEffect(Unit) {
        viewModel.loadApps(context)
    }

    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    var showBottomSheet by remember { mutableStateOf(false) }

    val itemBounds = remember { mutableStateMapOf<Any, Rect>() }

    var draggedItem by remember { mutableStateOf<LauncherItem?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dropTarget by remember { mutableStateOf<LauncherItem?>(null) }
    var draggedItemSize by remember { mutableStateOf(Size.Zero) }
    val density = LocalDensity.current
    var expandedFolderId by remember { mutableStateOf<String?>(null) }


    val onFolderAppDrag = { change: PointerInputChange, dragAmount: Offset ->
        change.consume()
        dragOffset += dragAmount
        val currentDragPosition = dragOffset + Offset(draggedItemSize.width / 2, draggedItemSize.height / 2)
        val newDropTarget = itemBounds.entries.find { (key, bounds) ->
            val draggedItemKey = "app_${(draggedItem as LauncherItem.App).appInfo.packageName}"
            draggedItemKey != key && bounds.contains(currentDragPosition)
        }?.key?.let { key ->
            items.find { launcherItem ->
                val currentKey = when (launcherItem) {
                    is LauncherItem.App -> "app_${launcherItem.appInfo.packageName}"
                    is LauncherItem.Folder -> "folder_${launcherItem.folderInfo.id}"
                }
                currentKey == key
            }
        }
        if (newDropTarget != dropTarget) {
            dropTarget = newDropTarget
        }
    }

    val onFolderAppDragEnd = {
        val (appToMove, fromFolder) = viewModel.draggedAppFromFolder!!
        when (val target = dropTarget) {
            is LauncherItem.App -> viewModel.moveAppFromFolderToApp(context, appToMove, fromFolder, target.appInfo)
            is LauncherItem.Folder -> viewModel.moveAppBetweenFolders(context, appToMove, fromFolder, target.folderInfo)
            null -> {
                val targetIndex = findTargetIndex(dragOffset, itemBounds, items)
                viewModel.moveAppFromFolderToHome(context, appToMove, fromFolder, targetIndex)
            }
        }
        viewModel.endDragFromFolder()
        draggedItem = null
        dragOffset = Offset.Zero
        dropTarget = null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .combinedClickable(
                onClick = {},
                onLongClick = {
                    showBottomSheet = true
                }
            )
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 96.dp),
            contentPadding = WindowInsets.systemBars
                .add(WindowInsets(top = 16.dp))
                .asPaddingValues(),
            userScrollEnabled = draggedItem == null
        ) {
            items(items, key = { item ->
                when (item) {
                    is LauncherItem.App -> "app_${item.appInfo.packageName}"
                    is LauncherItem.Folder -> "folder_${item.folderInfo.id}"
                }
            }) { item ->
                val itemKey = when (item) {
                    is LauncherItem.App -> "app_${item.appInfo.packageName}"
                    is LauncherItem.Folder -> "folder_${item.folderInfo.id}"
                }
                val isBeingDragged = draggedItem == item
                val isDropTarget = dropTarget == item && !isBeingDragged

                val scale by animateFloatAsState(if (isDropTarget) 1.1f else 1f, label = "scale")

                Box(
                    modifier = Modifier
                        .scale(scale)
                        .onGloballyPositioned { coordinates ->
                            itemBounds[itemKey] =
                                Rect(coordinates.positionInRoot(), coordinates.size.toSize())
                        }
                        .graphicsLayer {
                            alpha = if (isBeingDragged) 0f else 1f
                        }
                ) {
                    when (item) {
                        is LauncherItem.App -> AppItem(
                            appInfo = item.appInfo,
                            viewModel = viewModel,
                            onDragStart = {
                                viewModel.hideShortcuts()
                                itemBounds[itemKey]?.let { bounds ->
                                    draggedItem = item
                                    dragOffset = bounds.topLeft
                                    draggedItemSize = bounds.size
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffset += dragAmount
                                val currentDragPosition = dragOffset + Offset(
                                    draggedItemSize.width / 2,
                                    draggedItemSize.height / 2
                                )
                                val newDropTarget = itemBounds.entries.find { (key, bounds) ->
                                    itemKey != key && bounds.contains(currentDragPosition)
                                }?.key?.let { key ->
                                    items.find { launcherItem ->
                                        val currentKey = when (launcherItem) {
                                            is LauncherItem.App -> "app_${launcherItem.appInfo.packageName}"
                                            is LauncherItem.Folder -> "folder_${launcherItem.folderInfo.id}"
                                        }
                                        currentKey == key
                                    }
                                }
                                if (newDropTarget != dropTarget) {
                                    dropTarget = newDropTarget
                                }
                            },
                            onDragEnd = {
                                dropTarget?.let { target ->
                                    if (draggedItem is LauncherItem.App) {
                                        viewModel.createFolder(
                                            context,
                                            draggedItem as LauncherItem.App,
                                            target
                                        )
                                    }
                                } ?: run {
                                    val targetIndex =
                                        findTargetIndex(dragOffset, itemBounds, items)
                                    viewModel.moveItem(context, draggedItem!!, targetIndex)
                                }
                                draggedItem = null
                                dragOffset = Offset.Zero
                                dropTarget = null
                            },
                            onDragCancel = {
                                draggedItem = null
                                dragOffset = Offset.Zero
                                dropTarget = null
                            }
                        )

                        is LauncherItem.Folder -> FolderItem(
                            folderInfo = item.folderInfo,
                            viewModel = viewModel,
                            isExpanded = expandedFolderId == item.folderInfo.id,
                            onExpandRequest = {
                                expandedFolderId = if (it) item.folderInfo.id else null
                            },
                            onAppDragStart = { appInfo, folderInfo, offset, size ->
                                viewModel.startDragFromFolder(appInfo, folderInfo)
                                draggedItem = LauncherItem.App(appInfo)
                                dragOffset = offset
                                draggedItemSize = size
                            },
                            onAppDrag = onFolderAppDrag,
                            onAppDragEnd = onFolderAppDragEnd,
                            onAppDragCancel = onFolderAppDragEnd
                        )
                    }
                }
            }
        }

        if (draggedItem != null) {
            val density = LocalDensity.current
            Box(
                modifier = Modifier
                    .zIndex(1f)
                    .size(with(density) { draggedItemSize.toDpSize() })
                    .graphicsLayer {
                        translationX = dragOffset.x
                        translationY = dragOffset.y
                    }
            ) {
                when (val item = draggedItem) {
                    is LauncherItem.App -> AppItem(
                        appInfo = item.appInfo,
                        viewModel = viewModel,
                        isGhost = true
                    )
                    is LauncherItem.Folder -> FolderItem(
                        folderInfo = item.folderInfo,
                        viewModel = viewModel,
                        isExpanded = false,
                        onExpandRequest = {},
                        onAppDragStart = { _, _, _, _ -> },
                        onAppDrag = { _, _ -> },
                        onAppDragEnd = {},
                        onAppDragCancel = {}
                    )
                    null -> {}
                }
            }
        }

        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = {
                    showBottomSheet = false
                },
                sheetState = sheetState
            ) {
                Button(onClick = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) {
                            showBottomSheet = false
                        }
                    }
                }) {
                    Text("Settings")
                }
                Button(onClick = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) {
                            showBottomSheet = false
                        }
                    }
                }) {
                    Text("Widgets")
                }
            }
        }

        appToShowGestureConfig?.let { (appInfo, folderInfo, indexInFolder) ->
            GestureConfigDialog(
                appInfo = appInfo,
                folderInfo = folderInfo,
                indexInFolder = indexInFolder,
                onDismiss = { viewModel.hideGestureConfig() },
                viewModel = viewModel
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppItem(
    appInfo: AppInfo,
    viewModel: LauncherViewModel,
    modifier: Modifier = Modifier,
    folderInfo: FolderInfo? = null,
    indexInFolder: Int? = null,
    isGhost: Boolean = false,
    onDragStart: (() -> Unit)? = null,
    onDrag: ((change: PointerInputChange, dragAmount: Offset) -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null,
    onDragCancel: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val showShortcutsMenu = viewModel.showShortcutsMenu == appInfo && !isGhost
    val shortcuts = viewModel.shortcuts
    val density = LocalDensity.current

    val gestureModifier = if (onDragStart != null && onDrag != null && onDragEnd != null && onDragCancel != null) {
        Modifier.unifiedGestureDetector(
            onTap = {
                val launchIntent: Intent? =
                    context.packageManager.getLaunchIntentForPackage(appInfo.packageName)
                context.startActivity(launchIntent)
            },
            onDoubleTap = {},
            onLongPress = { viewModel.showShortcuts(context, appInfo) },
            onSwipe = { direction ->
                val key = if (folderInfo != null && indexInFolder != null) {
                    "folder:${folderInfo.id}:$indexInFolder:${direction.name}"
                } else {
                    "app:${appInfo.packageName}:${direction.name}"
                }
                val config = viewModel.getGestureConfig(key)
                config?.action?.let { action ->
                    viewModel.executeGestureAction(context, action)
                }
            },
            onDragStart = onDragStart,
            onDrag = onDrag,
            onDragEnd = onDragEnd,
            onDragCancel = onDragCancel,
            swipeThreshold = with(density) { 48.dp.toPx() },
            doubleTapTimeout = ViewConfiguration.getDoubleTapTimeout().toLong()
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier.then(gestureModifier)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            appInfo.icon?.let {
                Image(
                    painter = rememberDrawablePainter(drawable = it),
                    contentDescription = appInfo.label,
                    modifier = Modifier.size(64.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = appInfo.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (!isGhost) {
            DropdownMenu(
                expanded = showShortcutsMenu,
                onDismissRequest = { viewModel.hideShortcuts() }
            ) {
                DropdownMenuItem(
                    text = { Text("Gestures") },
                    onClick = {
                        viewModel.showGestureConfig(appInfo, folderInfo, indexInFolder)
                        viewModel.hideShortcuts()
                    }
                )
                if (shortcuts.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("No shortcuts available") },
                        onClick = { viewModel.hideShortcuts() }
                    )
                } else {
                    shortcuts.forEach { shortcut ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    shortcut.icon?.let { icon ->
                                        Image(
                                            painter = rememberDrawablePainter(drawable = icon),
                                            contentDescription = shortcut.label,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text(text = shortcut.label)
                                }
                            },
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                                    val launcherApps =
                                        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                                    shortcut.info?.let {
                                        launcherApps.startShortcut(it, null, null)
                                    }
                                }
                                viewModel.hideShortcuts()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FolderItem(
    folderInfo: FolderInfo,
    viewModel: LauncherViewModel,
    modifier: Modifier = Modifier,
    isExpanded: Boolean,
    onExpandRequest: (Boolean) -> Unit,
    onAppDragStart: (AppInfo, FolderInfo, Offset, Size) -> Unit,
    onAppDrag: (PointerInputChange, Offset) -> Unit,
    onAppDragEnd: () -> Unit,
    onAppDragCancel: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val showFolderMenu = viewModel.showFolderMenu == folderInfo.id

    key(folderInfo) {
        Box(
            modifier = modifier.unifiedGestureDetector(
                onTap = {
                    onExpandRequest(true)
                },
                onDoubleTap = {
                    viewModel.performFolderGesture(
                        context,
                        folderInfo,
                        GestureDirection.DOUBLE_TAP
                    )
                },
                onLongPress = { viewModel.showFolderMenu(folderInfo) },
                onSwipe = { direction ->
                    viewModel.performFolderGesture(context, folderInfo, direction)
                },
                onDragStart = { },
                onDrag = { _, _ -> },
                onDragEnd = { },
                onDragCancel = { },
                swipeThreshold = with(density) { 48.dp.toPx() },
                doubleTapTimeout = ViewConfiguration.getDoubleTapTimeout().toLong()
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = false
                    ) {
                        items(folderInfo.apps.take(9)) { app ->
                            app.icon?.let {
                                Image(
                                    painter = rememberDrawablePainter(drawable = it),
                                    contentDescription = app.label,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .padding(1.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = folderInfo.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            DropdownMenu(
                expanded = showFolderMenu,
                onDismissRequest = { viewModel.hideFolderMenu() }
            ) {
                if (folderInfo.gestureMode == GestureMode.CUSTOM) {
                    DropdownMenuItem(
                        text = { Text("Gestures") },
                        onClick = {
                            viewModel.showGestureConfig(
                                AppInfo(folderInfo.name, ""),
                                folderInfo,
                                null
                            )
                            viewModel.hideFolderMenu()
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text(if (folderInfo.gestureMode == GestureMode.DEFAULT) "Switch to Custom" else "Switch to Default") },
                    onClick = {
                        viewModel.toggleGestureMode(context, folderInfo)
                        viewModel.hideFolderMenu()
                    }
                )
            }
        }
    }

    if (isExpanded) {
        Dialog(
            onDismissRequest = { onExpandRequest(false) },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            content = {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black.copy(alpha = 0.5f)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) {
                                onExpandRequest(false)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .padding(16.dp)
                        ) {
                            Column {
                                Text(
                                    folderInfo.name,
                                    style = MaterialTheme.typography.headlineSmall,
                                    modifier = Modifier.padding(16.dp)
                                )
                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(minSize = 96.dp),
                                    contentPadding = PaddingValues(16.dp)
                                ) {
                                    items(folderInfo.apps.size) { index ->
                                        val app = folderInfo.apps[index]
                                        var position by remember { mutableStateOf(Offset.Zero) }
                                        var size by remember { mutableStateOf(Size.Zero) }

                                        Box(
                                            modifier = Modifier
                                                .onGloballyPositioned {
                                                    position = it.positionInRoot()
                                                    size = it.size.toSize()
                                                }
                                        ) {
                                            AppItem(
                                                appInfo = app,
                                                viewModel = viewModel,
                                                folderInfo = folderInfo,
                                                indexInFolder = index,
                                                onDragStart = {
                                                    viewModel.hideShortcuts()
                                                    onExpandRequest(false)
                                                    onAppDragStart(
                                                        app,
                                                        folderInfo,
                                                        position,
                                                        size
                                                    )
                                                },
                                                onDrag = onAppDrag,
                                                onDragEnd = onAppDragEnd,
                                                onDragCancel = onAppDragCancel
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        )
    }
}

@Composable
fun AssignedAction(
    action: GestureAction?,
    allApps: List<AppInfo>,
    allShortcuts: Map<String, List<AppShortcutInfo>>
) {
    if (action == null) {
        Text("Assign")
        return
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        var icon: Drawable? = null
        var label: String? = null

        when (action) {
            is GestureAction.LaunchApp -> {
                val app = allApps.find { it.packageName == action.packageName }
                if (app != null) {
                    icon = app.icon
                    label = app.label
                }
            }

            is GestureAction.LaunchShortcut -> {
                val shortcut =
                    allShortcuts[action.packageName]?.find { it.info?.id == action.shortcutId }
                if (shortcut != null) {
                    icon = shortcut.icon
                    label = shortcut.label
                }
            }
        }

        if (label != null) {
            icon?.let {
                Image(
                    painter = rememberDrawablePainter(drawable = it),
                    contentDescription = label,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else {
            Text("Assigned")
        }
    }
}

@Composable
fun GestureConfigDialog(
    appInfo: AppInfo,
    folderInfo: FolderInfo?,
    indexInFolder: Int?,
    onDismiss: () -> Unit,
    viewModel: LauncherViewModel
) {
    val context = LocalContext.current
    var showActionChooser by remember { mutableStateOf<GestureDirection?>(null) }
    val allApps by viewModel.allApps.collectAsState()
    val allShortcuts by viewModel.allShortcuts.collectAsState()

    if (showActionChooser == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Configure Gestures for ${appInfo.label}") },
            text = {
                LazyColumn {
                    items(GestureDirection.values().filter { it != GestureDirection.SINGLE_TAP }) { gesture ->
                        val key = if (folderInfo != null && indexInFolder != null) {
                            "folder:${folderInfo.id}:$indexInFolder:${gesture.name}"
                        } else if (folderInfo != null) {
                            "folder:${folderInfo.id}:${gesture.name}"
                        } else {
                            "app:${appInfo.packageName}:${gesture.name}"
                        }
                        val config = viewModel.getGestureConfig(key)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(gesture.description)
                            Button(onClick = { showActionChooser = gesture }) {
                                AssignedAction(
                                    action = config?.action,
                                    allApps = allApps,
                                    allShortcuts = allShortcuts
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.saveGestureConfigs(context)
                    onDismiss()
                }) {
                    Text("Save")
                }
            }
        )
    } else {
        ActionChooserDialog(
            viewModel = viewModel,
            onActionSelected = { action, selectedApp ->
                val key = if (folderInfo != null && indexInFolder != null) {
                    "folder:${folderInfo.id}:$indexInFolder:${showActionChooser!!.name}"
                } else if (folderInfo != null) {
                    "folder:${folderInfo.id}:${showActionChooser!!.name}"
                } else {
                    "app:${appInfo.packageName}:${showActionChooser!!.name}"
                }
                viewModel.setGestureConfig(key, selectedApp.packageName, showActionChooser!!, action)
                showActionChooser = null
            },
            onDismiss = { showActionChooser = null }
        )
    }
}

@Composable
fun ActionChooserDialog(
    viewModel: LauncherViewModel,
    onActionSelected: (GestureAction, AppInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val allApps by viewModel.allApps.collectAsState()
    val allShortcuts by viewModel.allShortcuts.collectAsState()
    val expandedState = remember { mutableStateMapOf<String, Boolean>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose an action") },
        text = {
            LazyColumn {
                items(allApps) { app ->
                    val isExpanded = expandedState[app.packageName] ?: false
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onActionSelected(GestureAction.LaunchApp(app.packageName), app)
                                }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            app.icon?.let {
                                Image(
                                    painter = rememberDrawablePainter(drawable = it),
                                    contentDescription = app.label,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = app.label, modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                expandedState[app.packageName] = !isExpanded
                            }) {
                                Icon(
                                    imageVector = if (isExpanded) Icons.Filled.ArrowDropDown else Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = if (isExpanded) "Collapse" else "Expand"
                                )
                            }
                        }
                        if (isExpanded) {
                            allShortcuts[app.packageName]?.forEach { shortcut ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            shortcut.info?.let {
                                                onActionSelected(
                                                    GestureAction.LaunchShortcut(
                                                        app.packageName,
                                                        it.id
                                                    ),
                                                    app
                                                )
                                            }
                                        }
                                        .padding(
                                            start = 32.dp,
                                            top = 4.dp,
                                            bottom = 4.dp,
                                            end = 8.dp
                                        ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    shortcut.icon?.let { icon ->
                                        Image(
                                            painter = rememberDrawablePainter(drawable = icon),
                                            contentDescription = shortcut.label,
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text(text = shortcut.label)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {}
    )
}
