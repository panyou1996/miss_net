package com.panyou.missnet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint
import com.panyou.missnet.ui.theme.MissNetTheme
import com.panyou.missnet.ui.theme.MotionTokens
import com.panyou.missnet.ui.screens.*
import com.panyou.missnet.ui.viewmodel.SettingsViewModel
import com.panyou.missnet.ui.components.MainTabScaffold
import com.panyou.missnet.ui.components.SimpleTopBarScaffold

sealed class Screen(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Home : Screen("home", "Home", Icons.Rounded.Home)
    object Actress : Screen("actress", "Actress", Icons.Rounded.People)
    object Tags : Screen("tags", "Tags", Icons.Rounded.Tag)
    object Library : Screen("library", "Library", Icons.Rounded.VideoLibrary)
    object Settings : Screen("settings", "Settings", Icons.Rounded.Settings)
    object Search : Screen("search", "Search", Icons.Rounded.Search)
    object CategoryDetail : Screen("categoryDetail/{title}?category={category}&actor={actor}", "Detail", Icons.AutoMirrored.Rounded.List) {
        fun createRoute(title: String, category: String? = null, actor: String? = null) = 
            "categoryDetail/$title?category=${category ?: ""}&actor=${actor ?: ""}"
    }
    object Player : Screen("player/{videoId}", "Player", Icons.Rounded.PlayArrow) {
        fun createRoute(id: String) = "player/$id"
    }
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { 
            val settingsState by settingsViewModel.uiState.collectAsState()
            
            val themeColor = remember(settingsState.themeColorIndex) {
                when (settingsState.themeColorIndex) {
                    0 -> Color(0xFFEF4444)
                    1 -> Color(0xFF3B82F6)
                    2 -> Color(0xFF10B981)
                    3 -> Color(0xFFA855F7)
                    else -> Color(0xFFEF4444)
                }
            }

            MissNetTheme(
                darkTheme = settingsState.isDarkMode,
                themeColor = themeColor
            ) { 
                MainScreen(settingsViewModel) 
            } 
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun MainScreen(settingsViewModel: SettingsViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    
    SharedTransitionLayout {
        NavHost(
            navController = navController, 
            startDestination = Screen.Home.route, 
            modifier = Modifier.fillMaxSize(),
            enterTransition = {
                val fromRoot = initialState.destination.route in listOf(Screen.Home.route, Screen.Actress.route, Screen.Tags.route, Screen.Library.route)
                val toRoot = targetState.destination.route in listOf(Screen.Home.route, Screen.Actress.route, Screen.Tags.route, Screen.Library.route)
                
                if (fromRoot && toRoot) {
                    fadeIn(animationSpec = tween(300)) 
                } else {
                    scaleIn(
                        initialScale = 0.85f, 
                        animationSpec = tween(450, easing = MotionTokens.EmphasizedDecelerate)
                    ) + fadeIn(animationSpec = tween(300))
                }
            },
            exitTransition = {
                val fromRoot = initialState.destination.route in listOf(Screen.Home.route, Screen.Actress.route, Screen.Tags.route, Screen.Library.route)
                val toRoot = targetState.destination.route in listOf(Screen.Home.route, Screen.Actress.route, Screen.Tags.route, Screen.Library.route)
                
                if (fromRoot && toRoot) {
                    fadeOut(animationSpec = tween(300))
                } else {
                    scaleOut(
                        targetScale = 1.1f, 
                        animationSpec = tween(400, easing = MotionTokens.EmphasizedAccelerate)
                    ) + fadeOut(animationSpec = tween(300))
                }
            },
            popEnterTransition = {
                val fromRoot = initialState.destination.route in listOf(Screen.Home.route, Screen.Actress.route, Screen.Tags.route, Screen.Library.route)
                val toRoot = targetState.destination.route in listOf(Screen.Home.route, Screen.Actress.route, Screen.Tags.route, Screen.Library.route)
                
                if (fromRoot && toRoot) {
                    fadeIn(animationSpec = tween(300))
                } else {
                    scaleIn(
                        initialScale = 1.1f, 
                        animationSpec = tween(450, easing = MotionTokens.EmphasizedDecelerate)
                    ) + fadeIn(animationSpec = tween(300))
                }
            },
            popExitTransition = {
                val fromRoot = initialState.destination.route in listOf(Screen.Home.route, Screen.Actress.route, Screen.Tags.route, Screen.Library.route)
                val toRoot = targetState.destination.route in listOf(Screen.Home.route, Screen.Actress.route, Screen.Tags.route, Screen.Library.route)
                
                if (fromRoot && toRoot) {
                    fadeOut(animationSpec = tween(300))
                } else {
                    scaleOut(
                        targetScale = 0.85f, 
                        animationSpec = tween(400, easing = MotionTokens.EmphasizedAccelerate)
                    ) + fadeOut(animationSpec = tween(300))
                }
            }
        ) {
            // ========== Home Tab ==========
            composable(Screen.Home.route) { 
                MainTabRouteShell(
                    title = "MissNet",
                    navController = navController,
                    currentDestination = currentDestination,
                    scrollBehavior = scrollBehavior
                ) { innerPadding ->
                    HomeScreen(
                        onVideoClick = { id -> navController.navigate(Screen.Player.createRoute(id)) },
                        onCategoryClick = { title, cat -> 
                            navController.navigate(Screen.CategoryDetail.createRoute(title, cat, null)) 
                        },
                        onLibraryClick = { navController.navigate(Screen.Library.route) },
                        contentPadding = innerPadding,
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this@composable
                    )
                }
            }
            
            // ========== Actress Tab ==========
            composable(Screen.Actress.route) { 
                MainTabRouteShell(
                    title = "Actress",
                    navController = navController,
                    currentDestination = currentDestination,
                    scrollBehavior = scrollBehavior
                ) { innerPadding ->
                    ActressScreen(
                        onActressClick = { name -> navController.navigate(Screen.CategoryDetail.createRoute(name, null, name)) },
                        contentPadding = innerPadding
                    )
                }
            }
            
            // ========== Tags Tab ==========
            composable(Screen.Tags.route) { 
                MainTabRouteShell(
                    title = "Tags",
                    navController = navController,
                    currentDestination = currentDestination,
                    scrollBehavior = scrollBehavior
                ) { innerPadding ->
                    TagsScreen(
                        onTagClick = { tag -> navController.navigate(Screen.CategoryDetail.createRoute(tag, tag, null)) },
                        contentPadding = innerPadding
                    )
                }
            }
            
            // ========== Library Tab ==========
            composable(Screen.Library.route) { 
                MainTabRouteShell(
                    title = "Library",
                    navController = navController,
                    currentDestination = currentDestination,
                    scrollBehavior = scrollBehavior
                ) { innerPadding ->
                    LibraryScreen(
                        onVideoClick = { id -> navController.navigate(Screen.Player.createRoute(id)) },
                        contentPadding = innerPadding,
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this@composable
                    )
                }
            }
            
            // ========== Search Screen ==========
            composable(Screen.Search.route) {
                SearchScreen(
                    onBack = { navController.popBackStack() },
                    onVideoClick = { id -> navController.navigate(Screen.Player.createRoute(id)) },
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this@composable
                )
            }
            
            // ========== Settings Screen ==========
            composable(Screen.Settings.route) { 
                SimpleTopBarScaffold(
                    title = "Settings",
                    scrollBehavior = null,
                    onBackClick = { navController.popBackStack() },
                    showSettings = false
                ) { innerPadding ->
                    SettingsScreen(viewModel = settingsViewModel, contentPadding = innerPadding) 
                }
            }
            
            // ========== Category Detail Screen ==========
            composable(
                route = Screen.CategoryDetail.route,
                arguments = listOf(
                    navArgument("title") { type = NavType.StringType },
                    navArgument("category") { type = NavType.StringType; nullable = true },
                    navArgument("actor") { type = NavType.StringType; nullable = true }
                )
            ) { backStackEntry ->
                val title = backStackEntry.arguments?.getString("title") ?: "Detail"
                SimpleTopBarScaffold(
                    title = title,
                    scrollBehavior = scrollBehavior,
                    onBackClick = { navController.popBackStack() },
                    onSettingsClick = { navController.navigate(Screen.Settings.route) }
                ) { innerPadding ->
                    CategoryDetailScreen(
                        category = backStackEntry.arguments?.getString("category")?.takeIf { it.isNotEmpty() },
                        actor = backStackEntry.arguments?.getString("actor")?.takeIf { it.isNotEmpty() },
                        onVideoClick = { id -> navController.navigate(Screen.Player.createRoute(id)) },
                        contentPadding = innerPadding,
                        scrollBehavior = scrollBehavior,
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this@composable
                    )
                }
            }
            
            // ========== Player Screen ==========
            composable(Screen.Player.route) { backStackEntry ->
                PlayerScreen(
                    videoId = backStackEntry.arguments?.getString("videoId") ?: "",
                    onBack = { navController.popBackStack() },
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this@composable
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTabRouteShell(
    title: String,
    navController: NavHostController,
    currentDestination: androidx.navigation.NavDestination?,
    scrollBehavior: TopAppBarScrollBehavior,
    content: @Composable (PaddingValues) -> Unit
) {
    MainTabScaffold(
        title = title,
        navController = navController,
        currentDestination = currentDestination,
        scrollBehavior = scrollBehavior,
        onSearchClick = { navController.navigate(Screen.Search.route) },
        onSettingsClick = { navController.navigate(Screen.Settings.route) },
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissNetTopBar(
    isMainTab: Boolean, 
    title: String,
    onSearchClick: () -> Unit, 
    onBackClick: () -> Unit, 
    onAvatarClick: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    showAvatar: Boolean = true
) {
    if (isMainTab) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clickable { onSearchClick() },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 2.dp,
            shadowElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { /* Drawer or action */ }) {
                    Icon(imageVector = Icons.Rounded.Menu, contentDescription = "Menu", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                
                Text(
                    text = "Search in MissNet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                
                if (showAvatar) {
                    IconButton(onClick = onAvatarClick) {
                        Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(0xFFECF3FE)), contentAlignment = Alignment.Center) {
                            Text("M", color = Color(0xFF1967D2), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    } else {
        CenterAlignedTopAppBar(
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            scrollBehavior = scrollBehavior,
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            actions = {
                if (showAvatar) {
                    IconButton(onClick = onAvatarClick) {
                        Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(0xFFECF3FE)), contentAlignment = Alignment.Center) {
                            Text("M", color = Color(0xFF1967D2), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.size(48.dp)) // Balance the back button
                }
            }
        )
    }
}

@Composable
fun MissNetBottomNavigation(navController: androidx.navigation.NavController, currentDestination: androidx.navigation.NavDestination?) {
    val navItems = listOf(Screen.Home, Screen.Actress, Screen.Tags, Screen.Library)
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        navItems.forEach { screen ->
            val isSelected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
            NavigationBarItem(
                icon = { Icon(screen.icon, null) },
                label = { Text(screen.title) },
                selected = isSelected,
                onClick = {
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}
