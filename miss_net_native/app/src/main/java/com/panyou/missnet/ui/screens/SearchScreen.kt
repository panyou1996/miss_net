package com.panyou.missnet.ui.screens

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.panyou.missnet.ui.components.MissNetErrorState
import com.panyou.missnet.ui.components.MissNetLoading
import com.panyou.missnet.ui.components.VideoCard
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
                query = uiState.query,
                onQueryChange = { viewModel.onQueryChange(it) },
                onSearch = { viewModel.search(it) },
                active = uiState.active,
                onActiveChange = { viewModel.onActiveChange(it) },
                placeholder = { Text("Search videos, actors...") },
                leadingIcon = {
                    if (uiState.active) {
                        IconButton(onClick = {
                            if (uiState.query.isNotEmpty()) viewModel.onQueryChange("") else {
                                viewModel.onActiveChange(false)
                                onBack()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    } else {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (uiState.history.isNotEmpty() && uiState.active) {
                            IconButton(onClick = viewModel::clearSearchHistory) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Clear history")
                            }
                        }
                        if (uiState.query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onQueryChange("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                LazyColumn {
                    items(uiState.history) { historyItem ->
                        ListItem(
                            headlineContent = { Text(historyItem) },
                            leadingContent = { Icon(Icons.Default.History, null) },
                            trailingContent = {
                                IconButton(onClick = { viewModel.removeHistoryItem(historyItem) }) {
                                    Icon(Icons.Default.Close, contentDescription = "Delete")
                                }
                            },
                            modifier = Modifier.clickable {
                                viewModel.onQueryChange(historyItem)
                                viewModel.search(historyItem)
                            }
                        )
                    }
                }
            }

            when {
                uiState.isLoading -> {
                    MissNetLoading()
                }
                uiState.errorMessage != null && uiState.query.isNotBlank() -> {
                    MissNetErrorState(
                        message = uiState.errorMessage ?: "搜索失败",
                        onRetry = viewModel::retry,
                        title = if (uiState.results.isEmpty()) "未找到结果" else "搜索失败"
                    )
                }
                uiState.results.isNotEmpty() -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(uiState.results) { video ->
                            VideoCard(
                                videoId = video.id,
                                title = video.title,
                                coverUrl = video.coverUrl ?: "",
                                duration = video.duration,
                                onClick = { onVideoClick(video.id) },
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope
                            )
                        }
                    }
                }
            }
        }
    }
}
