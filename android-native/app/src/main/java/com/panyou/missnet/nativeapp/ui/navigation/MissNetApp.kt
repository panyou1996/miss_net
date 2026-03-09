package com.panyou.missnet.nativeapp.ui.navigation

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.panyou.missnet.nativeapp.core.AppGraph
import com.panyou.missnet.nativeapp.core.model.Video
import com.panyou.missnet.nativeapp.ui.screens.DownloadsScreen
import com.panyou.missnet.nativeapp.ui.screens.ExploreScreen
import com.panyou.missnet.nativeapp.ui.screens.FavoritesScreen
import com.panyou.missnet.nativeapp.ui.screens.FeedScreen
import com.panyou.missnet.nativeapp.ui.screens.HomeScreen
import com.panyou.missnet.nativeapp.ui.screens.PlayerScreen
import com.panyou.missnet.nativeapp.ui.screens.SearchScreen
import com.panyou.missnet.nativeapp.ui.screens.SettingsScreen
import com.panyou.missnet.nativeapp.ui.theme.ScreenGradient
import com.panyou.missnet.nativeapp.ui.theme.SurfaceRaised
import com.panyou.missnet.nativeapp.ui.viewmodel.DownloadsViewModel
import com.panyou.missnet.nativeapp.ui.viewmodel.ExploreViewModel
import com.panyou.missnet.nativeapp.ui.viewmodel.FavoritesViewModel
import com.panyou.missnet.nativeapp.ui.viewmodel.FeedViewModel
import com.panyou.missnet.nativeapp.ui.viewmodel.HomeViewModel
import com.panyou.missnet.nativeapp.ui.viewmodel.PlayerViewModel
import com.panyou.missnet.nativeapp.ui.viewmodel.SearchViewModel
import com.panyou.missnet.nativeapp.ui.viewmodel.SettingsViewModel
import com.panyou.missnet.nativeapp.ui.viewmodel.appViewModelFactory
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Composable
fun MissNetApp(
    appGraph: AppGraph,
    activity: Activity,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = topLevelDestinations.any { destination ->
        currentRoute?.startsWith(destination.route) == true
    }

    fun openPlayer(video: Video) {
        navController.currentBackStackEntry?.savedStateHandle?.set(AppRoute.VideoArg, video)
        navController.navigate(AppRoute.Player)
    }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            ) {
                NavigationBar(containerColor = SurfaceRaised.copy(alpha = 0.94f)) {
                    topLevelDestinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute?.startsWith(destination.route) == true,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { androidx.compose.material3.Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ScreenGradient)
                .padding(innerPadding),
        ) {
            NavHost(
                navController = navController,
                startDestination = TopLevelDestination.Home.route,
            ) {
                composable(TopLevelDestination.Home.route) {
                    val viewModel: HomeViewModel = viewModel(
                        factory = appViewModelFactory { HomeViewModel(appGraph.videoRepository) },
                    )
                    HomeScreen(
                        viewModel = viewModel,
                        onSearch = { navController.navigate(AppRoute.Search) },
                        onOpenVideo = ::openPlayer,
                        onOpenFeed = { title, category, actor ->
                            navController.navigate(feedRoute(title, category, actor))
                        },
                    )
                }
                composable(TopLevelDestination.Explore.route) {
                    val viewModel: ExploreViewModel = viewModel(
                        factory = appViewModelFactory { ExploreViewModel(appGraph.videoRepository) },
                    )
                    ExploreScreen(
                        viewModel = viewModel,
                        onSearch = { navController.navigate(AppRoute.Search) },
                        onOpenFeed = { title, category, actor ->
                            navController.navigate(feedRoute(title, category, actor))
                        },
                    )
                }
                composable(TopLevelDestination.Favorites.route) {
                    val viewModel: FavoritesViewModel = viewModel(
                        factory = appViewModelFactory { FavoritesViewModel(appGraph.videoRepository) },
                    )
                    FavoritesScreen(viewModel = viewModel, onOpenVideo = ::openPlayer)
                }
                composable(TopLevelDestination.Downloads.route) {
                    val viewModel: DownloadsViewModel = viewModel(
                        factory = appViewModelFactory { DownloadsViewModel(appGraph.downloadCoordinator) },
                    )
                    DownloadsScreen(
                        viewModel = viewModel,
                        onOpenVideo = ::openPlayer,
                    )
                }
                composable(TopLevelDestination.Settings.route) {
                    val viewModel: SettingsViewModel = viewModel(
                        factory = appViewModelFactory {
                            SettingsViewModel(
                                appGraph.settingsRepository,
                                appGraph.videoRepository,
                                appGraph.downloadCoordinator,
                            )
                        },
                    )
                    SettingsScreen(viewModel = viewModel)
                }
                composable(AppRoute.Search) {
                    val viewModel: SearchViewModel = viewModel(
                        factory = appViewModelFactory { SearchViewModel(appGraph.videoRepository) },
                    )
                    SearchScreen(
                        viewModel = viewModel,
                        onBack = navController::popBackStack,
                        onOpenVideo = ::openPlayer,
                    )
                }
                composable(
                    route = AppRoute.Feed,
                    arguments = listOf(
                        navArgument("title") { type = NavType.StringType; defaultValue = "" },
                        navArgument("category") { type = NavType.StringType; defaultValue = "" },
                        navArgument("actor") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) { entry ->
                    val viewModel: FeedViewModel = viewModel(
                        factory = appViewModelFactory { FeedViewModel(appGraph.videoRepository) },
                    )
                    val title = entry.arguments?.getString("title").orEmpty()
                    val category = entry.arguments?.getString("category").orEmpty()
                    val actor = entry.arguments?.getString("actor").orEmpty()
                    FeedScreen(
                        viewModel = viewModel,
                        title = title,
                        category = category,
                        actor = actor,
                        onBack = navController::popBackStack,
                        onOpenVideo = ::openPlayer,
                    )
                }
                composable(AppRoute.Player) {
                    val video = navController.previousBackStackEntry?.savedStateHandle?.get<Video>(AppRoute.VideoArg)
                    if (video == null) {
                        navController.popBackStack()
                    } else {
                        val viewModel: PlayerViewModel = viewModel(
                            factory = appViewModelFactory {
                                PlayerViewModel(
                                    repository = appGraph.videoRepository,
                                    settingsRepository = appGraph.settingsRepository,
                                    streamResolver = appGraph.streamResolver,
                                    downloadCoordinator = appGraph.downloadCoordinator,
                                )
                            },
                        )
                        PlayerScreen(
                            activity = activity,
                            viewModel = viewModel,
                            video = video,
                            downloadCoordinator = appGraph.downloadCoordinator,
                            mediaHeaderStore = appGraph.mediaHeaderStore,
                            onBack = navController::popBackStack,
                            onOpenVideo = ::openPlayer,
                            onOpenFeed = { title, category, actor ->
                                navController.navigate(feedRoute(title, category, actor))
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun feedRoute(title: String, category: String?, actor: String?): String {
    fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
    return "${AppRoute.FeedBase}?title=${encode(title)}&category=${encode(category.orEmpty())}&actor=${encode(actor.orEmpty())}"
}
