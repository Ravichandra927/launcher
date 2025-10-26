package com.arc.launcher

import android.view.ViewConfiguration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.zIndex
import com.google.accompanist.drawablepainter.rememberDrawablePainter

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
                                viewModel.performFolderGesture(
                                    context,
                                    folderInfo,
                                    GestureDirection.DOUBLE_TAP
                                )
                            },
                            onLongPress = {
                                onLongPressHandled()
                                viewModel.showFolderMenu(folderInfo)
                                true
                            },
                            onSwipe = { direction ->
                                viewModel.performFolderGesture(
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
                items(appsToShow) { app ->
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
                items(folderInfo.apps.take(4)) { app ->
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
    var folderNameState by remember(folderInfo.name) {
        mutableStateOf(TextFieldValue(folderInfo.name))
    }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    var isFocused by remember { mutableStateOf(false) }

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
                ) {
                    Column {
                        val interactionSource = remember { MutableInteractionSource() }
                        BasicTextField(
                            value = folderNameState,
                            onValueChange = { folderNameState = it },
                            textStyle = MaterialTheme.typography.headlineSmall.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            singleLine = true,
                            cursorBrush = if (isFocused) SolidColor(MaterialTheme.colorScheme.primary) else SolidColor(
                                Color.Transparent
                            ),
                            interactionSource = interactionSource,
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .onFocusChanged {
                                    isFocused = it.isFocused
                                    if (!it.isFocused) {
                                        viewModel.updateFolderName(
                                            context,
                                            folderInfo.id,
                                            folderNameState.text
                                        )
                                    } else {
                                        // When focused, select all text
                                        folderNameState = folderNameState.copy(
                                            selection = TextRange(0, folderNameState.text.length)
                                        )
                                    }
                                }
                                .onKeyEvent {
                                    if (it.key == Key.Enter) {
                                        viewModel.updateFolderName(
                                            context,
                                            folderInfo.id,
                                            folderNameState.text
                                        )
                                        focusManager.clearFocus()
                                        true
                                    } else {
                                        false
                                    }
                                }
                        )

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
                                            folderItemBounds["app_${app.packageName}"] =
                                                Rect(position, size)
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
