@file:androidx.media3.common.util.UnstableApi

package com.panyou.missnet.ui.screens

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.exoplayer.offline.Download
import coil.compose.SubcomposeAsyncImage
import com.panyou.missnet.data.local.WatchProgressEntry
import com.panyou.missnet.data.media.DownloadStatusEntry
import com.panyou.missnet.data.media.ExportState
import com.panyou.missnet.data.model.Video
import com.panyou.missnet.ui.components.HeroCarouselItem
import com.panyou.missnet.ui.components.MediaPlaceholder
import com.panyou.missnet.ui.components.MissNetErrorState
import com.panyou.missnet.ui.components.MissNetLoading
import com.panyou.missnet.ui.components.SectionHeader
import com.panyou.missnet.ui.components.SmallBadge
import com.panyou.missnet.ui.components.StatusBadge
import com.panyou.missnet.ui.components.VerticalVideoCard
import com.panyou.missnet.ui.theme.ActionTokens
import com.panyou.missnet.ui.theme.ContainerTokens
import com.panyou.missnet.ui.theme.ThumbnailShape
import com.panyou.missnet.ui.viewmodel.HomeViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onVideoClick: (String) -> Unit,
    onCategoryClick: (String, String?) -> Unit,
    onLibraryClick: () -> Unit,
    contentPadding: PaddingValues,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val uiState by viewModel.uiState.collectAsState()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            uiState.isLoading -> MissNetLoading()
            uiState.errorMessage != null &&
                uiState.heroVideos.isEmpty() &&
                uiState.newVideos.isEmpty() &&
                uiState.continueWatching.isEmpty() &&
                uiState.recentDownloads.isEmpty() -> {
                MissNetErrorState(
                    message = uiState.errorMessage ?: "首页加载失败",
                    onRetry = viewModel::retry
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        TaskRecoverySection(
                            continueWatching = uiState.continueWatching,
                            recentDownloads = uiState.recentDownloads,
                            recentFavorites = uiState.recentFavorites,
                            onVideoClick = onVideoClick,
                            onLibraryClick = onLibraryClick
                        )
                    }

                    // Hero/Discovery Section
                    if (uiState.heroVideos.isNotEmpty()) {
                        item {
                            CompactHeroSection(
                                videos = uiState.heroVideos,
                                onVideoClick = onVideoClick,
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope
                            )
                        }
                    }

                    if (uiState.errorMessage != null) {
                        item {
                            ElevatedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = ContainerTokens.ScreenContentPadding),
                                colors = CardDefaults.elevatedCardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = uiState.errorMessage ?: "部分内容加载失败",
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    TextButton(onClick = viewModel::retry) {
                                        Text("重试")
                                    }
                                }
                            }
                        }
                    }


                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = ContainerTokens.ScreenCompactHorizontalPadding, vertical = 4.dp),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
                        ) {
                            Column(modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)) {
                                HomeSection(title = "最新发布", category = "new", videos = uiState.newVideos, onVideoClick = onVideoClick, onCategoryClick = onCategoryClick, sharedTransitionScope = sharedTransitionScope, animatedVisibilityScope = animatedVisibilityScope)
                                HomeSection(title = "本月热门", category = "monthly_hot", videos = uiState.monthlyVideos, onVideoClick = onVideoClick, onCategoryClick = onCategoryClick, sharedTransitionScope = sharedTransitionScope, animatedVisibilityScope = animatedVisibilityScope)
                                HomeSection(title = "无码", category = "uncensored", videos = uiState.uncensoredVideos, onVideoClick = onVideoClick, onCategoryClick = onCategoryClick, sharedTransitionScope = sharedTransitionScope, animatedVisibilityScope = animatedVisibilityScope)
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(ContainerTokens.ScreenBottomPadding)) }
                }
            }
        }
    }
}

@Composable
private fun TaskRecoverySection(
    continueWatching: List<WatchProgressEntry>,
    recentDownloads: List<DownloadStatusEntry>,
    recentFavorites: List<Video>,
    onVideoClick: (String) -> Unit,
    onLibraryClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = ContainerTokens.ScreenCompactHorizontalPadding,
                vertical = ContainerTokens.ScreenCompactVerticalPadding
            ),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
    ) {
        Column(
            modifier = Modifier.padding(ContainerTokens.ScreenContentPadding),
            verticalArrangement = Arrangement.spacedBy(ContainerTokens.ScreenContentPadding)
        ) {
            if (continueWatching.isNotEmpty() || recentDownloads.isNotEmpty() || recentFavorites.isNotEmpty()) {
                TaskSummaryStrip(
                    continueCount = continueWatching.size,
                    downloadCount = recentDownloads.size,
                    favoriteCount = recentFavorites.size
                )
            }

            if (continueWatching.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(ActionTokens.RowSpacing)) {
                    SectionTitleWithMeta(title = "继续播放", meta = "${continueWatching.size} 项")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(continueWatching, key = { it.video.id }) { entry ->
                            ContinueWatchingCard(entry = entry, onClick = { onVideoClick(entry.video.id) })
                        }
                    }
                }
            }

            if (recentDownloads.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(ActionTokens.RowSpacing)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionTitleWithMeta(title = "任务状态", meta = "${recentDownloads.size} 项")
                        TextButton(onClick = onLibraryClick) {
                            Text("查看任务")
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(ActionTokens.RowSpacing)) {
                        recentDownloads.forEach { item ->
                            DownloadGlanceRow(item = item, onClick = onLibraryClick)
                        }
                    }
                }
            }

            if (recentFavorites.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(ActionTokens.RowSpacing)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionTitleWithMeta(title = "最近收藏", meta = "${recentFavorites.size} 项")
                        TextButton(onClick = onLibraryClick) {
                            Text("查看全部")
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(recentFavorites, key = { it.id }) { video ->
                            FavoriteGlanceCard(video = video, onClick = { onVideoClick(video.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskSummaryStrip(
    continueCount: Int,
    downloadCount: Int,
    favoriteCount: Int
) {
    Row(horizontalArrangement = Arrangement.spacedBy(ActionTokens.RowSpacing)) {
        SummaryCard(modifier = Modifier.weight(1f), label = "继续看", value = continueCount.toString())
        SummaryCard(modifier = Modifier.weight(1f), label = "任务", value = downloadCount.toString())
        SummaryCard(modifier = Modifier.weight(1f), label = "收藏", value = favoriteCount.toString())
    }
}

@Composable
private fun SummaryCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String
) {
    ElevatedCard(
        modifier = modifier,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = ContainerTokens.ListRowHorizontalPadding,
                vertical = ContainerTokens.ListRowVerticalPadding
            )
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionTitleWithMeta(title: String, meta: String) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = meta,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickActionGrid(
    continueCount: Int,
    downloadCount: Int,
    favoriteCount: Int,
    onLibraryClick: () -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(ActionTokens.RowSpacing),
        verticalArrangement = Arrangement.spacedBy(ActionTokens.RowSpacing)
    ) {
        QuickActionChip(icon = Icons.Rounded.PlayCircle, label = "继续播放 $continueCount", onClick = onLibraryClick)
        QuickActionChip(icon = Icons.Rounded.CloudDownload, label = "下载任务 $downloadCount", onClick = onLibraryClick)
        QuickActionChip(icon = Icons.Rounded.Favorite, label = "收藏 $favoriteCount", onClick = onLibraryClick)
        QuickActionChip(icon = Icons.AutoMirrored.Rounded.MenuBook, label = "打开资源库", onClick = onLibraryClick)
    }
}

@Composable
private fun QuickActionChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    AssistChip(
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        leadingIcon = {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(ActionTokens.ChipIconSize)
            )
        },
        modifier = Modifier.heightIn(min = ActionTokens.ChipMinHeight)
    )
}

@Composable
private fun ContinueWatchingCard(
    entry: WatchProgressEntry,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .width(312.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = ContainerTokens.ListRowHorizontalPadding,
                vertical = ContainerTokens.ListRowVerticalPadding
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 92.dp, height = 64.dp)
                    .clip(ThumbnailShape)
            ) {
                MediaThumbnail(
                    coverUrl = entry.video.coverUrl,
                    label = "封面待同步"
                )
                if (entry.progress > 0f) {
                    SmallBadge(
                        text = "已看 ${(entry.progress * 100).toInt()}%",
                        containerColor = Color.Black.copy(alpha = 0.58f),
                        contentColor = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                    )
                }
            }
            Column(
                modifier = Modifier
                    .padding(start = ContainerTokens.ListItemVerticalPadding)
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(ActionTokens.ButtonContentGap)
            ) {
                Text(
                    text = entry.video.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = entry.video.actors.firstOrNull() ?: "未知演员",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "已观看 ${(entry.progress * 100).toInt()}% · 剩余 ${formatCompactDuration((entry.durationMs - entry.positionMs).coerceAtLeast(0L))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LinearProgressIndicator(
                    progress = { entry.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

private fun formatCompactDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) "${hours}小时${minutes}分" else "${minutes}分"
}

@Composable
private fun FavoriteGlanceCard(
    video: Video,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .width(312.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = ContainerTokens.ListRowHorizontalPadding,
                vertical = ContainerTokens.ListRowVerticalPadding
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 92.dp, height = 64.dp)
                    .clip(ThumbnailShape)
            ) {
                MediaThumbnail(
                    coverUrl = video.coverUrl,
                    label = "封面待同步"
                )
            }
            Column(
                modifier = Modifier
                    .padding(start = ContainerTokens.ListItemVerticalPadding)
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(ActionTokens.ButtonContentGap)
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = video.actors.firstOrNull() ?: "未知演员",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun MediaThumbnail(
    coverUrl: String?,
    label: String,
    modifier: Modifier = Modifier
) {
    val effectiveCoverUrl = coverUrl?.takeIf { it.isNotBlank() }
    if (effectiveCoverUrl != null) {
        SubcomposeAsyncImage(
            model = effectiveCoverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
            loading = {
                MediaPlaceholder(label = "封面加载中")
            },
            error = {
                MediaPlaceholder(label = label)
            }
        )
    } else {
        MediaPlaceholder(
            modifier = modifier.fillMaxSize(),
            label = label
        )
    }
}

@Composable
private fun DownloadGlanceRow(
    item: DownloadStatusEntry,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = ContainerTokens.ListRowHorizontalPadding,
                vertical = ContainerTokens.ListRowVerticalPadding
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ContainerTokens.ListItemVerticalPadding)
        ) {
            Icon(
                imageVector = when {
                    item.state == Download.STATE_FAILED || item.exportState == ExportState.EXPORT_FAILED -> Icons.Rounded.WarningAmber
                    item.state == Download.STATE_COMPLETED -> Icons.Rounded.CloudDownload
                    else -> Icons.Rounded.AccessTime
                },
                contentDescription = null,
                tint = when {
                    item.state == Download.STATE_FAILED || item.exportState == ExportState.EXPORT_FAILED -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.size(ContainerTokens.ListLeadingIconSize)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.homeSummaryLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            HomeStatusBadge(item = item)
        }
    }
}

@Composable
private fun HomeStatusBadge(item: DownloadStatusEntry) {
    val (label, container, content) = when {
        item.state == Download.STATE_FAILED || item.exportState == ExportState.EXPORT_FAILED -> Triple("需要处理", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        item.state == Download.STATE_COMPLETED && item.exportState == ExportState.EXPORT_UNSUPPORTED -> Triple("不支持", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        item.state == Download.STATE_COMPLETED && item.exportState == ExportState.EXPORTED -> Triple("已导出", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        item.state == Download.STATE_COMPLETED -> Triple("最近完成", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
        else -> Triple("进行中", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
    }
    StatusBadge(text = label, containerColor = container, contentColor = content)
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun CompactHeroSection(
    videos: List<Video>,
    onVideoClick: (String) -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    Column {
        SectionHeader(title = "发现内容")
        LazyRow(
            contentPadding = PaddingValues(horizontal = ContainerTokens.ScreenContentPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(videos) { video ->
                Box(modifier = Modifier.height(188.dp).width(312.dp)) {
                    HeroCarouselItem(
                        video = video,
                        onClick = { onVideoClick(video.id) },
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun HomeSection(
    title: String,
    category: String?,
    videos: List<Video>,
    onVideoClick: (String) -> Unit,
    onCategoryClick: (String, String?) -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    if (videos.isNotEmpty()) {
        Column {
            SectionHeader(title = title, onClick = { onCategoryClick(title, category) })
            LazyRow(
                contentPadding = PaddingValues(horizontal = ContainerTokens.ScreenContentPadding),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(videos) { video ->
                    VerticalVideoCard(
                        video = video,
                        onClick = { onVideoClick(video.id) },
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = ContainerTokens.ScreenContentPadding),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        }
    }
}
