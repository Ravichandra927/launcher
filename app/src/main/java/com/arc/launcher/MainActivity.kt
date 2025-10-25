package com.arc.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.ViewConfiguration
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.items // Explicitly import this items function for LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as lazyGridItems // Alias for LazyVerticalGrid items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.SouthEast
import androidx.compose.material.icons.filled.SouthWest
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
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.TextFieldValue
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

private fun findTargetIndexInFolder(
    finalPosition: Offset,
    itemBounds: Map<Any, Rect>,
    items: List<AppInfo>
): Int {
    val closestItem = itemBounds.minByOrNull { (_, bounds) ->
        sqrt((bounds.left - finalPosition.x).pow(2) + (bounds.top - finalPosition.y).pow(2))
    }
    return closestItem?.key?.let { key ->
        items.indexOfFirst { item ->
            "app_${item.packageName}" == key
        }
    } ?: items.size
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AppList(viewModel: LauncherViewModel = viewModel()) {
    BackHandler(enabled = true) {}

    val context = LocalContext.current
    val items by viewModel.items.collectAsState()
    val appToShowGestureConfig by remember { derivedStateOf { viewModel.showGestureConfig } }
    val folderToRename by remember { derivedStateOf { viewModel.folderToRename } }
    var longPressHandled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadApps(context)
    }

    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    var showBottomSheet by remember { mutableStateOf(false) }

    val itemBounds = remember { mutableStateMapOf<Any, Rect>() }
    val folderItemBounds = remember { mutableStateMapOf<Any, Rect>() }
    var folderContentBounds by remember { mutableStateOf<Rect?>(null) }


    var draggedItem by remember { mutableStateOf<LauncherItem?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dropTarget by remember { mutableStateOf<LauncherItem?>(null) }
    var draggedItemSize by remember { mutableStateOf(Size.Zero) }
    var expandedFolderId by remember { mutableStateOf<String?>(null) }
    var folderVisualsVisible by remember { mutableStateOf(true) }
    var isDraggingOutOfFolder by remember { mutableStateOf(false) }


    val onFolderAppDrag = { change: PointerInputChange, dragAmount: Offset ->
        change.consume()
        dragOffset += dragAmount
        val currentDragPosition = dragOffset + Offset(draggedItemSize.width / 2, draggedItemSize.height / 2)

        val isInFolder = folderContentBounds?.contains(currentDragPosition) ?: false
        if (!isInFolder) {
            isDraggingOutOfFolder = true
        }

        if (isDraggingOutOfFolder) {
            if (folderVisualsVisible) {
                folderVisualsVisible = false
            }
        } else {
            if (folderVisualsVisible != isInFolder) {
                folderVisualsVisible = isInFolder
            }
        }

        val currentBounds = if (isInFolder && !isDraggingOutOfFolder) folderItemBounds else itemBounds
        val newDropTarget = currentBounds.entries.find { (key, bounds) ->
            val draggedItemKey = when (val item = draggedItem) {
                is LauncherItem.App -> "app_${item.appInfo.packageName}"
                is LauncherItem.Folder -> "folder_${item.folderInfo.id}"
                null -> "" // Explicitly handle null case for draggedItem
            }
            draggedItemKey != key && bounds.contains(currentDragPosition)
        }?.key?.let { key ->
            items.find { launcherItem ->
                when (launcherItem) {
                    is LauncherItem.App -> "app_${launcherItem.appInfo.packageName}"
                    is LauncherItem.Folder -> "folder_${launcherItem.folderInfo.id}"
                } == key
            }
        }
        if (newDropTarget != dropTarget) {
            dropTarget = newDropTarget
        }
    }

    val onFolderAppDragEnd = {
        val (appToMove, fromFolder) = viewModel.draggedAppFromFolder!!
        val currentDragPosition = dragOffset + Offset(draggedItemSize.width / 2, draggedItemSize.height / 2)
        val isInFolder = folderContentBounds?.contains(currentDragPosition) ?: false && !isDraggingOutOfFolder

        if (isInFolder) {
            val fromIndex = fromFolder.apps.indexOf(appToMove)
            val toIndex = findTargetIndexInFolder(currentDragPosition, folderItemBounds, fromFolder.apps)
            viewModel.reorderAppInFolder(context, fromFolder.id, fromIndex, toIndex)
        } else {
            when (val target = dropTarget) {
                is LauncherItem.App -> viewModel.moveAppFromFolderToApp(context, appToMove, fromFolder, target.appInfo)
                is LauncherItem.Folder -> {
                    if (fromFolder.id == target.folderInfo.id) {
                        val toIndex = findTargetIndexInFolder(currentDragPosition, folderItemBounds, fromFolder.apps)
                        viewModel.reorderAppInFolder(context, fromFolder.id, 0, toIndex)
                    } else {
                        viewModel.moveAppBetweenFolders(context, appToMove, fromFolder, target.folderInfo)
                    }
                }
                null -> {
                    val targetIndex = findTargetIndex(dragOffset, itemBounds, items)
                    viewModel.moveAppFromFolderToHome(context, appToMove, fromFolder, targetIndex)
                }
            }
        }
        viewModel.endDragFromFolder()
        draggedItem = null
        dragOffset = Offset.Zero
        dropTarget = null
        expandedFolderId = null
        folderVisualsVisible = true
        isDraggingOutOfFolder = false
    }

    val onFolderAppDragCancel = {
        viewModel.endDragFromFolder()
        draggedItem = null
        dragOffset = Offset.Zero
        dropTarget = null
        expandedFolderId = null
        folderVisualsVisible = true
        isDraggingOutOfFolder = false
    }

    val onDrag = { change: PointerInputChange, dragAmount: Offset ->
        change.consume()
        dragOffset += dragAmount
        val currentDragPosition = dragOffset + Offset(
            draggedItemSize.width / 2,
            draggedItemSize.height / 2
        )
        val itemKey = when (val item = draggedItem) {
            is LauncherItem.App -> "app_${item.appInfo.packageName}"
            is LauncherItem.Folder -> "folder_${item.folderInfo.id}"
            null -> "" // Explicitly handle null case for draggedItem
        }
        val newDropTarget = itemBounds.entries.find { (key, bounds) ->
            itemKey != key && bounds.contains(currentDragPosition)
        }?.key?.let { key ->
            items.find { launcherItem ->
                when (launcherItem) {
                    is LauncherItem.App -> "app_${launcherItem.appInfo.packageName}"
                    is LauncherItem.Folder -> "folder_${launcherItem.folderInfo.id}"
                } == key
            }
        }
        if (newDropTarget != dropTarget) {
            dropTarget = newDropTarget
        }
    }

    val onDragEnd = {
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
    }

    val onDragCancel = {
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
                    if (!longPressHandled) {
                        showBottomSheet = true
                    }
                    longPressHandled = false
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
            lazyGridItems(items = items, key = { item -> // Using aliased lazyGridItems
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
                            onLongPressHandled = { longPressHandled = true },
                            onDragStart = {
                                viewModel.hideShortcuts()
                                itemBounds[itemKey]?.let { bounds ->
                                    draggedItem = item
                                    dragOffset = bounds.topLeft
                                    draggedItemSize = bounds.size
                                }
                            },
                            onDrag = onDrag,
                            onDragEnd = onDragEnd,
                            onDragCancel = onDragCancel
                        )

                        is LauncherItem.Folder -> FolderItem(
                            folderInfo = item.folderInfo,
                            viewModel = viewModel,
                            onExpandRequest = {
                                if (draggedItem == null) {
                                    expandedFolderId = item.folderInfo.id
                                    folderVisualsVisible = true
                                }
                            },
                            onLongPressHandled = { longPressHandled = true },
                            onDragStart = {
                                viewModel.hideShortcuts()
                                itemBounds[itemKey]?.let { bounds ->
                                    draggedItem = item
                                    dragOffset = bounds.topLeft
                                    draggedItemSize = bounds.size
                                }
                            },
                            onDrag = onDrag,
                            onDragEnd = onDragEnd,
                            onDragCancel = onDragCancel
                        )
                    }
                }
            }
        }

        val expandedFolderInfo = items.filterIsInstance<LauncherItem.Folder>()
            .find { it.folderInfo.id == expandedFolderId }?.folderInfo

        if (expandedFolderInfo != null) {
            ExpandedFolderDialog(
                folderInfo = expandedFolderInfo,
                viewModel = viewModel,
                visible = folderVisualsVisible,
                onDismissRequest = {
                    if (draggedItem == null) {
                        expandedFolderId = null
                        folderVisualsVisible = true
                        folderItemBounds.clear()
                    }
                },
                onLongPressHandled = { longPressHandled = true },
                onAppDragStart = { appInfo, folderInfo, offset, size ->
                    viewModel.startDragFromFolder(appInfo, folderInfo)
                    draggedItem = LauncherItem.App(appInfo)
                    dragOffset = offset
                    draggedItemSize = size
                },
                onAppDrag = onFolderAppDrag,
                onAppDragEnd = onFolderAppDragEnd,
                onAppDragCancel = onFolderAppDragCancel,
                folderItemBounds = folderItemBounds,
                onFolderContentPositioned = { folderContentBounds = it }
            )
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
                        isGhost = true,
                        onLongPressHandled = {}
                    )
                    is LauncherItem.Folder -> FolderItem(
                        folderInfo = item.folderInfo,
                        viewModel = viewModel,
                        onExpandRequest = {},
                        onLongPressHandled = {},
                        onDragStart = {},
                        onDrag = { _, _ -> },
                        onDragEnd = {},
                        onDragCancel = {}
                    )
                    null -> {} // Added null branch for exhaustiveness
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

        folderToRename?.let { folder ->
            RenameFolderDialog(
                folderInfo = folder,
                viewModel = viewModel,
                onDismiss = { viewModel.finishRenameFolder() }
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
    onLongPressHandled: () -> Unit,
    onDragStart: (() -> Unit)? = null,
    onDrag: ((change: PointerInputChange, dragAmount: Offset) -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null,
    onDragCancel: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val showShortcutsMenu = viewModel.showShortcutsMenu == appInfo && !isGhost
    val shortcuts = viewModel.shortcuts
    val density = LocalDensity.current
    val hasGestures by viewModel.gestureConfigs.collectAsState().let { state ->
        remember(state.value) {
            derivedStateOf {
                viewModel.hasCustomGestures(appInfo, folderInfo, indexInFolder)
            }
        }
    }
    var hasShortcutPermission by remember { mutableStateOf(true) }

    val gestureModifier = if (onDragStart != null && onDrag != null && onDragEnd != null && onDragCancel != null) {
        Modifier.unifiedGestureDetector(
            onTap = {
                val launchIntent: Intent? =
                    context.packageManager.getLaunchIntentForPackage(appInfo.packageName)
                context.startActivity(launchIntent)
            },
            onDoubleTap = {
                val key = if (folderInfo != null && indexInFolder != null) {
                    "folder:${folderInfo.id}:$indexInFolder:${GestureDirection.DOUBLE_TAP.name}"
                } else {
                    "app:${appInfo.packageName}:${GestureDirection.DOUBLE_TAP.name}"
                }
                val config = viewModel.getGestureConfig(key)
                config?.action?.let { action ->
                    viewModel.executeGestureAction(context, action)
                    return@unifiedGestureDetector true
                }
                return@unifiedGestureDetector false
            },
            onLongPress = {
                onLongPressHandled()
                hasShortcutPermission = viewModel.showShortcuts(context, appInfo)
				return@unifiedGestureDetector true
            },
            onSwipe = { direction ->
                val key = if (folderInfo != null && indexInFolder != null) {
                    "folder:${folderInfo.id}:$indexInFolder:${direction.name}"
                }
                else {
                    "app:${appInfo.packageName}:${direction.name}"
                }
                val config = viewModel.getGestureConfig(key)
                config?.action?.let { action ->
                    viewModel.executeGestureAction(context, action)
                    return@unifiedGestureDetector true
                }
                return@unifiedGestureDetector false
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
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .pointerInput(Unit) {},
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
            ) {
                appInfo.icon?.let {
                    Image(
                        painter = rememberDrawablePainter(drawable = it),
                        contentDescription = appInfo.label,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (hasGestures) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width - 12f, 12f)
                        drawCircle(
                            color = Color.Black.copy(alpha = 0.5f),
                            radius = 10f,
                            center = center
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 8f,
                            center = center
                        )
                    }
                }
                Spacer(
                    modifier = Modifier
                        .matchParentSize()
                        .scale(0.9f)
                        .then(gestureModifier)
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
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Gesture,
                            contentDescription = "Gestures"
                        )
                    }
                )
                if (!hasShortcutPermission) {
                    DropdownMenuItem(
                        text = { Text("Set as default to view shortcuts") },
                        onClick = {
                            val intent = Intent(android.provider.Settings.ACTION_HOME_SETTINGS)
                            context.startActivity(intent)
                            viewModel.hideShortcuts()
                        }
                    )
                } else if (shortcuts.isNotEmpty()) {
                    shortcuts.forEach { shortcut ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    shortcut.icon?.let {
                                        Image(
                                            painter = rememberDrawablePainter(drawable = it),
                                            contentDescription = shortcut.label,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text(text = shortcut.label)
                                 }
                            },
                            onClick = {
                                val launcherApps =
                                    context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                                shortcut.info?.let {
                                    launcherApps.startShortcut(it, null, null)
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
    onExpandRequest: () -> Unit,
    onLongPressHandled: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (PointerInputChange, Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val showFolderMenu = viewModel.showFolderMenu == folderInfo.id
    val hasGestures by viewModel.gestureConfigs.collectAsState().let { state ->
        remember(state.value) {
            derivedStateOf {
                viewModel.hasCustomGestures(AppInfo(folderInfo.name, ""), folderInfo, null)
            }
        }
    }

    key(folderInfo) {
        Box(
            modifier = modifier
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .pointerInput(Unit) {},
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier.unifiedGestureDetector(
                            onTap = {
                                onExpandRequest()
                            },
                            onDoubleTap = {
                                return@unifiedGestureDetector viewModel.performFolderGesture(
                                    context,
                                    folderInfo,
                                    GestureDirection.DOUBLE_TAP
                                )
                            },
                            onLongPress = {
                                onLongPressHandled()
                                viewModel.showFolderMenu(folderInfo)
                                return@unifiedGestureDetector true
                            },
                            onSwipe = { direction ->
                                return@unifiedGestureDetector viewModel.performFolderGesture(
                                    context,
                                    folderInfo,
                                    direction
                                )
                            },
                            onDragStart = onDragStart,
                            onDrag = onDrag,
                            onDragEnd = onDragEnd,
                            onDragCancel = onDragCancel,
                            swipeThreshold = with(density) { 48.dp.toPx() },
                            doubleTapTimeout = ViewConfiguration.getDoubleTapTimeout().toLong()
                        )
                    ) {
                        FolderIconLayout(folderInfo)
                    }
                    if (folderInfo.gestureMode == GestureMode.CUSTOM && hasGestures) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val center = Offset(size.width - 12f, 12f)
                            drawCircle(
                                color = Color.Black.copy(alpha = 0.5f),
                            radius = 10f,
                                center = center
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 8f,
                                center = center
                            )
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
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = {
                        viewModel.startRenameFolder(folderInfo)
                        viewModel.hideFolderMenu()
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Rename"
                        )
                    }
                )
                DropdownMenuItem(
                    text = { Text("Gestures") },
                    onClick = {
                        viewModel.showGestureConfig(
                            AppInfo(folderInfo.name, ""),
                            folderInfo,
                            null
                        )
                        viewModel.hideFolderMenu()
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Gesture,
                            contentDescription = "Gestures"
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun FolderIconLayout(folderInfo: FolderInfo) {
    val appCount = folderInfo.apps.size
    val appsToShow = folderInfo.apps.take(9) // Take up to 9 apps for a 3x3 grid

    if (appCount >= 5) { // For 5 or more apps, display a 3x3 grid
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.size(56.dp), // Constrain the grid size to fit in the folder icon
                contentPadding = PaddingValues(0.dp),
                userScrollEnabled = false
            ) {
                lazyGridItems(appsToShow) { app ->
                    app.icon?.let {
                        Image(
                            painter = rememberDrawablePainter(drawable = it),
                            contentDescription = app.label,
                            modifier = Modifier.size(18.dp).padding(1.dp) // Smaller icons for 3x3
                        )
                    }
                }
            }
        }
    } else { // Use a 2x2 grid for fewer than 5 apps
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.size(56.dp),
                contentPadding = PaddingValues(0.dp),
                userScrollEnabled = false
            ) {
                lazyGridItems(folderInfo.apps.take(4)) { app ->
                    app.icon?.let {
                        Image(
                            painter = rememberDrawablePainter(drawable = it),
                            contentDescription = app.label,
                            modifier = Modifier.size(28.dp).padding(1.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ExpandedFolderDialog(
    folderInfo: FolderInfo,
    viewModel: LauncherViewModel,
    visible: Boolean,
    onDismissRequest: () -> Unit,
    onLongPressHandled: () -> Unit,
    onAppDragStart: (AppInfo, FolderInfo, Offset, Size) -> Unit,
    onAppDrag: (PointerInputChange, Offset) -> Unit,
    onAppDragEnd: () -> Unit,
    onAppDragCancel: () -> Unit,
    folderItemBounds: MutableMap<Any, Rect>,
    onFolderContentPositioned: (Rect) -> Unit,
) {
    val folderCardAlpha by animateFloatAsState(if (visible) 1f else 0f, label = "alpha")
    var isEditingFolderName by remember { mutableStateOf(false) }
    var folderNameState by remember(folderInfo.name) { mutableStateOf(TextFieldValue(folderInfo.name)) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    LaunchedEffect(isEditingFolderName) {
        if (isEditingFolderName) {
            focusRequester.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(0.5f)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismissRequest
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black.copy(alpha = if (visible) 0.5f else 0f)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .padding(16.dp)
                        .graphicsLayer { alpha = folderCardAlpha }
                        .onGloballyPositioned {
                            onFolderContentPositioned(
                                Rect(
                                    it.positionInRoot(),
                                    it.size.toSize()
                                )
                            )
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        )
                ) {
                    Column {
                        if (isEditingFolderName) {
                            BasicTextField(
                                value = folderNameState,
                                onValueChange = { folderNameState = it },
                                textStyle = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onSurface),
                                singleLine = true,
                                modifier = Modifier
                                    .padding(16.dp)
                                    .focusRequester(focusRequester)
                                    .onFocusChanged { focusState ->
                                        if (!focusState.isFocused) {
                                            isEditingFolderName = false
                                            viewModel.updateFolderName(context, folderInfo.id, folderNameState.text)
                                        }
                                    }
                                    .onKeyEvent {
                                        if (it.key == Key.Enter) {
                                            isEditingFolderName = false
                                            viewModel.updateFolderName(context, folderInfo.id, folderNameState.text)
                                            focusManager.clearFocus()
                                            true
                                        } else {
                                            false
                                        }
                                    }
                            )
                        } else {
                            Text(
                                text = folderInfo.name,
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier
                                    .padding(16.dp)
                                    .clickable {
                                        isEditingFolderName = true
                                    }
                            )
                        }
                        val appCount = folderInfo.apps.size
                        val gridCells = when {
                            appCount <= 1 -> GridCells.Fixed(1)
                            appCount <= 4 -> GridCells.Fixed(2)
                            else -> GridCells.Fixed(3)
                        }
                        LazyVerticalGrid(
                            columns = gridCells,
                            contentPadding = PaddingValues(16.dp)
                        ) {
                            itemsIndexed(folderInfo.apps, key = { _, app -> "app_${app.packageName}"}) { index, app ->
                                var position by remember { mutableStateOf(Offset.Zero) }
                                var size by remember { mutableStateOf(Size.Zero) }
                                val isBeingDragged = viewModel.draggedAppFromFolder?.first?.packageName == app.packageName

                                Box(
                                    modifier = Modifier
                                        .onGloballyPositioned {
                                            position = it.positionInRoot()
                                            size = it.size.toSize()
                                            folderItemBounds["app_${app.packageName}"] = Rect(position, size)
                                        }
                                        .graphicsLayer {
                                            alpha = if (isBeingDragged) 0f else 1f
                                        }
                                ) {
                                    AppItem(
                                        appInfo = app,
                                        viewModel = viewModel,
                                        folderInfo = folderInfo,
                                        indexInFolder = index,
                                        onLongPressHandled = { onLongPressHandled() },
                                        onDragStart = {
                                            viewModel.hideShortcuts()
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
}

@Composable
fun RenameFolderDialog(
    folderInfo: FolderInfo,
    viewModel: LauncherViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var textState by remember { mutableStateOf(TextFieldValue(folderInfo.name)) }
    val focusRequester = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Folder") },
        text = {
            BasicTextField(
                value = textState,
                onValueChange = { textState = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    viewModel.updateFolderName(context, folderInfo.id, textState.text)
                    onDismiss()
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDismiss()
                }
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AssignedAction(
    action: GestureAction?,
    allApps: List<AppInfo>,
    allShortcuts: Map<String, List<AppShortcutInfo>>,
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
fun GestureIcon(gesture: GestureDirection, modifier: Modifier = Modifier) {
    val icon = when (gesture) {
        GestureDirection.UP -> Icons.Default.ArrowUpward
        GestureDirection.DOWN -> Icons.Default.ArrowDownward
        GestureDirection.LEFT -> Icons.AutoMirrored.Filled.ArrowBack
        GestureDirection.RIGHT -> Icons.AutoMirrored.Filled.ArrowForward
        GestureDirection.UP_LEFT -> Icons.Default.NorthWest
        GestureDirection.UP_RIGHT -> Icons.Default.NorthEast
        GestureDirection.DOWN_LEFT -> Icons.Default.SouthWest
        GestureDirection.DOWN_RIGHT -> Icons.Default.SouthEast
        GestureDirection.DOUBLE_TAP -> Icons.Default.Gesture
        else -> Icons.Default.Gesture // Fallback for single tap or other gestures
    }
    Icon(icon, contentDescription = gesture.description, modifier = modifier)
}

@Composable
fun GestureConfigDialog(
    appInfo: AppInfo,
    folderInfo: FolderInfo? = null,
    indexInFolder: Int? = null,
    onDismiss: () -> Unit,
    viewModel: LauncherViewModel,
) {
    val context = LocalContext.current
    var showActionChooser by remember { mutableStateOf<GestureDirection?>(null) }
    var showClearAllConfirmation by remember { mutableStateOf(false) }
    val allApps by viewModel.allApps.collectAsState()
    val allShortcuts by viewModel.allShortcuts.collectAsState()
    val gestureConfigs by viewModel.gestureConfigs.collectAsState()
    val hasCustomGestures by remember(gestureConfigs) {
        derivedStateOf {
            viewModel.hasCustomGestures(appInfo, folderInfo, indexInFolder)
        }
    }

    val isFolderGestureConfig = folderInfo != null && indexInFolder == null

    var selectedMode by remember(folderInfo?.gestureMode) {
        mutableStateOf(folderInfo?.gestureMode ?: GestureMode.CUSTOM)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            if (showActionChooser == null) {
                Text("Configure Gestures for ${appInfo.label}")
            } else {
                Text("Choose an action")
            }
        },
        text = {
            if (showActionChooser == null) {
                Column {
                    if (isFolderGestureConfig) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .selectable(
                                        selected = (selectedMode == GestureMode.DEFAULT),
                                        onClick = {
                                            selectedMode = GestureMode.DEFAULT
                                            folderInfo?.let { viewModel.toggleGestureMode(context, it) }
                                        }
                                    )
                                    .padding(horizontal = 8.dp)
                            ) {
                                RadioButton(
                                    selected = (selectedMode == GestureMode.DEFAULT),
                                    onClick = {
                                        selectedMode = GestureMode.DEFAULT
                                        folderInfo?.let { viewModel.toggleGestureMode(context, it) }
                                    }
                                )
                                Text("Default")
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .selectable(
                                        selected = (selectedMode == GestureMode.CUSTOM),
                                        onClick = {
                                            selectedMode = GestureMode.CUSTOM
                                            folderInfo?.let { viewModel.toggleGestureMode(context, it) }
                                        }
                                    )
                                    .padding(horizontal = 8.dp)
                            ) {
                                RadioButton(
                                    selected = (selectedMode == GestureMode.CUSTOM),
                                    onClick = {
                                        selectedMode = GestureMode.CUSTOM
                                        folderInfo?.let { viewModel.toggleGestureMode(context, it) }
                                    }
                                )
                                Text("Custom")
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    if (isFolderGestureConfig && selectedMode == GestureMode.DEFAULT) {
                        Text(
                            "Default gestures launch apps based on their position:\n\n" +
                                    "For folders with 4 or fewer apps, gestures correspond to the corners (e.g., Swipe Up-Left for the top-left app).\n\n" +
                                    "For folders with 5 or more apps, gestures map to a 3x3 grid (e.g., Swipe Up for the top-middle app, Double Tap for the center)."
                        )
                    } else {
                        LazyColumn {
                            items(items = GestureDirection.entries.filter { it != GestureDirection.SINGLE_TAP }.toList(), key = { it.name }) { gesture ->
                                val key = if (folderInfo != null && indexInFolder != null) {
                                    "folder:${folderInfo.id}:$indexInFolder:${gesture.name}"
                                } else if (folderInfo != null) {
                                    "folder:${folderInfo.id}:${gesture.name}"
                                } else {
                                    "app:${appInfo.packageName}:${gesture.name}"
                                }
                                val config = gestureConfigs[key]
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    GestureIcon(gesture, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(gesture.description)
                                    Spacer(modifier = Modifier.weight(1f))
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
                    }
                }
            } else {
                ActionChooserContent(
                    viewModel = viewModel,
                    onActionSelected = { action, _ ->
                        val key = if (folderInfo != null && indexInFolder != null) {
                            "folder:${folderInfo.id}:$indexInFolder:${showActionChooser!!.name}"
                        } else if (folderInfo != null) {
                            "folder:${folderInfo.id}:${showActionChooser!!.name}"
                        } else {
                            "app:${appInfo.packageName}:${showActionChooser!!.name}"
                        }
                        viewModel.setGestureConfig(context, key, showActionChooser!!, action)
                        showActionChooser = null
                    }
                )
            }
        },
        confirmButton = {
            if (showActionChooser == null) {
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        },
        dismissButton = {
            if (showActionChooser == null && hasCustomGestures) {
                TextButton(onClick = { showClearAllConfirmation = true }) {
                    Text("Clear All")
                }
            }
        }
    )

    if (showClearAllConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirmation = false },
            title = { Text("Clear All Gestures?") },
            text = { Text("Are you sure you want to remove all custom gestures for ${appInfo.label}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val keyPrefix = if (folderInfo != null && indexInFolder != null) {
                            "folder:${folderInfo.id}:$indexInFolder:"
                        } else if (folderInfo != null) {
                            "folder:${folderInfo.id}:"
                        } else {
                            "app:${appInfo.packageName}:"
                        }
                        viewModel.clearAllGestures(context, keyPrefix)
                        showClearAllConfirmation = false
                    }
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ActionChooserContent(
    viewModel: LauncherViewModel,
    onActionSelected: (GestureAction?, AppInfo?) -> Unit,
) {
    val allApps by viewModel.allApps.collectAsState()
    val sortedApps = remember(allApps) { allApps.sortedBy { it.label } }
    val allShortcuts by viewModel.allShortcuts.collectAsState()
    val expandedState = remember { mutableStateMapOf<String, Boolean>() }

    LazyColumn {
        item {
            TextButton(
                onClick = { onActionSelected(null, null) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("None")
            }
        }
        items(items = sortedApps, key = { it.packageName }) { app ->
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
                            shortcut.icon?.let {
                                Image(
                                    painter = rememberDrawablePainter(drawable = it),
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
}
