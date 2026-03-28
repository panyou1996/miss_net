package com.panyou.missnet.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.panyou.missnet.ui.theme.MotionTokens

/** U-8: Loading state with scenario-specific copy */
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

@Composable
fun MissNetLoading(
    scenario: LoadingScenario = LoadingScenario.UNKNOWN,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
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

/** U-9: Error state with error-type-specific copy */
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

@Composable
fun MissNetErrorState(
    errorType: ErrorType = ErrorType.UNKNOWN,
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

// ── 以下为原有组件（保留向下兼容）──────────────────────────────────

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
