package com.panyou.missnet.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Container-level tokens for consistent spacing, padding, and layouts.
 * Inspired by GDrive/Material Design 3 system approach.
 * 
 * Usage:
 * - ContainerTokens.Card - for card-like surfaces
 * - ContainerTokens.ListItem - for list item padding
 * - ContainerTokens.Section - for section spacing
 */
object ContainerTokens {
    // Card container tokens
    val CardPadding = 16.dp
    val CardElevation = 2.dp
    val CardPressedElevation = 8.dp
    
    // List item tokens  
    val ListItemPadding = 16.dp
    val ListItemVerticalPadding = 12.dp
    val ListItemThumbnailSize = 80.dp
    val ListRowHorizontalPadding = 16.dp
    val ListRowVerticalPadding = 12.dp
    val ListLeadingIconSize = 22.dp
    val ListLeadingContainerSize = 40.dp
    val ListTrailingIconSize = 20.dp
    val ListDividerInsetStart = 56.dp
    val ListDividerInsetEnd = 16.dp
    
    // Section tokens
    val SectionPadding = 24.dp
    val SectionHeaderPadding = 16.dp
    val SectionVerticalSpacing = 16.dp
    val SectionGridSpacing = 16.dp
    
    // Grid tokens
    val GridItemSpacing = 16.dp
    val GridColumns = 2
    
    // Content padding for full-screen containers
    val ScreenContentPadding = 16.dp
    val ScreenBottomPadding = 100.dp

    // Compact container insets (used by Home/Library/Actress core cards)
    val ScreenCompactHorizontalPadding = 12.dp
    val ScreenCompactVerticalPadding = 8.dp
}

/**
 * Badge tokens for consistent status indicators across the app.
 */
object BadgeTokens {
    // Status badge sizes
    val StatusBadgeHeight = 24.dp
    val StatusBadgePaddingHorizontal = 8.dp
    val StatusBadgePaddingVertical = 4.dp
    val StatusBadgeFontSize = 12.sp  // MaterialTheme.typography.labelMedium
    
    // Small badge (for inline tags)
    val SmallBadgeHeight = 20.dp
    val SmallBadgePaddingHorizontal = 6.dp
    val SmallBadgePaddingVertical = 2.dp
    val SmallBadgeFontSize = 10.sp  // MaterialTheme.typography.labelSmall
    
    // Overlay badge (for favorite, duration overlay)
    val OverlayBadgeSize = 28.dp
    val OverlayBadgeIconSize = 16.dp
    val OverlayBadgePadding = 8.dp
    
    // Corner radius - use shapes.small (8dp) for badges
    val CornerRadius = 8.dp
}

/**
 * Action-level tokens for button/chip density consistency.
 */
object ActionTokens {
    val RowSpacing = 8.dp
    val ButtonContentGap = 6.dp
    val ButtonIconSize = 18.dp
    val ButtonContentPaddingHorizontal = 14.dp
    val ButtonContentPaddingVertical = 10.dp
    val ChipMinHeight = 36.dp
    val ChipIconSize = 18.dp
}
