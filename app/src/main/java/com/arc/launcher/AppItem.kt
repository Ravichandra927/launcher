package com.arc.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.view.ViewConfiguration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.accompanist.drawablepainter.rememberDrawablePainter

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
                false
            },
            onLongPress = {
                onLongPressHandled()
                hasShortcutPermission = viewModel.showShortcuts(context, appInfo)
				true
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
                false
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
