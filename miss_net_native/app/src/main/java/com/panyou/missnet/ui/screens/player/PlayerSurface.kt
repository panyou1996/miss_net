@file:androidx.media3.common.util.UnstableApi

package com.panyou.missnet.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.panyou.missnet.ui.components.MissNetCoverImage
import com.panyou.missnet.ui.theme.MotionTokens
import com.panyou.missnet.ui.theme.mediaScrim
import kotlinx.coroutines.delay
import android.view.ViewGroup
import android.widget.FrameLayout

@Composable
fun PlayerPlaybackSurface(
    player: Player?,
    coverUrl: String?,
    title: String?,
    showPosterArtwork: Boolean,
    isBuffering: Boolean,
    isLoadingStream: Boolean,
    bufferedProgress: Float?,
    errorMessage: String?,
    showControls: Boolean,
    onToggleControls: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onRetry: () -> Unit,
    controls: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    var seekFeedback by remember { mutableStateOf<DoubleTapSeekAction?>(null) }

    // U-6: Fade-out cover image when video starts playing
    val coverAlpha by animateFloatAsState(
        targetValue = if (showPosterArtwork) 1f else 0f,
        animationSpec = tween(durationMillis = 350),
        label = "cover-fade"
    )

    LaunchedEffect(seekFeedback) {
        if (seekFeedback != null) {
            delay(650)
            seekFeedback = null
        }
    }

    Box(modifier = modifier) {
        if (coverAlpha > 0f) {
            Box(modifier = Modifier.fillMaxSize()) {
                MissNetCoverImage(
                    coverUrl = coverUrl,
                    contentDescription = title,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(coverAlpha)
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(mediaScrim(alpha = 0.28f * coverAlpha))
                )
            }
        }

        PlayerContainer(player)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(showControls) {
                    detectTapGestures(
                        onTap = { onToggleControls() },
                        onDoubleTap = { offset ->
                            when (resolveDoubleTapSeekAction(offset.x, size.width.toFloat())) {
                                DoubleTapSeekAction.Backward -> {
                                    seekFeedback = DoubleTapSeekAction.Backward
                                    onSeekBack()
                                }

                                DoubleTapSeekAction.Forward -> {
                                    seekFeedback = DoubleTapSeekAction.Forward
                                    onSeekForward()
                                }

                                null -> Unit
                            }
                        }
                    )
                }
        )

        if (isBuffering || isLoadingStream) {
            PlayerLoadingOverlay(
                title = if (isLoadingStream) "正在加载播放源" else "正在缓冲",
                subtitle = if (isLoadingStream) "即将进入播放" else "网络波动时会自动恢复",
                progress = bufferedProgress,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        if (errorMessage != null) {
            PlayerErrorOverlay(
                message = errorMessage,
                onRetry = onRetry,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        AnimatedVisibility(
            visible = seekFeedback != null,
            enter = fadeIn(animationSpec = MotionTokens.standard(MotionTokens.DurationShort3)),
            exit = fadeOut(animationSpec = MotionTokens.exit(MotionTokens.DurationShort2)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                color = mediaScrim(alpha = 0.64f),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = if (seekFeedback == DoubleTapSeekAction.Backward) "后退 10 秒" else "前进 10 秒",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        controls()
    }
}

@Composable
fun PlayerLoadingState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        PlayerLoadingOverlay(
            title = title,
            subtitle = subtitle
        )
    }
}

@Composable
fun PlayerContainer(player: Player?) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = false
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
        },
        update = { view -> view.player = player },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun PlayerLoadingOverlay(
    title: String,
    subtitle: String,
    progress: Float? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.animateContentSize(animationSpec = MotionTokens.standard()),
        shape = MaterialTheme.shapes.large,
        color = mediaScrim(alpha = 0.56f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(36.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.78f)
            )
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color.Transparent, CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.18f)
                )
                Text(
                    text = "已缓存 ${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.74f)
                )
            }
        }
    }
}

@Composable
fun PlayerErrorOverlay(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .padding(24.dp)
            .fillMaxWidth(0.88f),
        shape = MaterialTheme.shapes.large,
        color = mediaScrim(alpha = 0.82f)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "播放失败",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                color = Color.White.copy(alpha = 0.88f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                androidx.compose.material3.Icon(Icons.Default.Refresh, contentDescription = null)
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(8.dp))
                Text("重试")
            }
        }
    }
}
