package com.panyou.missnet.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panyou.missnet.ui.theme.BadgeTokens

/**
 * Unified Status Badge for displaying download/player states.
 * 
 * Usage:
 * - TaskStateBadge for download states
 * - ExportStateBadge for export states  
 * - Custom status badges
 */
@Composable
fun StatusBadge(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.heightIn(min = BadgeTokens.StatusBadgeHeight),
        shape = MaterialTheme.shapes.small,
        color = containerColor
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(
                horizontal = BadgeTokens.StatusBadgePaddingHorizontal,
                vertical = BadgeTokens.StatusBadgePaddingVertical
            ),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = contentColor
        )
    }
}

/**
 * Small inline tag/badge for actor names, categories, etc.
 */
@Composable
fun SmallBadge(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.heightIn(min = BadgeTokens.SmallBadgeHeight),
        shape = MaterialTheme.shapes.small,
        color = containerColor
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(
                horizontal = BadgeTokens.SmallBadgePaddingHorizontal,
                vertical = BadgeTokens.SmallBadgePaddingVertical
            ),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
    }
}

/**
 * Overlay badge for favorite icons, play icons, etc. on top of media.
 */
@Composable
fun OverlayBadge(
    icon: ImageVector,
    iconTint: Color = Color.White,
    backgroundColor: Color = Color.Black.copy(alpha = 0.4f),
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(BadgeTokens.OverlayBadgeSize)
            .background(backgroundColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(BadgeTokens.OverlayBadgeIconSize)
        )
    }
}

/**
 * Duration badge for video thumbnails.
 */
@Composable
fun DurationBadge(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.7f), MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

/**
 * Progress badge for showing download progress or watch progress.
 */
@Composable
fun ProgressBadge(
    progress: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(3.dp)
            .background(Color.Black.copy(alpha = 0.4f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}
