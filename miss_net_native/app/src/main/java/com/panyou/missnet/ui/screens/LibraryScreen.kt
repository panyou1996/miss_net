@file:androidx.media3.common.util.UnstableApi

package com.panyou.missnet.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
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
import coil.compose.AsyncImage
import com.panyou.missnet.data.media.DownloadStatusEntry
import com.panyou.missnet.data.media.ExportState
import com.panyou.missnet.data.model.Video
import com.panyou.missnet.ui.components.MissNetStateCard
import com.panyou.missnet.ui.components.MissNetLoading
import com.panyou.missnet.ui.components.VideoCard
import com.panyou.missnet.ui.components.DurationBadge
import com.panyou.missnet.ui.components.StatusBadge
import com.panyou.missnet.ui.theme.ContainerTokens
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
                when (LibraryTab.entries[selectedTab]) {
                    LibraryTab.Downloads -> DownloadsPage(
                        downloads = uiState.downloads,
                        onVideoClick = onVideoClick,
                        onPause = viewModel::pauseDownload,
                        onResume = viewModel::resumeDownload,
                        onRetry = viewModel::retryDownload,
                        onRemove = viewModel::removeDownload,
                        onExport = viewModel::exportDownload
                    )
                    LibraryTab.History -> VideoGridPage(
                        title = "继续观看",
                        emptyTitle = "暂无继续观看内容",
                        emptySubtitle = "你看过但还没看完的内容会显示在这里",
                        icon = Icons.Rounded.History,
                        isLoading = uiState.isLoading,
                        videos = uiState.history,
                        historyProgress = uiState.historyProgress,
                        onVideoClick = onVideoClick,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope
                    )
                    LibraryTab.Likes -> VideoGridPage(
                        title = "收藏",
                        emptyTitle = "暂无收藏",
                        emptySubtitle = "你收藏的内容会出现在这里",
                        icon = Icons.Rounded.Favorite,
                        isLoading = uiState.isLoading,
                        videos = uiState.likes,
                        historyProgress = emptyMap(),
                        onVideoClick = onVideoClick,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope
                    )
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
    animatedVisibilityScope: AnimatedVisibilityScope?
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
            contentAlignment = Alignment.Center
        ) {
            LibraryEmptyStateCard(
                icon = icon,
                title = emptyTitle,
                subtitle = emptySubtitle
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(ContainerTokens.ScreenContentPadding),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("${videos.size} 项", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(ContainerTokens.GridColumns),
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

@Composable
private fun LibraryEmptyStateCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    MissNetStateCard(
        icon = icon,
        title = title,
        subtitle = subtitle,
        modifier = Modifier.fillMaxWidth()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadsPage(
    downloads: List<DownloadStatusEntry>,
    onVideoClick: (String) -> Unit,
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
            contentAlignment = Alignment.Center
        ) {
            LibraryEmptyStateCard(
                icon = Icons.Default.Downloading,
                title = "暂无任务",
                subtitle = "下载、导出和失败恢复会集中显示在这里"
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
            verticalArrangement = Arrangement.spacedBy(ContainerTokens.SectionVerticalSpacing)
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
        Column(
            modifier = Modifier.padding(ContainerTokens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(ContainerTokens.ListItemVerticalPadding)
        ) {
            Text("任务与资产状态中心", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "优先处理正在进行的任务、失败恢复，以及已完成资产的快速访问。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OverviewChip(icon = Icons.Default.Downloading, label = "进行中 $activeCount")
                OverviewChip(icon = Icons.Rounded.WarningAmber, label = "需要处理 $failedCount")
                OverviewChip(icon = Icons.Default.DownloadDone, label = "最近完成 $completedCount")
            }
            if (failedCount > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = "建议优先处理需要处理项，避免旧任务长期堆积。",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun OverviewChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) }
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
                    if (!item.coverUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = item.coverUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = if (item.state == Download.STATE_COMPLETED) Icons.Default.DownloadDone else Icons.Default.Downloading,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp)
                        )
                    }
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
        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
        modifier = modifier
    ) {
        Icon(icon, contentDescription = null)
        Spacer(modifier = Modifier.width(6.dp))
        Text(label)
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
        modifier = modifier
    ) {
        Icon(icon, contentDescription = null)
        Spacer(modifier = Modifier.width(6.dp))
        Text(label)
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
        )
    ) {
        Icon(icon, contentDescription = null)
        Spacer(modifier = Modifier.width(6.dp))
        Text(label)
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
