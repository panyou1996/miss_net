package com.panyou.missnet.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.panyou.missnet.ui.theme.MotionTokens

@Composable
fun MissNetLoading(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    title: String = "正在整理页面内容",
    subtitle: String = "请稍候，当前页面正在同步最新状态。"
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.animateContentSize(animationSpec = MotionTokens.standard()),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f))
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(44.dp),
                    color = color,
                    strokeWidth = 4.dp,
                    trackColor = color.copy(alpha = 0.1f)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun MissNetErrorState(
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    title: String = "暂时无法显示内容",
    actionLabel: String = "重新加载"
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        MissNetStateCard(
            icon = Icons.Outlined.CloudOff,
            title = title,
            subtitle = message,
            actionLabel = if (onRetry != null) actionLabel else null,
            onAction = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        )
    }
}

@Composable
fun MissNetStateCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.animateContentSize(animationSpec = MotionTokens.standard()),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                FilledTonalButton(onClick = onAction) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun MissNetStatePane(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = MotionTokens.standard()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MissNetStateCard(
            icon = icon,
            title = title,
            subtitle = subtitle,
            modifier = Modifier.fillMaxWidth()
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onAction,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(actionLabel)
            }
        }
    }
}



/**
 * Unified empty state for when there's no content to display.
 * Replaces scattered empty state implementations across screens.
 */
@Composable
fun MissNetEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    MissNetStateCard(
        icon = icon,
        title = title,
        subtitle = subtitle ?: "",
        actionLabel = actionLabel,
        onAction = onAction,
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * Convenience function for common empty states.
 */
@Composable
fun MissNetEmptyStateNoResults(
    keyword: String? = null,
    modifier: Modifier = Modifier
) {
    MissNetEmptyState(
        icon = Icons.Outlined.SearchOff,
        title = "暂未找到匹配内容",
        subtitle = keyword?.let { "未找到与 \"$it\" 相关的内容，请尝试更短的标题关键词。" } ?: "当前暂无可展示的内容，请稍后再试。",
        modifier = modifier
    )
}

@Composable
fun MissNetEmptyStateNoDownloads(
    modifier: Modifier = Modifier
) {
    MissNetEmptyState(
        icon = Icons.Default.CloudDownload,
        title = "暂无任务",
        subtitle = "下载、导出与失败恢复会统一显示在这里。",
        modifier = modifier
    )
}

@Composable
fun MissNetEmptyStateNoFavorites(
    modifier: Modifier = Modifier
) {
    MissNetEmptyState(
        icon = Icons.Default.FavoriteBorder,
        title = "暂无收藏",
        subtitle = "你收藏的内容会集中显示在这里。",
        modifier = modifier
    )
}

@Composable
fun MediaPlaceholder(
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.PlayCircle,
    label: String? = null
) {
    val transition = rememberInfiniteTransition(label = "media-placeholder")
    val pulseAlpha = transition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.36f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "media-placeholder-alpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .alpha(pulseAlpha.value)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                modifier = Modifier.size(40.dp)
            )
            label?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                )
            }
        }
    }
}

// ── U-8: LoadingScenario enum with scenario-specific copy ──────────────────────

/** U-8: Loading state with scenario-specific title/subtitle */
enum class LoadingScenario(
    val title: String,
    val subtitle: String
) {
    HOME("正在整理页面内容", "请稍候，当前页面正在同步最新状态。"),
    SEARCH("正在搜索", "正在从海量内容中筛选..."),
    LIBRARY("正在加载资源库", "资源整理中，请稍候。"),
    ACTOR("正在加载演员页", "演员信息加载中，请稍候。"),
    PLAYER("正在加载视频", "视频解析中，请稍候。"),
    UNKNOWN("正在加载", "请稍候...")
}

/** U-8: MissNetLoading overload with LoadingScenario — preserves original for compat */
@Composable
fun MissNetLoading(
    scenario: LoadingScenario,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.animateContentSize(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f))
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(44.dp),
                    color = color,
                    strokeWidth = 4.dp,
                    trackColor = color.copy(alpha = 0.1f)
                )
                Text(
                    text = scenario.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = scenario.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ── U-9: ErrorType enum with error-type-specific copy ────────────────────────

/** U-9: Error state with error-type-specific title/subtitle/retryLabel */
enum class ErrorType(
    val title: String,
    val subtitle: String,
    val retryLabel: String = "重新加载"
) {
    NETWORK("网络连接失败", "请检查网络后点击重试。"),
    SERVER("服务器异常", "服务端开小差了，请稍后再试。"),
    TIMEOUT("请求超时", "加载超时，请重试。"),
    NOT_FOUND("内容不存在", "该内容已被删除或无法访问。"),
    UNKNOWN("暂时无法显示内容", "遇到未知错误，请稍后重试。")
}

/** U-9: MissNetErrorState overload with ErrorType — preserves original for compat */
@Composable
fun MissNetErrorState(
    errorType: ErrorType,
    message: String? = null,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        MissNetStateCard(
            icon = Icons.Outlined.CloudOff,
            title = errorType.title,
            subtitle = message ?: errorType.subtitle,
            actionLabel = if (onRetry != null) errorType.retryLabel else null,
            onAction = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        )
    }
}
