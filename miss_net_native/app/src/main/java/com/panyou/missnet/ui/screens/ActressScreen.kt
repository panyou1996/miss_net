package com.panyou.missnet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.People
import androidx.compose.material3.*
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
import coil.compose.SubcomposeAsyncImage
import com.panyou.missnet.data.model.ActorInfo
import com.panyou.missnet.ui.components.BrowseSummaryCard
import com.panyou.missnet.ui.components.MediaPlaceholder
import com.panyou.missnet.ui.components.MissNetLoading
import com.panyou.missnet.ui.components.MissNetStateCard
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
            Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
                // Grid of Actresses
                SecondaryPageSurface {
                    if (actresses.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            MissNetStateCard(
                                icon = Icons.Rounded.People,
                                title = "暂无演员数据",
                                subtitle = "请尝试其他筛选条件",
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            BrowseSummaryCard(
                                title = "热门演员",
                                summary = "共 ${actresses.size} 位 · 优先展示常用浏览入口",
                                helper = "点击卡片查看该演员相关内容",
                                modifier = Modifier.padding(ContainerTokens.ScreenContentPadding)
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)
                            )
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
                val coverUrl = actor.coverUrl?.takeIf { it.isNotBlank() }
                if (coverUrl != null) {
                    SubcomposeAsyncImage(
                        model = coverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        loading = {
                            MediaPlaceholder(label = "封面加载中")
                        },
                        error = {
                            MediaPlaceholder(label = "资料待补充")
                        }
                    )
                } else {
                    MediaPlaceholder(label = "资料待补充")
                }
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
                text = if (isFeatured) "热门演员 · 点击查看相关内容" else "点击查看该演员相关内容",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
