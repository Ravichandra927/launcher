package com.arc.launcher

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.pm.PackageManager
import android.util.TypedValue
import android.widget.FrameLayout
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.graphics.drawable.toBitmap
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlin.math.roundToInt
import androidx.compose.foundation.lazy.items as lazyColumnItems

@Composable
fun WidgetGhost(widgetInfo: WidgetInfo, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val pm = context.packageManager
    val resources = try {
        pm.getResourcesForApplication(widgetInfo.provider.packageName)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
    val drawable = resources?.let {
        try {
            it.getDrawable(widgetInfo.previewImage, null)
        } catch (_: Exception) {
            null
        }
    }

    Box(modifier = modifier.size(
        with(LocalDensity.current) { widgetInfo.targetWidth.toDp() },
        with(LocalDensity.current) { widgetInfo.targetHeight.toDp() }
    )) {
        if (drawable != null) {
            Image(
                bitmap = drawable.toBitmap().asImageBitmap(),
                contentDescription = widgetInfo.label,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Image(
                painter = rememberDrawablePainter(drawable = widgetInfo.icon),
                contentDescription = widgetInfo.label,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun WidgetPickerDialog(
    viewModel: LauncherViewModel,
    onDismissRequest: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (PointerInputChange, Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    isVisible: Boolean
) {
    val widgetsByApp by viewModel.allApps.collectAsState()
    val context = LocalContext.current
    val pm = context.packageManager
    val expandedState = remember { mutableStateMapOf<String, Boolean>() }
    val density = LocalDensity.current
    val alpha by animateFloatAsState(if (isVisible) 1f else 0f, label = "alpha")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(1f)
            .graphicsLayer(alpha = alpha)
            .background(Color.Black.copy(alpha = 0.5f * alpha))
            .clickable(
                enabled = isVisible,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismissRequest
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* Consume clicks */ }
                )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Widgets", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(16.dp))
                LazyColumn {
                    lazyColumnItems(widgetsByApp) { appInfo ->
                        val isExpanded = expandedState[appInfo.packageName] ?: false
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        expandedState[appInfo.packageName] =
                                            !isExpanded
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Image(
                                    painter = rememberDrawablePainter(drawable = appInfo.icon),
                                    contentDescription = appInfo.label,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = appInfo.label,
                                    style = MaterialTheme.typography.headlineSmall,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (isExpanded) "Collapse" else "Expand"
                                )
                            }
                            if (isExpanded) {
                                Column(
                                    modifier = Modifier.padding(
                                        start = 16.dp,
                                        end = 16.dp,
                                        bottom = 8.dp
                                    )
                                ) {
                                    val appWidgetManager = AppWidgetManager.getInstance(context)
                                    val providers = appWidgetManager.getInstalledProvidersForPackage(appInfo.packageName, null)
                                    providers.forEach { providerInfo ->
                                        val widgetInfo = WidgetInfo(
                                            provider = providerInfo.provider,
                                            label = providerInfo.label,
                                            targetWidth = providerInfo.minWidth,
                                            targetHeight = providerInfo.minHeight,
                                            icon = providerInfo.loadIcon(context, density.density.toInt()),
                                            previewImage = providerInfo.previewImage
                                        )
                                        val resources = try {
                                            pm.getResourcesForApplication(widgetInfo.provider.packageName)
                                        } catch (_: PackageManager.NameNotFoundException) {
                                            null
                                        }
                                        val drawable = resources?.let {
                                            try {
                                                it.getDrawable(widgetInfo.previewImage, null)
                                            } catch (_: Exception) {
                                                null
                                            }
                                        }
                                        var widgetPosition by remember { mutableStateOf(Offset.Zero) }

                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 8.dp)
                                                .onGloballyPositioned {
                                                    widgetPosition = it.positionInRoot()
                                                }
                                                .pointerInput(widgetInfo) {
                                                    detectDragGestures(
                                                        onDragStart = {
                                                            viewModel.startDragWidget(widgetInfo)
                                                            onDragStart(widgetPosition)
                                                        },
                                                        onDrag = onDrag,
                                                        onDragEnd = onDragEnd,
                                                        onDragCancel = onDragCancel
                                                    )
                                                },
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            if (drawable != null) {
                                                Image(
                                                    bitmap = drawable
                                                        .toBitmap()
                                                        .asImageBitmap(),
                                                    contentDescription = widgetInfo.label,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                )
                                            } else {
                                                Image(
                                                    painter = rememberDrawablePainter(drawable = widgetInfo.icon),
                                                    contentDescription = widgetInfo.label,
                                                    modifier = Modifier.size(48.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(text = widgetInfo.label)
                                                val hSpan = calculateSpan(with(density) { widgetInfo.targetWidth.toDp().value.roundToInt() })
                                                val vSpan = calculateVerticalSpan(with(density) { widgetInfo.targetHeight.toDp().value.roundToInt() })
                                                Text(text = "${hSpan}x${vSpan}")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
fun WidgetItem(appWidgetHost: AppWidgetHost, widgetId: Int) {
    val context = LocalContext.current
    val appWidgetManager = AppWidgetManager.getInstance(context)
    val providerInfo = appWidgetManager.getAppWidgetInfo(widgetId)
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val view = appWidgetHost.createView(ctx, widgetId, providerInfo)
            view.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )

            // This is a workaround to ensure the widget's text is scaled correctly.
            val displayMetrics = ctx.resources.displayMetrics
            val fontMetrics = view.context.resources.displayMetrics
            val ratio = displayMetrics.density / fontMetrics.density
            val textViews = view.javaClass.declaredFields
                .filter { it.type == android.widget.TextView::class.java }
            textViews.forEach {
                it.isAccessible = true
                val textView = it.get(view) as android.widget.TextView
                textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, textView.textSize * ratio)
            }
            view
        }
    )
}

@Composable
fun WidgetStack(
    appWidgetHost: AppWidgetHost,
    widgets: List<LauncherItem.Widget>,
    onLongPress: () -> Unit,
    onSwipe: (direction: GestureDirection) -> Unit
) {
    var currentIndex by remember { mutableStateOf(0) }
    val currentWidget = widgets[currentIndex]

    Box(modifier = Modifier.fillMaxSize()) {
        WidgetItem(appWidgetHost, currentWidget.widgetId)

        WidgetStackIndicator(
            modifier = Modifier.align(Alignment.BottomCenter),
            count = widgets.size,
            currentIndex = currentIndex,
            isVertical = false
        )

        WidgetStackIndicator(
            modifier = Modifier.align(Alignment.CenterEnd),
            count = widgets.size,
            currentIndex = currentIndex,
            isVertical = true
        )
    }
}

@Composable
fun WidgetStackIndicator(
    modifier: Modifier = Modifier,
    count: Int,
    currentIndex: Int,
    isVertical: Boolean
) {
    val indicatorSize = 8.dp
    val spacing = 4.dp
    val color = Color.White.copy(alpha = 0.5f)
    val selectedColor = Color.White

    if (isVertical) {
        Column(
            modifier = modifier.padding(end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(spacing)
        ) {
            for (i in 0 until count) {
                Box(
                    modifier = Modifier
                        .size(indicatorSize)
                        .background(
                            color = if (i == currentIndex) selectedColor else color,
                            shape = MaterialTheme.shapes.small
                        )
                )
            }
        }
    } else {
        Row(
            modifier = modifier.padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(spacing)
        ) {
            for (i in 0 until count) {
                Box(
                    modifier = Modifier
                        .size(indicatorSize)
                        .background(
                            color = if (i == currentIndex) selectedColor else color,
                            shape = MaterialTheme.shapes.small
                        )
                )
            }
        }
    }
}
