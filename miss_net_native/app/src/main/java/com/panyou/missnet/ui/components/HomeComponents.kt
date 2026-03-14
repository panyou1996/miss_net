package com.panyou.missnet.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import com.panyou.missnet.data.model.Video
import com.panyou.missnet.ui.theme.ContainerTokens
import com.panyou.missnet.ui.theme.MotionTokens
import com.panyou.missnet.ui.util.bouncyClick

/**
 * Section Header with Title and Arrow
 */
@Composable
fun SectionHeader(
    title: String,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = ContainerTokens.SectionPadding, vertical = ContainerTokens.SectionHeaderPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Vertical Video Card - For Horizontal Lists (Figma Style)
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun VerticalVideoCard(
    video: Video,
    onClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .bouncyClick(onClick = onClick)
            .padding(end = ContainerTokens.GridItemSpacing)
    ) {
        // Image Container - using ThumbnailShape
        with(sharedTransitionScope) {
            val imageModifier = if (this != null && animatedVisibilityScope != null) {
                Modifier.sharedElement(
                    state = rememberSharedContentState(key = "image-${video.id}"),
                    animatedVisibilityScope = animatedVisibilityScope
                )
            } else {
                Modifier
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.large)  // Using shapes.large for consistency
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .then(imageModifier)
            ) {
                MissNetCoverImage(
                    coverUrl = video.coverUrl,
                    contentDescription = video.title,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Text Info
        Text(
            text = video.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = video.actors.firstOrNull() ?: "未知演员",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

/**
 * M3 Hero Carousel Item - Adapted for HorizontalMultiBrowseCarousel
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun HeroCarouselItem(
    video: Video,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    Card(
        modifier = modifier
            .fillMaxSize()
            .bouncyClick(onClick = onClick),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        with(sharedTransitionScope) {
            val imageModifier = if (this != null && animatedVisibilityScope != null) {
                Modifier.sharedElement(
                    state = rememberSharedContentState(key = "image-${video.id}"),
                    animatedVisibilityScope = animatedVisibilityScope
                )
            } else {
                Modifier
            }

            Box(modifier = Modifier.fillMaxSize()) {
                MissNetCoverImage(
                    coverUrl = video.coverUrl,
                    contentDescription = video.title,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(imageModifier)
                )
                
                // Gradient Overlay for text readability
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent, 
                                    Color.Black.copy(alpha = 0.1f), 
                                    Color.Black.copy(alpha = 0.8f)
                                ),
                                startY = 100f
                            )
                        )
                )
                
                // Content
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    // Actor tag - using unified SmallBadge
                    if (video.actors.isNotEmpty()) {
                        SmallBadge(
                            text = video.actors.first(),
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    
                    Text(
                        text = video.title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        lineHeight = 28.sp,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Refined Video Feed Card - High Density, Immersive
 */
@Composable
fun VideoFeedCard(
    video: Video,
    onClick: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    // Main Content - Using ContainerTokens for padding
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .padding(horizontal = ContainerTokens.ScreenContentPadding, vertical = 8.dp)
            .bouncyClick(onClick = onClick)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ContainerTokens.ListItemPadding, vertical = ContainerTokens.ListItemVerticalPadding),
                verticalAlignment = Alignment.Top
            ) {
                // Left: Thumbnail - using shapes.medium
                Box(
                    modifier = Modifier
                        .width(130.dp)
                        .aspectRatio(16f/9f)
                        .clip(MaterialTheme.shapes.medium)
                ) {
                    MissNetCoverImage(
                        coverUrl = video.coverUrl,
                        contentDescription = video.title,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Duration Badge - using unified component
                    DurationBadge(
                        text = video.duration ?: "HD",
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Right: Info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 20.sp,
                        fontSize = 15.sp
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (video.actors.isNotEmpty()) {
                            Text(
                                text = video.actors.first(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = " • ",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = video.createdAt?.take(10) ?: "Recent",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Expand Hint
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                         Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "More",
                            modifier = Modifier
                                .size(20.dp)
                                .align(Alignment.CenterEnd)
                                .clickable { expanded = !expanded },
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            // Expanded Details
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = MotionTokens.standard()) + fadeIn(animationSpec = MotionTokens.standard()),
                exit = shrinkVertically(animationSpec = MotionTokens.standard()) + fadeOut(animationSpec = MotionTokens.standard())
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ContainerTokens.ListItemPadding, vertical = 8.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.medium)
                        .padding(12.dp)
                    ) {
                        Text(
                            text = "来源: ${video.sourceUrl.ifBlank { "未知" }}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                
                HorizontalDivider(
                    modifier = Modifier.padding(start = ContainerTokens.ListItemPadding), 
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}
