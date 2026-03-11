package com.panyou.missnet.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun VideoCard(
    videoId: String,
    title: String,
    coverUrl: String,
    duration: String? = null,
    progress: Float? = null,
    showFavoriteBadge: Boolean = false,
    onClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val elevation by androidx.compose.animation.core.animateDpAsState(
        targetValue = 2.dp,
        label = "elevation"
    )

    Card(
        modifier = Modifier
            .aspectRatio(16f / 9f)
            .clip(MaterialTheme.shapes.large)
            .clickable { onClick() },
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(
            defaultElevation = elevation,
            pressedElevation = 8.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            with(sharedTransitionScope) {
                val imageModifier = if (this != null && animatedVisibilityScope != null) {
                    Modifier.sharedElement(
                        state = rememberSharedContentState(key = "image-$videoId"),
                        animatedVisibilityScope = animatedVisibilityScope
                    )
                } else {
                    Modifier
                }

                Image(
                    painter = rememberAsyncImagePainter(coverUrl),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(imageModifier)
                )
            }
            
            // Gradient overlay for text readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                            startY = 50f
                        )
                    )
            )

            // Title overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 18.sp
                )
            }
            
            // Favorite badge - using unified OverlayBadge
            if (showFavoriteBadge) {
                OverlayBadge(
                    icon = Icons.Default.Favorite,
                    iconTint = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                )
            }
            
            // Duration badge - using unified DurationBadge
            if (duration != null && progress == null) {
                DurationBadge(
                    text = duration,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                )
            }
            
            // Progress indicator - using unified ProgressBadge
            if (progress != null) {
                ProgressBadge(
                    progress = progress,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                )
            }
        }
    }
}
