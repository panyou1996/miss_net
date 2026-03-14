package com.panyou.missnet.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.panyou.missnet.ui.components.MissNetListDivider
import com.panyou.missnet.ui.components.MissNetErrorState
import com.panyou.missnet.ui.components.MissNetLoading
import com.panyou.missnet.ui.components.MissNetStatePane
import com.panyou.missnet.ui.components.SecondaryPageSurface
import com.panyou.missnet.ui.components.VideoCard
import com.panyou.missnet.ui.theme.ContainerTokens
import com.panyou.missnet.ui.theme.MotionTokens
import com.panyou.missnet.ui.viewmodel.SearchViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onVideoClick: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadSearchHistory()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            SearchBar(
                inputField = {
                    SearchBarDefaults.InputField(
                        query = uiState.query,
                        onQueryChange = { viewModel.onQueryChange(it) },
                        onSearch = { viewModel.search(it) },
                        expanded = uiState.active,
                        onExpandedChange = { viewModel.onActiveChange(it) },
                        placeholder = { Text("搜索标题") },
                        leadingIcon = {
                            if (uiState.active) {
                                IconButton(onClick = {
                                    if (uiState.query.isNotEmpty()) {
                                        viewModel.onQueryChange("")
                                    } else {
                                        viewModel.onActiveChange(false)
                                        onBack()
                                    }
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                                }
                            } else {
                                Icon(Icons.Default.Search, contentDescription = "搜索")
                            }
                        },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (uiState.history.isNotEmpty() && uiState.active) {
                                    IconButton(onClick = viewModel::clearSearchHistory) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "清空历史")
                                    }
                                }
                                if (uiState.query.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.onQueryChange("") }) {
                                        Icon(Icons.Default.Close, contentDescription = "清空")
                                    }
                                }
                            }
                        }
                    )
                },
                expanded = uiState.active,
                onExpandedChange = { viewModel.onActiveChange(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = ContainerTokens.ScreenCompactHorizontalPadding,
                        vertical = ContainerTokens.ScreenCompactVerticalPadding
                    )
            ) {
                LazyColumn {
                    itemsIndexed(uiState.history) { index, historyItem ->
                        ListItem(
                            headlineContent = { Text(historyItem) },
                            leadingContent = {
                                Icon(
                                    Icons.Default.History,
                                    null,
                                    modifier = Modifier.size(ContainerTokens.ListLeadingIconSize)
                                )
                            },
                            trailingContent = {
                                IconButton(onClick = { viewModel.removeHistoryItem(historyItem) }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "删除",
                                        modifier = Modifier.size(ContainerTokens.ListTrailingIconSize)
                                    )
                                }
                            },
                            modifier = Modifier.clickable {
                                viewModel.onQueryChange(historyItem)
                                viewModel.search(historyItem)
                            }
                        )
                        if (index < uiState.history.lastIndex) {
                            MissNetListDivider()
                        }
                    }
                }
            }

            SecondaryPageSurface {
                AnimatedContent(
                    targetState = when {
                        uiState.isLoading -> SearchContentState.Loading
                        uiState.errorMessage != null && uiState.query.isNotBlank() -> SearchContentState.Error
                        uiState.results.isNotEmpty() -> SearchContentState.Results
                        uiState.query.isBlank() -> SearchContentState.Idle
                        else -> SearchContentState.Empty
                    },
                    transitionSpec = {
                        fadeIn(animationSpec = MotionTokens.standard(MotionTokens.DurationShort4)) +
                            scaleIn(initialScale = 0.98f, animationSpec = MotionTokens.standard(MotionTokens.DurationShort4)) togetherWith
                            fadeOut(animationSpec = MotionTokens.exit(MotionTokens.DurationShort3)) +
                            scaleOut(targetScale = 0.98f, animationSpec = MotionTokens.exit(MotionTokens.DurationShort3))
                    },
                    label = "search-content-state",
                    modifier = Modifier.fillMaxSize()
                ) { contentState ->
                    when (contentState) {
                        SearchContentState.Loading -> MissNetLoading()

                        SearchContentState.Error -> MissNetErrorState(
                            message = uiState.errorMessage ?: "搜索失败",
                            onRetry = viewModel::retry
                        )

                        SearchContentState.Results -> {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 160.dp),
                                contentPadding = PaddingValues(ContainerTokens.ScreenContentPadding),
                                horizontalArrangement = Arrangement.spacedBy(ContainerTokens.GridItemSpacing),
                                verticalArrangement = Arrangement.spacedBy(ContainerTokens.GridItemSpacing),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(uiState.results, key = { it.id }) { video ->
                                    VideoCard(
                                        videoId = video.id,
                                        title = video.title,
                                        coverUrl = video.coverUrl,
                                        duration = video.displayDurationOrNull,
                                        onClick = { onVideoClick(video.id) },
                                        sharedTransitionScope = sharedTransitionScope,
                                        animatedVisibilityScope = animatedVisibilityScope
                                    )
                                }
                                item { Spacer(modifier = Modifier.height(ContainerTokens.ScreenBottomPadding)) }
                            }
                        }

                        SearchContentState.Idle -> SearchState(
                            icon = Icons.Default.Search,
                            title = "输入标题关键词开始搜索",
                            subtitle = "当前版本仅支持按视频标题搜索，可从上方搜索框开始。",
                            actionLabel = if (uiState.history.isNotEmpty()) "查看最近搜索" else null,
                            onAction = if (uiState.history.isNotEmpty()) {
                                { viewModel.onActiveChange(true) }
                            } else {
                                null
                            }
                        )

                        SearchContentState.Empty -> SearchState(
                            icon = Icons.Default.History,
                            title = "暂未找到匹配内容",
                            subtitle = "试试更短的标题关键词，或换一个标题片段。",
                            actionLabel = "清空关键词",
                            onAction = { viewModel.onQueryChange("") }
                        )
                    }
                }
            }
        }
    }
}

private enum class SearchContentState {
    Loading,
    Error,
    Results,
    Idle,
    Empty
}

@Composable
private fun SearchState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        MissNetStatePane(
            icon = icon,
            title = title,
            subtitle = subtitle,
            actionLabel = actionLabel,
            onAction = onAction,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}
