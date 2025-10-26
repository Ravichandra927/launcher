package com.arc.launcher

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.SouthEast
import androidx.compose.material.icons.filled.SouthWest
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import androidx.compose.foundation.lazy.items as lazyColumnItems

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
                                            viewModel.toggleGestureMode(context, folderInfo)
                                        }
                                    )
                                    .padding(horizontal = 8.dp)
                            ) {
                                RadioButton(
                                    selected = (selectedMode == GestureMode.DEFAULT),
                                    onClick = {
                                        selectedMode = GestureMode.DEFAULT
                                        viewModel.toggleGestureMode(context, folderInfo)
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
                                            viewModel.toggleGestureMode(context, folderInfo)
                                        }
                                    )
                                    .padding(horizontal = 8.dp)
                            ) {
                                RadioButton(
                                    selected = (selectedMode == GestureMode.CUSTOM),
                                    onClick = {
                                        selectedMode = GestureMode.CUSTOM
                                        viewModel.toggleGestureMode(context, folderInfo)
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
                            lazyColumnItems(items = GestureDirection.entries.filter { it != GestureDirection.SINGLE_TAP }.toList(), key = { it.name }) { gesture ->
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
        lazyColumnItems(items = sortedApps, key = { it.packageName }) { app ->
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
