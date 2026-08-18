@file:androidx.media3.common.util.UnstableApi

package com.panyou.missnet.ui.screens.player

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.panyou.missnet.data.model.Video
import com.panyou.missnet.data.model.VideoEpisode
import com.panyou.missnet.ui.components.DurationBadge
import com.panyou.missnet.ui.components.MissNetCoverImage
import com.panyou.missnet.ui.theme.ActionTokens
import com.panyou.missnet.ui.theme.MotionTokens
import com.panyou.missnet.ui.theme.ThumbnailShape

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EpisodeSelectorSection(
    currentVideoId: String,
    videoCount: Int,
    episodes: List<VideoEpisode>,
    onEpisodeSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (videoCount <= 1 && episodes.size <= 1) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "选集 (${episodes.size.coerceAtLeast(videoCount)})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val list = if (episodes.isNotEmpty()) {
                episodes
            } else {
                (1..videoCount).map { idx ->
                    val epId = if (idx == 1) currentVideoId else "${currentVideoId}_$idx"
                    VideoEpisode(index = idx, id = epId, title = "第 $idx 集")
                }
            }
            list.forEach { ep ->
                val isSelected = ep.id == currentVideoId || (ep.index == 1 && !currentVideoId.contains(Regex("""_\d+$""")))
                AssistChip(
                    onClick = { onEpisodeSelect(ep.id) },
                    label = {
                        Text(
                            text = ep.title.ifBlank { "第 ${ep.index} 集" },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = if (isSelected) {
                        AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } else {
                        AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun RecommendSectionHeader(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "相关推荐",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "点击后切换当前播放内容",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoInfoSection(
    title: String,
    primaryDate: String?,
    lastPositionMs: Long,
    tags: List<String>,
    actors: List<String>,
    onTagClick: (String) -> Unit,
    onActorClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedMeta by remember(title, primaryDate, tags, actors) { mutableStateOf(false) }
    val safeActors = remember(actors) { actors.filter { it.isNotBlank() }.distinct() }
    val safeTags = remember(tags) { tags.filter { it.isNotBlank() }.distinct() }
    val collapsedActors = if (expandedMeta) safeActors else safeActors.take(2)
    val collapsedTags = if (expandedMeta) safeTags else safeTags.take(4)
    val remainingMetaCount = (safeActors.size - collapsedActors.size).coerceAtLeast(0) +
        (safeTags.size - collapsedTags.size).coerceAtLeast(0)

    Column(
        modifier = modifier.animateContentSize(animationSpec = MotionTokens.standard()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            InfoMetaChip(
                icon = Icons.Default.CalendarToday,
                text = "发布于 ${primaryDate ?: "最近"}"
            )
            if (lastPositionMs > 0L) {
                InfoMetaChip(
                    icon = Icons.Default.History,
                    text = "上次看到 ${formatTime(lastPositionMs)}"
                )
            }
        }

        if (safeActors.isNotEmpty() || safeTags.isNotEmpty()) {
            Text(
                text = buildString {
                    if (safeActors.isNotEmpty()) append("${safeActors.size} 位演员")
                    if (safeTags.isNotEmpty()) {
                        if (isNotEmpty()) append(" · ")
                        append("${safeTags.size} 个标签")
                    }
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (collapsedActors.isNotEmpty() || collapsedTags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                collapsedActors.forEach { actor ->
                    AssistChip(
                        onClick = { onActorClick(actor) },
                        label = { Text(text = actor, style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        modifier = Modifier.heightIn(min = 30.dp),
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            leadingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    )
                }

                collapsedTags.forEach { tag ->
                    AssistChip(
                        onClick = { onTagClick(tag) },
                        label = { Text(text = "#$tag", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.heightIn(min = 30.dp)
                    )
                }

                if (remainingMetaCount > 0) {
                    AssistChip(
                        onClick = { expandedMeta = !expandedMeta },
                        label = {
                            Text(
                                text = if (expandedMeta) "收起" else "+$remainingMetaCount",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.heightIn(min = 30.dp),
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoMetaChip(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun PrimaryActionsRow(
    onDownload: () -> Unit,
    onFavorite: () -> Unit,
    isFavorite: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ActionTokens.RowSpacing)
    ) {
        Button(
            onClick = onDownload,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                horizontal = ActionTokens.ButtonContentPaddingHorizontal,
                vertical = 8.dp
            )
        ) {
            Icon(
                Icons.Default.CloudDownload,
                contentDescription = null,
                modifier = Modifier.size(ActionTokens.ButtonIconSize)
            )
            Spacer(modifier = Modifier.width(ActionTokens.ButtonContentGap))
            Text("下载", style = MaterialTheme.typography.labelLarge)
        }

        FilledTonalButton(
            onClick = onFavorite,
            modifier = Modifier.weight(1f),
            colors = if (isFavorite) {
                ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            } else {
                ButtonDefaults.filledTonalButtonColors()
            },
            contentPadding = PaddingValues(
                horizontal = ActionTokens.ButtonContentPaddingHorizontal,
                vertical = 8.dp
            )
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = null,
                modifier = Modifier.size(ActionTokens.ButtonIconSize)
            )
            Spacer(modifier = Modifier.width(ActionTokens.ButtonContentGap))
            Text(if (isFavorite) "已收藏" else "收藏", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SecondaryActionsRow(
    onShare: () -> Unit,
    onSpeed: () -> Unit,
    onCast: () -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ActionTokens.RowSpacing),
        verticalArrangement = Arrangement.spacedBy(ActionTokens.RowSpacing)
    ) {
        SecondaryActionChip(
            icon = Icons.Default.Share,
            label = "分享",
            onClick = onShare
        )
        SecondaryActionChip(
            icon = Icons.Default.Speed,
            label = "速度",
            onClick = onSpeed
        )
        SecondaryActionChip(
            icon = Icons.Default.Tv,
            label = "投屏",
            onClick = onCast
        )
    }
}

@Composable
private fun SecondaryActionChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    AssistChip(
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(ActionTokens.ChipIconSize)
            )
        },
        modifier = Modifier.heightIn(min = 32.dp),
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            leadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

@Composable
fun RecommendItem(video: Video, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = MotionTokens.standard()),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(112.dp, 64.dp)
                    .clip(ThumbnailShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                MissNetCoverImage(
                    coverUrl = video.coverUrl,
                    contentDescription = video.title,
                    modifier = Modifier.fillMaxSize()
                )
                video.displayDurationOrNull?.let { dur ->
                    DurationBadge(
                        text = dur,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = buildString {
                        append(video.primaryActorOrNull ?: video.metadataStatusLabel)
                        video.displayDate?.takeIf { it.isNotBlank() }?.let {
                            append(" · ")
                            append(it)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = video.tags.take(2).joinToString(" · ").ifBlank { video.metadataStatusLabel },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "切换",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
