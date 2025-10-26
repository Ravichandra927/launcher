package com.arc.launcher

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.view.ViewConfiguration
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AppList(
    viewModel: LauncherViewModel = viewModel(),
    appWidgetHost: AppWidgetHost
) {
    BackHandler(enabled = true) {}

    val context = LocalContext.current
    val items by viewModel.items.collectAsState()
    val appToShowGestureConfig by remember { derivedStateOf { viewModel.showGestureConfig } }
    val folderToRename by remember { derivedStateOf { viewModel.folderToRename } }
    var longPressHandled by remember { mutableStateOf(false) }
    val showWidgetPicker by viewModel.showWidgetPicker.collectAsState()
    val draggedWidget by viewModel.draggedWidget.collectAsState()
    val isDraggingWidget by viewModel.isDraggingWidget.collectAsState()

    val appWidgetManager = AppWidgetManager.getInstance(context)

    var pendingWidgetId by remember { mutableStateOf<Int?>(null) }
    var pendingWidgetProvider by remember { mutableStateOf<ComponentName?>(null) }
    var pendingWidgetTargetIndex by remember { mutableStateOf<Int?>(null) }

    val configureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val widgetId = pendingWidgetId
        val provider = pendingWidgetProvider
        val targetIndex = pendingWidgetTargetIndex

        if (result.resultCode == Activity.RESULT_OK && widgetId != null && provider != null && targetIndex != null) {
            viewModel.addWidget(context, widgetId, provider, targetIndex)
        } else if (widgetId != null) {
            appWidgetHost.deleteAppWidgetId(widgetId)
        }

        // Reset pending state
        pendingWidgetId = null
        pendingWidgetProvider = null
        pendingWidgetTargetIndex = null
    }

    val bindLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val widgetId = pendingWidgetId
        val provider = pendingWidgetProvider
        val targetIndex = pendingWidgetTargetIndex

        if (result.resultCode == Activity.RESULT_OK && widgetId != null && provider != null && targetIndex != null) {
            val providerInfo = appWidgetManager.installedProviders.find { it.provider == provider }
            if (providerInfo?.configure != null) {
                // Configuration is needed
                val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                    component = providerInfo.configure
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                }
                configureLauncher.launch(intent)
            } else {
                // No configuration needed, add it directly
                viewModel.addWidget(context, widgetId, provider, targetIndex)
            }
        } else if (widgetId != null) {
            // Binding failed or was cancelled
            appWidgetHost.deleteAppWidgetId(widgetId)
            Toast.makeText(context, "Couldn't add widget", Toast.LENGTH_SHORT).show()
            // Reset pending state
            pendingWidgetId = null
            pendingWidgetProvider = null
            pendingWidgetTargetIndex = null
        }
    }


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
    val isDragging by remember { derivedStateOf { draggedItem != null || isDraggingWidget } }
    var removeDropTargetBounds by remember { mutableStateOf<Rect?>(null) }
    var initialDragOffset by remember { mutableStateOf(Offset.Zero) }


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
            folderVisualsVisible = true
        }

        val currentBounds = if (isInFolder && !isDraggingOutOfFolder) folderItemBounds else itemBounds
        val newDropTarget = currentBounds.entries.find { (key, bounds) ->
            val draggedItemKey = when (val item = draggedItem) {
                is LauncherItem.App -> "app_${item.appInfo.packageName}"
                is LauncherItem.Folder -> "folder_${item.folderInfo.id}"
                is LauncherItem.Widget -> "widget_${item.widgetId}"
                is LauncherItem.WidgetStack -> "widget_stack_${item.widgets.first().widgetId}"
                null -> "" // Explicitly handle null case for draggedItem
            }
            draggedItemKey != key && bounds.contains(currentDragPosition)
        }?.key?.let { key ->
            items.find { launcherItem ->
                when (launcherItem) {
                    is LauncherItem.App -> "app_${launcherItem.appInfo.packageName}"
                    is LauncherItem.Folder -> "folder_${launcherItem.folderInfo.id}"
                    is LauncherItem.Widget -> "widget_${launcherItem.widgetId}"
                    is LauncherItem.WidgetStack -> "widget_stack_${launcherItem.widgets.first().widgetId}"
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

        if (removeDropTargetBounds?.contains(currentDragPosition) == true) {
            viewModel.removeAppFromFolder(context, appToMove, fromFolder)
        } else {
            val isInFolder = folderContentBounds?.contains(currentDragPosition) ?: false && !isDraggingOutOfFolder
            if (isInFolder) {
                val fromIndex = fromFolder.apps.indexOf(appToMove)
                val (toKey, _) = findTargetKeyAndSide(currentDragPosition, folderItemBounds)
                val toIndex = toKey?.let { key ->
                    fromFolder.apps.indexOfFirst { "app_${it.packageName}" == key }
                } ?: -1
                if (toIndex != -1) {
                    viewModel.reorderAppInFolder(context, fromFolder.id, fromIndex, toIndex)
                }
            } else {
                when (val target = dropTarget) {
                    is LauncherItem.App -> viewModel.moveAppFromFolderToApp(context, appToMove, fromFolder, target.appInfo)
                    is LauncherItem.Folder -> {
                        if (fromFolder.id == target.folderInfo.id) {
                            val (toKey, _) = findTargetKeyAndSide(currentDragPosition, folderItemBounds)
                            val toIndex = toKey?.let { key ->
                                fromFolder.apps.indexOfFirst { "app_${it.packageName}" == key }
                            } ?: -1
                            if (toIndex != -1) {
                                viewModel.reorderAppInFolder(context, fromFolder.id, 0, toIndex)
                            }
                        } else {
                            viewModel.moveAppBetweenFolders(context, appToMove, fromFolder, target.folderInfo)
                        }
                    }
                    null -> {
                        val (targetKey, insertAfter) = findTargetKeyAndSide(dragOffset, itemBounds)
                        val targetIndex = targetKey?.let { key -> items.indexOfFirst {
                            val itemKey = when(it) {
                                is LauncherItem.App -> "app_${it.appInfo.packageName}"
                                is LauncherItem.Folder -> "folder_${it.folderInfo.id}"
                                is LauncherItem.Widget -> "widget_${it.widgetId}"
                                is LauncherItem.WidgetStack -> "widget_stack_${it.widgets.first().widgetId}"
                            }
                            itemKey == key
                        }} ?: -1
                        val finalIndex = if (targetIndex != -1) {
                            if (insertAfter) targetIndex + 1 else targetIndex
                        } else {
                            items.size
                        }
                        viewModel.moveAppFromFolderToHome(context, appToMove, fromFolder, finalIndex)
                    }
                    is LauncherItem.Widget -> {
                        // Not supported
                    }
                    is LauncherItem.WidgetStack -> {
                        // Not supported
                    }
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

    val onAppOrFolderDrag = { change: PointerInputChange, dragAmount: Offset ->
        change.consume()
        dragOffset += dragAmount
        val currentDragPosition = dragOffset + Offset(
            draggedItemSize.width / 2,
            draggedItemSize.height / 2
        )

        val currentDraggedItem = draggedItem
        if (currentDraggedItem != null) {
            val (closestKey, insertAfter) = findTargetKeyAndSide(currentDragPosition, itemBounds.filterKeys { key ->
                items.any { item ->
                    val itemKey = when (item) {
                        is LauncherItem.App -> "app_${item.appInfo.packageName}"
                        is LauncherItem.Folder -> "folder_${item.folderInfo.id}"
                        is LauncherItem.Widget -> "widget_${item.widgetId}"
                        is LauncherItem.WidgetStack -> "widget_stack_${item.widgets.first().widgetId}"
                    }
                    itemKey == key && item != currentDraggedItem
                }
            })

            if (closestKey != null) {
                val closestItemIndex = items.indexOfFirst { item ->
                    val itemKey = when (item) {
                        is LauncherItem.App -> "app_${item.appInfo.packageName}"
                        is LauncherItem.Folder -> "folder_${item.folderInfo.id}"
                        is LauncherItem.Widget -> "widget_${item.widgetId}"
                        is LauncherItem.WidgetStack -> "widget_stack_${item.widgets.first().widgetId}"
                    }
                    itemKey == closestKey
                }
                if (closestItemIndex != -1) {
                    val targetIndex = if (insertAfter) closestItemIndex + 1 else closestItemIndex
                    val fromIndex = items.indexOf(currentDraggedItem)
                    if (fromIndex != -1 && fromIndex != targetIndex) {
                        viewModel.moveItem(context, fromIndex, targetIndex)
                    }
                }
            }
        }

        val itemKey = when (val item = draggedItem) {
            is LauncherItem.App -> "app_${item.appInfo.packageName}"
            is LauncherItem.Folder -> "folder_${item.folderInfo.id}"
            is LauncherItem.Widget -> "widget_${item.widgetId}"
            is LauncherItem.WidgetStack -> "widget_stack_${item.widgets.first().widgetId}"
            null -> "" // Explicitly handle null case for draggedItem
        }
        val newDropTarget = itemBounds.entries.find { (key, bounds) ->
            itemKey != key && bounds.contains(currentDragPosition)
        }?.key?.let { key ->
            items.find { launcherItem ->
                when (launcherItem) {
                    is LauncherItem.App -> "app_${launcherItem.appInfo.packageName}"
                    is LauncherItem.Folder -> "folder_${launcherItem.folderInfo.id}"
                    is LauncherItem.Widget -> "widget_${launcherItem.widgetId}"
                    is LauncherItem.WidgetStack -> "widget_stack_${launcherItem.widgets.first().widgetId}"
                } == key
            }
        }
        if (newDropTarget != dropTarget) {
            dropTarget = newDropTarget
        }
    }

    val onAppOrFolderDragEnd = {
        val currentDraggedItem = draggedItem
        val currentDragPosition = dragOffset + Offset(draggedItemSize.width / 2, draggedItemSize.height / 2)

        if (currentDraggedItem != null) {
            if (removeDropTargetBounds?.contains(currentDragPosition) == true) {
                viewModel.removeItem(context, currentDraggedItem)
            } else {
                viewModel.saveItems(context)
            }
        }
        draggedItem = null
        dragOffset = Offset.Zero
        dropTarget = null
    }

    val onDragCancel = {
        viewModel.loadApps(context)
        draggedItem = null
        dragOffset = Offset.Zero
        dropTarget = null
    }

    val onNewWidgetDrag = { change: PointerInputChange, dragAmount: Offset ->
        change.consume()
        dragOffset += dragAmount
    }
    val density = LocalDensity.current
    val onNewWidgetDragEnd = {
        draggedWidget?.let { widgetInfo ->
            val widgetCenter = Offset(
                dragOffset.x + widgetInfo.targetWidth / 2f,
                dragOffset.y + widgetInfo.targetHeight / 2f
            )
            val (targetKey, insertAfter) = findTargetKeyAndSide(widgetCenter, itemBounds)
            val targetIndex = targetKey?.let { key -> items.indexOfFirst {
                val itemKey = when(it) {
                    is LauncherItem.App -> "app_${it.appInfo.packageName}"
                    is LauncherItem.Folder -> "folder_${it.folderInfo.id}"
                    is LauncherItem.Widget -> "widget_${it.widgetId}"
                    is LauncherItem.WidgetStack -> "widget_stack_${it.widgets.first().widgetId}"
                }
                itemKey == key
            }} ?: -1
            var insertionPoint = if(targetIndex != -1) {
                if (insertAfter) targetIndex + 1 else targetIndex
            } else {
                items.size
            }
            if (insertionPoint < 0) insertionPoint = 0
            if (insertionPoint > items.size) insertionPoint = items.size

            val widgetSpan = calculateSpan(with(density) { widgetInfo.targetWidth.toDp().value.roundToInt() })
            val columnCount = 4

            var finalIndex = insertionPoint
            while (true) {
                var totalSpan = 0
                items.take(finalIndex).forEach { item ->
                    val span = when (item) {
                        is LauncherItem.App, is LauncherItem.Folder -> 1
                        is LauncherItem.Widget -> appWidgetManager.getAppWidgetInfo(item.widgetId)?.let {
                            calculateSpan(it.minWidth)
                        } ?: 1
                        is LauncherItem.WidgetStack -> appWidgetManager.getAppWidgetInfo(item.widgets.first().widgetId)?.let {
                            calculateSpan(it.minWidth)
                        } ?: 1
                    }
                    totalSpan += span
                }
                val startColumn = totalSpan % columnCount
                if (startColumn + widgetSpan <= columnCount) {
                    // It fits, we're done
                    break
                }

                finalIndex++
                if (finalIndex > items.size) {
                    finalIndex = items.size
                    break
                }
            }

            val widgetId = appWidgetHost.allocateAppWidgetId()

            pendingWidgetId = widgetId
            pendingWidgetProvider = widgetInfo.provider
            pendingWidgetTargetIndex = finalIndex

            val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, widgetInfo.provider)
            }
            bindLauncher.launch(bindIntent)
        }
        viewModel.endDragWidget()
        viewModel.hideWidgetPicker()
        dragOffset = Offset.Zero
    }

    val onWidgetDrag = { change: PointerInputChange, dragAmount: Offset ->
        change.consume()
        dragOffset += dragAmount
    }

    val onWidgetDragEnd = fun() {
        val currentDraggedItem = draggedItem
        val currentDragPosition = dragOffset + Offset(draggedItemSize.width / 2, draggedItemSize.height / 2)

        if (currentDraggedItem is LauncherItem.Widget) {
            if (removeDropTargetBounds?.contains(currentDragPosition) == true) {
                viewModel.removeItem(context, currentDraggedItem)
            } else {
                val fromIndex = items.indexOf(currentDraggedItem)
                if (fromIndex == -1) {
                    draggedItem = null; dragOffset = Offset.Zero; return
                }

                val widgetCenter = Offset(
                    dragOffset.x + draggedItemSize.width / 2f,
                    dragOffset.y + draggedItemSize.height / 2f
                )

                val tempItems = items.toMutableList().apply { removeAt(fromIndex) }
                val tempItemBounds =
                    itemBounds.filter { it.key != "widget_${currentDraggedItem.widgetId}" }

                val (closestKey, insertAfter) = findTargetKeyAndSide(widgetCenter, tempItemBounds)

                val closestItem = items.find {
                    val key = when(it) {
                        is LauncherItem.App -> "app_${it.appInfo.packageName}"
                        is LauncherItem.Folder -> "folder_${it.folderInfo.id}"
                        is LauncherItem.Widget -> "widget_${it.widgetId}"
                        is LauncherItem.WidgetStack -> "widget_stack_${it.widgets.first().widgetId}"
                    }
                    key == closestKey
                }
                if (closestItem is LauncherItem.Widget) {
                    viewModel.stackWidgets(context, currentDraggedItem, closestItem)
                } else {
                    val insertionPoint = if (closestKey == null) {
                        tempItems.size
                    } else {
                        val listIndexOfClosest = tempItems.indexOfFirst {
                            val key = when (it) {
                                is LauncherItem.App -> "app_${it.appInfo.packageName}"
                                is LauncherItem.Folder -> "folder_${it.folderInfo.id}"
                                is LauncherItem.Widget -> "widget_${it.widgetId}"
                                is LauncherItem.WidgetStack -> "widget_stack_${it.widgets.first().widgetId}"
                            }
                            key == closestKey
                        }

                        if (listIndexOfClosest != -1) {
                            if (insertAfter) listIndexOfClosest + 1 else listIndexOfClosest
                        } else {
                            tempItems.size
                        }
                    }

                    val providerInfo =
                        appWidgetManager.getAppWidgetInfo(currentDraggedItem.widgetId) ?: return
                    val widgetSpan = calculateSpan(providerInfo.minWidth)
                    val columnCount = 4

                    var finalIndex = insertionPoint.coerceIn(0, tempItems.size)
                    while (true) {
                        var totalSpan = 0
                        tempItems.take(finalIndex).forEach { item ->
                            val span = when (item) {
                                is LauncherItem.App, is LauncherItem.Folder -> 1
                                is LauncherItem.Widget -> appWidgetManager.getAppWidgetInfo(item.widgetId)
                                    ?.let {
                                        calculateSpan(it.minWidth)
                                    } ?: 1
                                is LauncherItem.WidgetStack -> appWidgetManager.getAppWidgetInfo(item.widgets.first().widgetId)
                                    ?.let {
                                        calculateSpan(it.minWidth)
                                    } ?: 1
                            }
                            totalSpan += span
                        }
                        val startColumn = totalSpan % columnCount
                        if (startColumn + widgetSpan <= columnCount) {
                            break // It fits
                        }
                        finalIndex++
                        if (finalIndex > tempItems.size) {
                            finalIndex = tempItems.size
                            break
                        }
                    }
                    viewModel.moveItem(context, fromIndex, finalIndex)
                }
            }
        }
        draggedItem = null
        dragOffset = Offset.Zero
        dropTarget = null
    }

    val onNewWidgetDragCancel = {
        viewModel.endDragWidget()
        viewModel.hideWidgetPicker()
        dragOffset = Offset.Zero
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
            columns = GridCells.Fixed(4),
            contentPadding = WindowInsets.systemBars
                .add(WindowInsets(top = 16.dp))
                .asPaddingValues(),
            userScrollEnabled = draggedItem == null && !isDraggingWidget
        ) {
            items(
                items = items,
                key = { item ->
                    when (item) {
                        is LauncherItem.App -> "app_${item.appInfo.packageName}"
                        is LauncherItem.Folder -> "folder_${item.folderInfo.id}"
                        is LauncherItem.Widget -> "widget_${item.widgetId}"
                        is LauncherItem.WidgetStack -> "widget_stack_${item.widgets.first().widgetId}"
                    }
                },
                span = { item ->
                    val span = when (item) {
                        is LauncherItem.App, is LauncherItem.Folder -> 1
                        is LauncherItem.Widget -> {
                            val providerInfo = appWidgetManager.getAppWidgetInfo(item.widgetId)
                            if (providerInfo != null) {
                                calculateSpan(providerInfo.minWidth)
                            } else {
                                1
                            }
                        }
                        is LauncherItem.WidgetStack -> {
                            val providerInfo = appWidgetManager.getAppWidgetInfo(item.widgets.first().widgetId)
                            if (providerInfo != null) {
                                calculateSpan(providerInfo.minWidth)
                            } else {
                                1
                            }
                        }
                    }
                    GridItemSpan(span)
                }
            ) { item ->
                val itemKey = when (item) {
                    is LauncherItem.App -> "app_${item.appInfo.packageName}"
                    is LauncherItem.Folder -> "folder_${item.folderInfo.id}"
                    is LauncherItem.Widget -> "widget_${item.widgetId}"
                    is LauncherItem.WidgetStack -> "widget_stack_${item.widgets.first().widgetId}"
                }
                val isDropTarget = dropTarget == item
                val isBeingDragged = draggedItem == item

                val scale by animateFloatAsState(if (isDropTarget) 1.1f else 1f, label = "scale")

                Box(
                    modifier = Modifier
                        .onGloballyPositioned { coordinates ->
                            itemBounds[itemKey] =
                                Rect(coordinates.positionInRoot(), coordinates.size.toSize())
                        }
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
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
                            onDrag = onAppOrFolderDrag,
                            onDragEnd = onAppOrFolderDragEnd,
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
                            onDrag = onAppOrFolderDrag,
                            onDragEnd = onAppOrFolderDragEnd,
                            onDragCancel = onDragCancel
                        )
                        is LauncherItem.Widget -> {
                            val providerInfo = appWidgetManager.getAppWidgetInfo(item.widgetId)
                            val heightInDp = if (providerInfo != null) {
                                val verticalSpan = calculateVerticalSpan(providerInfo.minHeight)
                                (verticalSpan * 96).dp // Assuming 96.dp per cell
                            } else {
                                96.dp
                            }
                            Box(
                                modifier = Modifier
                                    .height(heightInDp)
                                    .unifiedGestureDetector(
                                        onTap = { /*TODO*/ },
                                        onDoubleTap = { false },
                                        onLongPress = { true },
                                        onSwipe = { false },
                                        onDragStart = {
                                            itemBounds[itemKey]?.let { bounds ->
                                                draggedItem = item
                                                draggedItemSize = bounds.size
                                                dragOffset = bounds.topLeft
                                                initialDragOffset = Offset.Zero
                                            }
                                        },
                                        onDrag = onWidgetDrag,
                                        onDragEnd = onWidgetDragEnd,
                                        onDragCancel = onDragCancel,
                                        swipeThreshold = with(density) { 48.dp.toPx() },
                                        doubleTapTimeout = ViewConfiguration
                                            .getDoubleTapTimeout()
                                            .toLong()
                                    )
                            ) {
                                WidgetItem(
                                    appWidgetHost,
                                    item.widgetId,
                                )
                            }
                        }
                        is LauncherItem.WidgetStack -> {
                            WidgetStack(
                                appWidgetHost = appWidgetHost,
                                widgets = item.widgets,
                                onLongPress = {
                                    // Handle long press on widget stack
                                },
                                onSwipe = {
                                    // Handle swipe on widget stack
                                }
                            )
                        }
                    }
                }
            }
        }

        RemoveDropTarget(
            modifier = Modifier.align(Alignment.TopCenter),
            visible = isDragging,
            onBoundsChanged = { removeDropTargetBounds = it }
        )

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
            Box(
                modifier = Modifier
                    .zIndex(1f)
                    .size(with(LocalDensity.current) {
                        DpSize(
                            draggedItemSize.width.toDp(),
                            draggedItemSize.height.toDp()
                        )
                    })
                    .graphicsLayer {
                        translationX = dragOffset.x - initialDragOffset.x
                        translationY = dragOffset.y - initialDragOffset.y
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
                    is LauncherItem.Widget -> {
                        WidgetItem(appWidgetHost, item.widgetId)
                    }
                    is LauncherItem.WidgetStack -> {
                        // Placeholder for WidgetStack
                    }
                    null -> {
                        // Do nothing
                    }
                }
            }
        }

        if (isDraggingWidget) {
            draggedWidget?.let {
                WidgetGhost(
                    widgetInfo = it,
                    modifier = Modifier
                        .zIndex(1f)
                        .graphicsLayer {
                            translationX = dragOffset.x
                            translationY = dragOffset.y
                        }
                )
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
                    scope.launch {
                        sheetState.hide()
                        viewModel.showWidgetPicker(context)
                    }.invokeOnCompletion {
                        if (!sheetState.isVisible) {
                            showBottomSheet = false
                        }
                    }
                }) {
                    Text("Widgets")
                }
            }
        }

        if (showWidgetPicker || isDraggingWidget) {
            WidgetPickerDialog(
                viewModel = viewModel,
                onDismissRequest = { viewModel.hideWidgetPicker() },
                onDragStart = {
                    dragOffset = it
                },
                onDrag = onNewWidgetDrag,
                onDragEnd = onNewWidgetDragEnd,
                onDragCancel = onNewWidgetDragCancel,
                isVisible = !isDraggingWidget
            )
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
