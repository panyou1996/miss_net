package com.panyou.missnet.nativeapp.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    data object Home : TopLevelDestination("home", "Home", Icons.Rounded.Home)
    data object Explore : TopLevelDestination("explore", "Explore", Icons.Rounded.Explore)
    data object Favorites : TopLevelDestination("favorites", "Favorites", Icons.Rounded.Favorite)
    data object Downloads : TopLevelDestination("downloads", "Downloads", Icons.Rounded.Download)
    data object Settings : TopLevelDestination("settings", "Settings", Icons.Rounded.Settings)
}

object AppRoute {
    const val Search = "search"
    const val Feed = "feed?title={title}&category={category}&actor={actor}"
    const val FeedBase = "feed"
    const val Player = "player"
    const val VideoArg = "video_arg"
}

val topLevelDestinations = listOf(
    TopLevelDestination.Home,
    TopLevelDestination.Explore,
    TopLevelDestination.Favorites,
    TopLevelDestination.Downloads,
    TopLevelDestination.Settings,
)
