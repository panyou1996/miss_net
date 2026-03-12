package com.panyou.missnet.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.panyou.missnet.ui.components.MissNetListDivider
import com.panyou.missnet.ui.components.MissNetLoading
import com.panyou.missnet.ui.components.MissNetStateCard
import com.panyou.missnet.ui.components.SecondaryPageSurface
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
            Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
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
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                top = ContainerTokens.ScreenContentPadding,
                                bottom = 8.dp,
                                start = ContainerTokens.ScreenContentPadding,
                                end = ContainerTokens.ScreenContentPadding
                            )
                        ) {
                            items(uiState.tags) { tag ->
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            text = tag,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium
                                        )
                                    },
                                    leadingContent = {
                                        Icon(
                                            Icons.Rounded.Star,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(ContainerTokens.ListLeadingIconSize)
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
