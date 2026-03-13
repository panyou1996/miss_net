package com.panyou.missnet.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.panyou.missnet.ui.components.BrowseSummaryCard
import com.panyou.missnet.ui.components.MissNetListDivider
import com.panyou.missnet.ui.components.MissNetLoading
import com.panyou.missnet.ui.components.MissNetStateCard
import com.panyou.missnet.ui.components.SecondaryPageSurface
import com.panyou.missnet.ui.components.SmallBadge
import com.panyou.missnet.ui.theme.ContainerTokens
import com.panyou.missnet.ui.viewmodel.TagsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsScreen(
    onTagClick: (String) -> Unit,
    contentPadding: PaddingValues,
    viewModel: TagsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (uiState.isLoading) {
            MissNetLoading()
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(contentPadding).padding(top = ContainerTokens.ScreenContentPadding)) {
                SecondaryPageSurface {
                    if (uiState.tags.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            MissNetStateCard(
                                icon = Icons.Rounded.Star,
                                title = "暂无标签数据",
                                subtitle = "稍后重试，或等待抓取任务同步最新标签",
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            BrowseSummaryCard(
                                title = "热门标签",
                                summary = "共 ${uiState.tags.size} 项 · 默认按热度与常用程度排序",
                                helper = "点击标签即可进入对应内容列表",
                                modifier = Modifier.padding(ContainerTokens.ScreenContentPadding)
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)
                            )
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    top = 8.dp,
                                    bottom = 8.dp,
                                    start = ContainerTokens.ScreenContentPadding,
                                    end = ContainerTokens.ScreenContentPadding
                                )
                            ) {
                                itemsIndexed(uiState.tags) { index, tag ->
                                    ListItem(
                                        headlineContent = {
                                            Text(
                                                text = tag,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Medium
                                            )
                                        },
                                        supportingContent = {
                                            Text(
                                                text = when {
                                                    index < 3 -> "高热度入口 · 优先推荐浏览"
                                                    index < 10 -> "热门标签 · 点击查看聚合内容"
                                                    else -> "按标签浏览相关内容"
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        },
                                        leadingContent = {
                                            SmallBadge(
                                                text = "#${index + 1}",
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        },
                                        trailingContent = {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                        modifier = Modifier.clickable { onTagClick(tag) }
                                    )
                                    MissNetListDivider()
                                }
                                item { Spacer(modifier = Modifier.height(ContainerTokens.ScreenBottomPadding)) }
                            }
                        }
                    }
                }
            }
        }
    }
}
