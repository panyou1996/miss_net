package com.panyou.missnet.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Unified shape tokens for a calm, system-like visual language.
 * 
 * Design principles:
 * - extraSmall (4dp): Tags, badges, small overlays
 * - small (8dp): Buttons, chips, small cards, list items
 * - medium (16dp): Standard cards, dialogs
 * - large (20dp): Hero cards, featured content
 * - extraLarge (28dp): Full-screen containers, modals
 * 
 * Additional shapes:
 * - SquircleShape: Used for thumbnail containers (same as large)
 * - PlayfulHeaderShape: Bottom-rounded header for expressive sections
 * - ThumbnailShape: Standard for video thumbnails (medium)
 */
val Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

// Alias shapes for semantic usage
val SquircleShape = RoundedCornerShape(20.dp)  // Same as large - for card-like containers
val PlayfulHeaderShape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
val ThumbnailShape = RoundedCornerShape(16.dp)  // Same as medium - for video thumbnails

// Legacy compatibility - mark as deprecated
@Deprecated("Use MaterialTheme.shapes.small instead", ReplaceWith("MaterialTheme.shapes.small"))
val SmallCornerShape = RoundedCornerShape(8.dp)

@Deprecated("Use MaterialTheme.shapes.medium instead", ReplaceWith("MaterialTheme.shapes.medium"))
val MediumCornerShape = RoundedCornerShape(16.dp)

@Deprecated("Use MaterialTheme.shapes.large instead", ReplaceWith("MaterialTheme.shapes.large"))
val LargeCornerShape = RoundedCornerShape(20.dp)
