package com.arc.launcher

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize

@Composable
fun RemoveDropTarget(
    modifier: Modifier = Modifier,
    visible: Boolean,
    onBoundsChanged: (Rect) -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it }),
        exit = slideOutVertically(targetOffsetY = { -it }),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .background(Color.Red.copy(alpha = 0.5f), shape = MaterialTheme.shapes.medium)
                .onGloballyPositioned { onBoundsChanged(Rect(it.positionInRoot(), it.size.toSize())) }
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = Icons.Default.Delete, contentDescription = "Remove", tint = Color.White)
        }
    }
}
