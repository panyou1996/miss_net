package com.panyou.missnet.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun mediaScrim(alpha: Float): Color = MaterialTheme.colorScheme.scrim.copy(alpha = alpha)

@Composable
fun mediaBottomGradient(
    topAlpha: Float = 0f,
    middleAlpha: Float = 0f,
    bottomAlpha: Float
): Brush {
    val scrim = MaterialTheme.colorScheme.scrim
    return Brush.verticalGradient(
        colors = listOf(
            scrim.copy(alpha = topAlpha),
            scrim.copy(alpha = middleAlpha),
            scrim.copy(alpha = bottomAlpha)
        )
    )
}

fun videoSharedTransitionKey(videoId: String): String = "image-$videoId"
