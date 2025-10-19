package com.arc.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arc.launcher.ui.theme.LauncherTheme
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.math.abs
import kotlin.math.atan2
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
    var isLongPressHandled by remember { mutableStateOf(false) }
    val density = LocalDensity.current


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
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = {
                                    if (item is LauncherItem.App) {
                                        viewModel.showShortcuts(context, item.appInfo)
                                        isLongPressHandled = true
                                    }
                                },
                                onPress = {
                                    awaitRelease()
                                    if (!isLongPressHandled) {
                                        if (item is LauncherItem.App) {
                                            val launchIntent: Intent? =
                                                context.packageManager.getLaunchIntentForPackage(
                                                    item.appInfo.packageName
                                                )
                                            context.startActivity(launchIntent)
                                        }
                                    }
                                    isLongPressHandled = false
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            var totalDragAmount = Offset.Zero
                            detectDragGestures(
                                onDragStart = {
                                    totalDragAmount = Offset.Zero
                                    if (isLongPressHandled) {
                                        viewModel.hideShortcuts()
                                        itemBounds[itemKey]?.let { bounds ->
                                            draggedItem = item
                                            dragOffset = bounds.topLeft
                                            draggedItemSize = bounds.size
                                            Log.d(
                                                "DragDrop",
                                                "onDragStart: draggedItem=$draggedItem"
                                            )
                                        }
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    if (draggedItem != null) {
                                        Log.d("DragDrop", "onDrag: $dragAmount")
                                        change.consume()
                                        dragOffset += dragAmount

                                        val currentDragPosition =
                                            dragOffset + Offset(
                                                draggedItemSize.width / 2,
                                                draggedItemSize.height / 2
                                            )
                                        val newDropTarget = itemBounds.entries
                                            .find { (key, bounds) ->
                                                itemKey != key && bounds.contains(
                                                    currentDragPosition
                                                )
                                            }
                                            ?.key
                                            ?.let { key ->
                                                items.find { launcherItem ->
                                                    val currentKey = when (launcherItem) {
                                                        is LauncherItem.App -> "app_${launcherItem.appInfo.packageName}"
                                                        is LauncherItem.Folder -> "folder_${launcherItem.folderInfo.id}"
                                                    }
                                                    currentKey == key
                                                }
                                            }
                                        if (newDropTarget != dropTarget) {
                                            Log.d(
                                                "DragDrop",
                                                "dropTarget changed from $dropTarget to $newDropTarget"
                                            )
                                            dropTarget = newDropTarget
                                        }
                                    } else if (!isLongPressHandled) {
                                        totalDragAmount += dragAmount
                                        change.consume()
                                    }
                                },
                                onDragCancel = {
                                    Log.d("DragDrop", "onDragCancel")
                                    draggedItem = null
                                    dragOffset = Offset.Zero
                                    dropTarget = null
                                },
                                onDragEnd = {
                                    Log.d(
                                        "DragDrop",
                                        "onDragEnd: draggedItem=$draggedItem, dropTarget=$dropTarget"
                                    )
                                    val currentDraggedItem = draggedItem
                                    if (currentDraggedItem != null) {
                                        dropTarget?.let { target ->
                                            if (currentDraggedItem is LauncherItem.App) {
                                                viewModel.createFolder(
                                                    context,
                                                    currentDraggedItem,
                                                    target
                                                )
                                            }
                                        } ?: run {
                                            val finalPosition = dragOffset
                                            Log.d("DragDrop", "finalPosition: $finalPosition")

                                            val closestItem =
                                                itemBounds.minByOrNull { (_, bounds) ->
                                                    val dx =
                                                        (bounds.left - finalPosition.x).pow(2)
                                                    val dy = (bounds.top - finalPosition.y).pow(2)
                                                    sqrt(dx + dy)
                                                }
                                            Log.d("DragDrop", "closestItem: $closestItem")

                                            val targetIndex = closestItem?.key?.let { key ->
                                                items.indexOfFirst { item ->
                                                    val currentKey = when (item) {
                                                        is LauncherItem.App -> "app_${item.appInfo.packageName}"
                                                        is LauncherItem.Folder -> "folder_${item.folderInfo.id}"
                                                    }
                                                    currentKey == key
                                                }
                                            } ?: items.size
                                            Log.d("DragDrop", "targetIndex: $targetIndex")
                                            viewModel.moveItem(
                                                context,
                                                currentDraggedItem,
                                                targetIndex
                                            )
                                        }
                                    } else if (!isLongPressHandled) {
                                        val swipeThreshold = with(density) { 48.dp.toPx() }
                                        if (totalDragAmount.getDistance() > swipeThreshold) {
                                            if (item is LauncherItem.App) {
                                                val direction = getSwipeDirection(totalDragAmount)
                                                Log.d("Gestures", "Swipe detected: $direction for ${item.appInfo.packageName}")
                                                val config = viewModel.getGestureConfig(item.appInfo.packageName, direction)
                                                config?.action?.let { action ->
                                                    executeGestureAction(context, action)
                                                }
                                            }
                                        }
                                    }
                                    draggedItem = null
                                    dragOffset = Offset.Zero
                                    dropTarget = null
                                }
                            )
                        }
                        .graphicsLayer {
                            alpha = if (isBeingDragged) 0f else 1f
                        }
                ) {
                    when (item) {
                        is LauncherItem.App -> AppItem(
                            appInfo = item.appInfo,
                            viewModel = viewModel
                        )

                        is LauncherItem.Folder -> FolderItem(
                            folderInfo = item.folderInfo,
                            viewModel = viewModel
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
                        viewModel = viewModel
                    )
                    is LauncherItem.Folder -> FolderItem(
                        folderInfo = item.folderInfo,
                        viewModel = viewModel
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
                // Sheet content
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

        appToShowGestureConfig?.let { appInfo ->
            GestureConfigDialog(
                appInfo = appInfo,
                onDismiss = { viewModel.hideGestureConfig() },
                viewModel = viewModel
            )
        }
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
                val shortcuts =
                    launcherApps.getShortcuts(shortcutQuery, android.os.Process.myUserHandle())
                if (shortcuts != null && shortcuts.isNotEmpty()) {
                    launcherApps.startShortcut(shortcuts[0], null, null)
                }
            }
        }
    }
}

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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppItem(
    appInfo: AppInfo,
    viewModel: LauncherViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val showShortcutsMenu = viewModel.showShortcutsMenu == appInfo
    val shortcuts = viewModel.shortcuts

    Box(
        modifier = modifier
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
        DropdownMenu(
            expanded = showShortcutsMenu,
            onDismissRequest = { viewModel.hideShortcuts() }
        ) {
            DropdownMenuItem(
                text = { Text("Gestures") },
                onClick = {
                    viewModel.showGestureConfig(appInfo)
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

@Composable
fun FolderItem(
    folderInfo: FolderInfo,
    viewModel: LauncherViewModel,
    modifier: Modifier = Modifier
) {
    var showFolderDialog by remember { mutableStateOf(false) }

    Box(modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .combinedClickable(
                    onClick = { showFolderDialog = true },
                    onLongClick = { /* Handle long click for folder */ }
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            folderInfo.apps.firstOrNull()?.icon?.let {
                Image(
                    painter = rememberDrawablePainter(drawable = it),
                    contentDescription = folderInfo.name,
                    modifier = Modifier.size(64.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = folderInfo.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    if (showFolderDialog) {
        AlertDialog(
            onDismissRequest = { showFolderDialog = false },
            title = { Text(folderInfo.name) },
            text = {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 128.dp),
                ) {
                    items(folderInfo.apps) { app ->
                        AppItem(appInfo = app, viewModel = viewModel)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFolderDialog = false }) {
                    Text("Close")
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
                    items(GestureDirection.values()) { gesture ->
                        val config = viewModel.getGestureConfig(appInfo.packageName, gesture)
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
            onActionSelected = { action ->
                viewModel.setGestureConfig(appInfo.packageName, showActionChooser!!, action)
                showActionChooser = null
            },
            onDismiss = { showActionChooser = null }
        )
    }
}

@Composable
fun ActionChooserDialog(
    viewModel: LauncherViewModel,
    onActionSelected: (GestureAction) -> Unit,
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
                                    onActionSelected(GestureAction.LaunchApp(app.packageName))
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
                                                    )
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
