@file:androidx.media3.common.util.UnstableApi

package com.panyou.missnet.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.exoplayer.offline.Download
import com.panyou.missnet.ui.components.BrowseSummaryCard
import com.panyou.missnet.data.media.DownloadStatusEntry
import com.panyou.missnet.data.media.ExportState
import com.panyou.missnet.data.local.WatchProgressEntry
import com.panyou.missnet.data.model.Video
import com.panyou.missnet.ui.components.MissNetCoverImage
import com.panyou.missnet.ui.components.MissNetLoading
import com.panyou.missnet.ui.components.MissNetStatePane
import com.panyou.missnet.ui.components.VideoCard
import com.panyou.missnet.ui.components.DurationBadge
import com.panyou.missnet.ui.components.StatusBadge
import com.panyou.missnet.ui.theme.ActionTokens
import com.panyou.missnet.ui.theme.ContainerTokens
import com.panyou.missnet.ui.theme.MotionTokens
import com.panyou.missnet.ui.theme.ThumbnailShape
import com.panyou.missnet.ui.viewmodel.LibraryViewModel
import java.util.Locale

private enum class LibraryTab(val title: String) {
    Downloads("任务"),
    History("继续看"),
    Likes("收藏")
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    onVideoClick: (String) -> Unit,
    onHomeClick: () -> Unit,
    onSearchClick: () -> Unit,
    contentPadding: PaddingValues,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(LibraryTab.Downloads.ordinal) }

    LaunchedEffect(Unit) {
        viewModel.loadAll()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                divider = {}
            ) {
                LibraryTab.entries.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        text = { Text(tab.title) }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                AnimatedContent(
                    targetState = LibraryTab.entries[selectedTab],
                    transitionSpec = {
                        fadeIn(animationSpec = MotionTokens.standard(MotionTokens.DurationShort4)) togetherWith
                            fadeOut(animationSpec = MotionTokens.exit(MotionTokens.DurationShort3))
                    },
                    label = "library-tab-content"
                ) { currentTab ->
                    when (currentTab) {
                        LibraryTab.Downloads -> DownloadsPage(
                            downloads = uiState.downloads,
                            onVideoClick = onVideoClick,
                            actionLabel = "去首页发现内容",
                            onAction = onHomeClick,
                            onPause = viewModel::pauseDownload,
                            onResume = viewModel::resumeDownload,
                            onRetry = viewModel::retryDownload,
                            onRemove = viewModel::removeDownload,
                            onExport = viewModel::exportDownload
                        )
                        LibraryTab.History -> ContinueWatchingPage(
                            entries = uiState.historyEntries,
                            isLoading = uiState.isLoading,
                            onVideoClick = onVideoClick,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            actionLabel = "去首页发现内容",
                            onAction = onHomeClick
                        )
                        LibraryTab.Likes -> VideoGridPage(
                            title = "收藏",
                            emptyTitle = "暂无收藏",
                            emptySubtitle = "你收藏的内容会集中显示在这里。",
                            icon = Icons.Rounded.Favorite,
                            isLoading = uiState.isLoading,
                            videos = uiState.likes,
                            historyProgress = emptyMap(),
                            onVideoClick = onVideoClick,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            actionLabel = "去搜索发现内容",
                            onAction = onSearchClick
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun VideoGridPage(
    title: String,
    emptyTitle: String,
    emptySubtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isLoading: Boolean,
    videos: List<Video>,
    historyProgress: Map<String, Float>,
    onVideoClick: (String) -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    if (isLoading && videos.isEmpty()) {
        MissNetLoading()
        return
    }

    if (videos.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = ContainerTokens.ScreenCompactHorizontalPadding,
                    vertical = ContainerTokens.ScreenContentPadding
                ),
            contentAlignment = Alignment.TopCenter
        ) {
            LibraryEmptyStateCard(
                icon = icon,
                title = emptyTitle,
                subtitle = emptySubtitle,
                actionLabel = actionLabel,
                onAction = onAction,
                modifier = Modifier.padding(top = 56.dp)
            )
        }
        return
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = ContainerTokens.ScreenCompactHorizontalPadding,
                end = ContainerTokens.ScreenCompactHorizontalPadding,
                bottom = ContainerTokens.ScreenContentPadding
            ),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            BrowseSummaryCard(
                title = title,
                summary = "共 ${videos.size} 项 · 以卡片形式集中浏览",
                helper = if (title == "收藏") "点击卡片可快速进入已收藏内容。" else "点击卡片可继续浏览对应内容。",
                modifier = Modifier.padding(ContainerTokens.ScreenContentPadding)
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(ContainerTokens.ScreenContentPadding),
                horizontalArrangement = Arrangement.spacedBy(ContainerTokens.GridItemSpacing),
                verticalArrangement = Arrangement.spacedBy(ContainerTokens.GridItemSpacing),
                modifier = Modifier.fillMaxSize()
            ) {
                items(videos) { video ->
                    VideoCard(
                        videoId = video.id,
                        title = video.title,
                        coverUrl = video.coverUrl ?: "",
                        duration = video.duration,
                        progress = historyProgress[video.id],
                        showFavoriteBadge = title == "收藏",
                        onClick = { onVideoClick(video.id) },
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope
                    )
                }
                item { Spacer(modifier = Modifier.height(ContainerTokens.ScreenBottomPadding)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun ContinueWatchingPage(
    entries: List<WatchProgressEntry>,
    isLoading: Boolean,
    onVideoClick: (String) -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    if (isLoading && entries.isEmpty()) {
        MissNetLoading()
        return
    }

    if (entries.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = ContainerTokens.ScreenCompactHorizontalPadding,
                    vertical = ContainerTokens.ScreenContentPadding
                ),
            contentAlignment = Alignment.TopCenter
        ) {
            LibraryEmptyStateCard(
                icon = Icons.Rounded.History,
                title = "暂无继续看内容",
                subtitle = "你看过但还没看完的内容会集中显示在这里。",
                actionLabel = actionLabel,
                onAction = onAction,
                modifier = Modifier.padding(top = 56.dp)
            )
        }
        return
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = ContainerTokens.ScreenCompactHorizontalPadding,
                end = ContainerTokens.ScreenCompactHorizontalPadding,
                bottom = ContainerTokens.ScreenContentPadding
            ),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(ContainerTokens.ScreenContentPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                BrowseSummaryCard(
                    title = "继续看",
                    summary = "共 ${entries.size} 项 · 优先恢复最近未完成内容",
                    helper = "点整卡或右侧按钮都可以继续播放。"
                )
            }
            items(entries.sortedByDescending { it.updatedAt }) { entry ->
                ContinueWatchingCard(
                    entry = entry,
                    onClick = { onVideoClick(entry.video.id) }
                )
            }
            item { Spacer(modifier = Modifier.height(ContainerTokens.ScreenBottomPadding)) }
        }
    }
}

@Composable
private fun ContinueWatchingCard(
    entry: WatchProgressEntry,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = MotionTokens.standard()),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cover thumbnail
            Box(
                modifier = Modifier
                    .size(100.dp, 60.dp)
                    .clip(ThumbnailShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                MissNetCoverImage(
                    coverUrl = entry.video.coverUrl,
                    contentDescription = entry.video.title,
                    modifier = Modifier.fillMaxSize()
                )
                entry.video.duration?.let { dur ->
                    DurationBadge(
                        text = dur,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.video.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = buildString {
                        append(entry.video.actors.firstOrNull() ?: "未知演员")
                        append(" · ")
                        append(formatRelativeTime(entry.updatedAt))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                // Progress bar
                LinearProgressIndicator(
                    progress = { entry.progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "已播放 ${formatDuration(entry.positionMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val remainingMs = (entry.durationMs - entry.positionMs).coerceAtLeast(0)
                    Text(
                        text = "剩余 ${formatDuration(remainingMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))
            FilledTonalButton(
                onClick = onClick,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text("继续", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.CHINA, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.CHINA, "%d:%02d", minutes, seconds)
    }
}

private fun formatRelativeTime(timestampMs: Long, nowMs: Long = System.currentTimeMillis()): String {
    if (timestampMs <= 0L) return "刚刚更新"
    val diffMs = (nowMs - timestampMs).coerceAtLeast(0L)
    val minutes = diffMs / 60_000
    val hours = diffMs / 3_600_000
    val days = diffMs / 86_400_000
    return when {
        minutes < 1 -> "刚刚观看"
        minutes < 60 -> "${minutes} 分钟前"
        hours < 24 -> "${hours} 小时前"
        days < 7 -> "${days} 天前"
        else -> "最近一周"
    }
}

@Composable
private fun LibraryEmptyStateCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    MissNetStatePane(
        icon = icon,
        title = title,
        subtitle = subtitle,
        actionLabel = actionLabel,
        onAction = onAction,
        modifier = modifier
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadsPage(
    downloads: List<DownloadStatusEntry>,
    onVideoClick: (String) -> Unit,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onRetry: (DownloadStatusEntry) -> Unit,
    onRemove: (DownloadStatusEntry) -> Unit,
    onExport: (DownloadStatusEntry) -> Unit
) {
    if (downloads.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = ContainerTokens.ScreenCompactHorizontalPadding,
                    vertical = ContainerTokens.ScreenContentPadding
                ),
            contentAlignment = Alignment.TopCenter
        ) {
            LibraryEmptyStateCard(
                icon = Icons.Default.Downloading,
                title = "暂无任务",
                subtitle = "下载、导出和失败恢复会统一显示在这里。",
                actionLabel = actionLabel,
                onAction = onAction,
                modifier = Modifier.padding(top = 56.dp)
            )
        }
        return
    }

    val sortedDownloads = downloads.sortedByDescending { it.updatedAt }
    val activeTasks = sortedDownloads.filter { it.state == Download.STATE_QUEUED || it.state == Download.STATE_DOWNLOADING || it.state == Download.STATE_RESTARTING || it.state == Download.STATE_STOPPED || it.exportState == ExportState.EXPORTING || it.exportState == ExportState.EXPORT_QUEUED }
    val failedTasks = sortedDownloads.filter { it.state == Download.STATE_FAILED || it.exportState == ExportState.EXPORT_FAILED }
    val completedTasks = sortedDownloads.filter { it.state == Download.STATE_COMPLETED && it !in activeTasks && it !in failedTasks }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = ContainerTokens.ScreenCompactHorizontalPadding,
                end = ContainerTokens.ScreenCompactHorizontalPadding,
                bottom = ContainerTokens.ScreenContentPadding
            ),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(ContainerTokens.ScreenContentPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                DownloadOverviewCard(
                    activeCount = activeTasks.size,
                    failedCount = failedTasks.size,
                    completedCount = completedTasks.size
                )
            }

            if (activeTasks.isNotEmpty()) {
                item { DownloadSectionHeader(title = "进行中", subtitle = "优先关注下载、暂停和导出中的项目", count = activeTasks.size) }
                items(activeTasks, key = { it.id }) { item ->
                    DownloadCard(
                        item = item,
                        onClick = { if (item.state == Download.STATE_COMPLETED) onVideoClick(item.id) },
                        onPause = { onPause(item.id) },
                        onResume = { onResume(item.id) },
                        onRetry = { onRetry(item) },
                        onRemove = { onRemove(item) },
                        onExport = { onExport(item) }
                    )
                }
            }

            if (failedTasks.isNotEmpty()) {
                item { DownloadSectionHeader(title = "需要处理", subtitle = "失败项应支持一键恢复", count = failedTasks.size) }
                items(failedTasks, key = { "failed-${it.id}" }) { item ->
                    DownloadCard(
                        item = item,
                        onClick = { if (item.state == Download.STATE_COMPLETED) onVideoClick(item.id) },
                        onPause = { onPause(item.id) },
                        onResume = { onResume(item.id) },
                        onRetry = { onRetry(item) },
                        onRemove = { onRemove(item) },
                        onExport = { onExport(item) }
                    )
                }
            }

            if (completedTasks.isNotEmpty()) {
                item { DownloadSectionHeader(title = "最近完成", subtitle = "可快速打开、导出或清理", count = completedTasks.size) }
                items(completedTasks.take(20), key = { "completed-${it.id}" }) { item ->
                    DownloadCard(
                        item = item,
                        onClick = { onVideoClick(item.id) },
                        onPause = { onPause(item.id) },
                        onResume = { onResume(item.id) },
                        onRetry = { onRetry(item) },
                        onRemove = { onRemove(item) },
                        onExport = { onExport(item) }
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(ContainerTokens.ScreenBottomPadding)) }
        }
    }
}

@Composable
private fun DownloadOverviewCard(
    activeCount: Int,
    failedCount: Int,
    completedCount: Int
) {
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        BrowseSummaryCard(
            title = "任务与资产状态",
            summary = "进行中 $activeCount 项 · 需要处理 $failedCount 项 · 最近完成 $completedCount 项",
            helper = if (failedCount > 0) {
                "建议优先处理需要处理项，避免旧任务长期堆积。"
            } else {
                "优先处理进行中任务，已完成内容可快速打开或导出。"
            },
            modifier = Modifier.padding(ContainerTokens.CardPadding),
            footer = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OverviewChip(icon = Icons.Default.Downloading, label = "进行中 $activeCount")
                    OverviewChip(icon = Icons.Rounded.WarningAmber, label = "需要处理 $failedCount")
                    OverviewChip(icon = Icons.Default.DownloadDone, label = "最近完成 $completedCount")
                }
            }
        )
    }
}

@Composable
private fun OverviewChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(ActionTokens.ChipIconSize)) },
        modifier = Modifier.heightIn(min = ActionTokens.ChipMinHeight)
    )
}

@Composable
private fun DownloadSectionHeader(title: String, subtitle: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = "$count 项",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DownloadCard(
    item: DownloadStatusEntry,
    onClick: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    onExport: () -> Unit
) {
    val clickable = item.state == Download.STATE_COMPLETED && item.id.isNotBlank()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = MotionTokens.standard())
            .then(if (clickable) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(ContainerTokens.CardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                    .size(ContainerTokens.ListItemThumbnailSize)
                    .clip(ThumbnailShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    MissNetCoverImage(
                        coverUrl = item.coverUrl,
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                        emptyLabel = if (item.state == Download.STATE_COMPLETED) "已缓存" else "封面待同步"
                    )
                }

                Spacer(modifier = Modifier.width(ContainerTokens.ScreenContentPadding))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TaskStateBadge(item = item)
                        Text(
                            text = percentText(item),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = progressText(item),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        ExportStateBadge(item = item)
                        if (item.exportLocationText != null && item.exportState == ExportState.EXPORTED) {
                            Text(
                                text = item.exportLocationText ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    item.exportError?.takeIf { it.isNotBlank() }?.let { exportError ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = exportError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(ContainerTokens.ListItemVerticalPadding))
            LinearProgressIndicator(
                progress = { item.normalizedProgress },
                modifier = Modifier.fillMaxWidth(),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(modifier = Modifier.height(ContainerTokens.ListItemVerticalPadding))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(ContainerTokens.ListItemVerticalPadding))
            val context = androidx.compose.ui.platform.LocalContext.current
            DownloadActionRow(
                item = item,
                onPause = onPause,
                onResume = onResume,
                onRetry = onRetry,
                onRemove = onRemove,
                onExport = onExport,
                onOpenExport = if (item.canOpenExport) {
                    {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(Uri.parse(item.exportedUri), item.exportMimeType ?: item.mimeType ?: "*/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(intent)
                        } catch (_: ActivityNotFoundException) {
                            Toast.makeText(context, "系统中没有可打开该导出文件的应用，请到文件管理器查看", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else null
            )
        }
    }
}


@Composable
private fun TaskStateBadge(item: DownloadStatusEntry) {
    val (containerColor, contentColor) = when (item.state) {
        Download.STATE_FAILED -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        Download.STATE_COMPLETED -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        Download.STATE_STOPPED -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    }
    StatusBadge(
        text = item.stateLabel,
        containerColor = containerColor,
        contentColor = contentColor
    )
}

@Composable
private fun ExportStateBadge(item: DownloadStatusEntry) {
    val (label, containerColor, contentColor) = when (item.exportState) {
        ExportState.EXPORT_FAILED -> Triple("导出失败", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        ExportState.EXPORTED -> Triple("已导出", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        ExportState.EXPORTING, ExportState.EXPORT_QUEUED -> Triple("导出中", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        ExportState.EXPORT_UNSUPPORTED -> Triple("不支持", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        ExportState.NOT_EXPORTED -> Triple(
            if (item.state == Download.STATE_COMPLETED) "待导出" else "未导出",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    StatusBadge(
        text = label,
        containerColor = containerColor,
        contentColor = contentColor
    )
}

@Composable
private fun DownloadActionRow(
    item: DownloadStatusEntry,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    onExport: () -> Unit,
    onOpenExport: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ActionTokens.RowSpacing)
    ) {
        when {
            item.canPause -> {
                TaskPrimaryActionButton(
                    icon = Icons.Default.Pause,
                    label = "暂停",
                    onClick = onPause,
                    modifier = Modifier.weight(1f)
                )
                TaskDestructiveActionButton(
                    icon = Icons.Default.Delete,
                    label = "取消",
                    onClick = onRemove,
                    modifier = Modifier.weight(1f)
                )
            }
            item.canResume -> {
                TaskPrimaryActionButton(
                    icon = Icons.Default.PlayArrow,
                    label = "继续",
                    onClick = onResume,
                    modifier = Modifier.weight(1f)
                )
                TaskDestructiveActionButton(
                    icon = Icons.Default.Delete,
                    label = "取消",
                    onClick = onRemove,
                    modifier = Modifier.weight(1f)
                )
            }
            item.state == Download.STATE_FAILED || item.exportState == ExportState.EXPORT_FAILED -> {
                TaskPrimaryActionButton(
                    icon = Icons.Default.Refresh,
                    label = "重试",
                    onClick = onRetry,
                    modifier = Modifier.weight(1f)
                )
                TaskDestructiveActionButton(
                    icon = Icons.Default.Delete,
                    label = "移除",
                    onClick = onRemove,
                    modifier = Modifier.weight(1f)
                )
            }
            item.state == Download.STATE_COMPLETED -> {
                when {
                    onOpenExport != null -> {
                        TaskPrimaryActionButton(
                            icon = Icons.Default.DownloadDone,
                            label = "打开导出",
                            onClick = onOpenExport,
                            modifier = Modifier.weight(1f)
                        )
                        TaskDestructiveActionButton(
                            icon = Icons.Default.Delete,
                            label = "删除本地",
                            onClick = onRemove,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    item.exportState == ExportState.EXPORTING || item.exportState == ExportState.EXPORT_QUEUED -> {
                        TaskSecondaryActionButton(
                            icon = Icons.Default.DownloadDone,
                            label = "导出中",
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.weight(1f)
                        )
                        TaskDestructiveActionButton(
                            icon = Icons.Default.Delete,
                            label = "删除本地",
                            onClick = onRemove,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    item.exportState == ExportState.EXPORT_UNSUPPORTED -> {
                        TaskPrimaryActionButton(
                            icon = Icons.Default.Refresh,
                            label = "重新下载",
                            onClick = onRetry,
                            modifier = Modifier.weight(1f)
                        )
                        TaskDestructiveActionButton(
                            icon = Icons.Default.Delete,
                            label = "删除本地",
                            onClick = onRemove,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    else -> {
                        TaskPrimaryActionButton(
                            icon = Icons.Default.DownloadDone,
                            label = if (item.exportState == ExportState.EXPORT_FAILED) "重试导出" else "导出视频",
                            onClick = onExport,
                            modifier = Modifier.weight(1f)
                        )
                        TaskDestructiveActionButton(
                            icon = Icons.Default.Delete,
                            label = "删除本地",
                            onClick = onRemove,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            else -> {
                TaskDestructiveActionButton(
                    icon = Icons.Default.Delete,
                    label = if (item.canRemove) "移除任务" else "处理中",
                    onClick = onRemove,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = item.canRemove
                )
            }
        }
    }
}

@Composable
private fun TaskPrimaryActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        contentPadding = PaddingValues(
            horizontal = ActionTokens.ButtonContentPaddingHorizontal,
            vertical = ActionTokens.ButtonContentPaddingVertical
        )
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(ActionTokens.ButtonIconSize))
        Spacer(modifier = Modifier.width(ActionTokens.ButtonContentGap))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun TaskSecondaryActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        contentPadding = PaddingValues(
            horizontal = ActionTokens.ButtonContentPaddingHorizontal,
            vertical = ActionTokens.ButtonContentPaddingVertical
        )
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(ActionTokens.ButtonIconSize))
        Spacer(modifier = Modifier.width(ActionTokens.ButtonContentGap))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun TaskDestructiveActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        ),
        contentPadding = PaddingValues(
            horizontal = ActionTokens.ButtonContentPaddingHorizontal,
            vertical = ActionTokens.ButtonContentPaddingVertical
        )
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(ActionTokens.ButtonIconSize))
        Spacer(modifier = Modifier.width(ActionTokens.ButtonContentGap))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

private fun percentText(item: DownloadStatusEntry): String {
    if (item.state == Download.STATE_COMPLETED) return "100%"
    if (item.state == Download.STATE_FAILED && item.progressPercent <= 0f) return "失败"
    return String.format(Locale.US, "%.0f%%", item.progressPercent.coerceIn(0f, 100f))
}

private fun progressText(item: DownloadStatusEntry): String {
    val bytes = formatBytes(item.bytesDownloaded)
    val total = if (item.contentLength > 0) formatBytes(item.contentLength) else "--"
    return when (item.state) {
        Download.STATE_COMPLETED -> when (item.exportState) {
            ExportState.EXPORTED -> "已下载并导出视频 · $bytes"
            ExportState.EXPORT_UNSUPPORTED -> "已下载完成 · 当前资源仅支持应用内离线或暂不支持导出为单个视频文件"
            else -> "已下载完成 · $bytes"
        }
        Download.STATE_FAILED -> "下载失败 · 已下载 $bytes / $total"
        Download.STATE_STOPPED -> "已暂停 · 已下载 $bytes / $total"
        Download.STATE_QUEUED -> "等待开始 · 已下载 $bytes / $total"
        Download.STATE_REMOVING -> "正在移除缓存和记录"
        else -> "$bytes / $total"
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    return when {
        bytes >= gb -> String.format(Locale.US, "%.2f GB", bytes / gb)
        bytes >= mb -> String.format(Locale.US, "%.1f MB", bytes / mb)
        bytes >= kb -> String.format(Locale.US, "%.1f KB", bytes / kb)
        else -> "$bytes B"
    }
}
