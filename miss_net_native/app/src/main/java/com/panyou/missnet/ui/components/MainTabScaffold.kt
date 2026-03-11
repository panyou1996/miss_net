package com.panyou.missnet.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import com.panyou.missnet.MissNetBottomNavigation
import com.panyou.missnet.MissNetTopBar

/**
 * 统一的主 Tab 页面骨架
 * 
 * 封装了 Home / Actress / Tags / Library 四个主 tab 页面的公共结构：
 * - Scaffold + TopBar + BottomBar 的标准布局
 * - statusBarsPadding 处理
 * - scrollBehavior 集成
 * 
 * 使用此组件可以避免后续因滚动/布局/padding 不一致导致的问题
 *
 * @param title 页面标题
 * @param navController 导航控制器
 * @param currentDestination 当前导航目标
 * @param scrollBehavior 顶部应用栏滚动行为
 * @param onSearchClick 搜索按钮点击回调
 * @param onSettingsClick 设置/头像按钮点击回调
 * @param content 内容区域
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTabScaffold(
    title: String,
    navController: NavController,
    currentDestination: NavDestination?,
    scrollBehavior: TopAppBarScrollBehavior,
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    MissNetTopBar(
                        isMainTab = true,
                        title = title,
                        onMenuClick = onMenuClick,
                        onSearchClick = onSearchClick,
                        onBackClick = { },
                        onAvatarClick = onSettingsClick,
                        scrollBehavior = scrollBehavior
                    )
                }
            }
        },
        bottomBar = {
            MissNetBottomNavigation(
                navController = navController,
                currentDestination = currentDestination
            )
        }
    ) { innerPadding ->
        content(innerPadding)
    }
}

/**
 * 非主 Tab 页面的简易 Scaffold
 * 
 * 适用于不需要 BottomNavigation 的页面（如 Settings、Search 等）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleTopBarScaffold(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior?,
    onBackClick: () -> Unit,
    onSettingsClick: (() -> Unit)? = null,
    showSettings: Boolean = true,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        topBar = {
            MissNetTopBar(
                isMainTab = false,
                title = title,
                onMenuClick = { },
                onSearchClick = { },
                onBackClick = onBackClick,
                onAvatarClick = onSettingsClick ?: { },
                scrollBehavior = scrollBehavior,
                showAvatar = showSettings
            )
        }
    ) { innerPadding ->
        content(innerPadding)
    }
}
