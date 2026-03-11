package com.panyou.missnet.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import com.panyou.missnet.ui.theme.MotionTokens
import kotlinx.coroutines.delay

@Composable
fun StaggeredEntrance(
    index: Int,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        delay(index * 50L) // 50ms stagger
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { 100 },
            animationSpec = MotionTokens.enter()
        ) + fadeIn(animationSpec = MotionTokens.enter()),
        exit = fadeOut()
    ) {
        content()
    }
}
