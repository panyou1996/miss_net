package com.panyou.missnet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.People
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.panyou.missnet.data.model.ActorInfo
import com.panyou.missnet.ui.components.MissNetCoverImage
import com.panyou.missnet.ui.components.MissNetLoading
import com.panyou.missnet.ui.components.MissNetStatePane
import com.panyou.missnet.ui.components.SecondaryPageSurface
import com.panyou.missnet.ui.components.SmallBadge
import com.panyou.missnet.ui.theme.ContainerTokens
import com.panyou.missnet.ui.util.bouncyClick
import com.panyou.missnet.ui.viewmodel.ActressViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActressScreen(
    onActressClick: (String) -> Unit,
    contentPadding: PaddingValues,
    viewModel: ActressViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    // Note: A-Z filter disabled - requires pinyin data for actual filtering
    val actresses = uiState.actresses

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (uiState.isLoading) {
            MissNetLoading()
        } else {
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize().padding(contentPadding)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    SecondaryPageSurface {
                        if (actresses.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                MissNetStatePane(
                                    icon = Icons.Rounded.People,
                                    title = "暂无可浏览演员",
                                    subtitle = "当前还没有可展示的演员入口，请稍后再试。",
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                )
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 124.dp),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(ContainerTokens.ScreenContentPadding),
                                horizontalArrangement = Arrangement.spacedBy(ContainerTokens.GridItemSpacing),
                                verticalArrangement = Arrangement.spacedBy(ContainerTokens.GridItemSpacing)
                            ) {
                                itemsIndexed(actresses) { index, actor ->
                                    ActressItem(
                                        actor = actor,
                                        isFeatured = index < 12,
                                        onClick = { onActressClick(actor.name) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActressItem(actor: ActorInfo, isFeatured: Boolean, onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().bouncyClick(onClick = onClick),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                MissNetCoverImage(
                    coverUrl = actor.coverUrl,
                    contentDescription = actor.name,
                    modifier = Modifier.fillMaxSize(),
                    emptyLabel = "资料待补充"
                )
                if (isFeatured) {
                    SmallBadge(
                        text = "热门",
                        containerColor = Color.Black.copy(alpha = 0.58f),
                        contentColor = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                    )
                }
            }
            Text(
                text = actor.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = buildString {
                    if (actor.videoCount > 0) {
                        append("${actor.videoCount} 部")
                    }
                    actor.latestReleaseDate?.takeIf { it.isNotBlank() }?.let {
                        if (isNotEmpty()) append(" · ")
                        append(it)
                    }
                    if (isEmpty()) {
                        append("近期收录")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
