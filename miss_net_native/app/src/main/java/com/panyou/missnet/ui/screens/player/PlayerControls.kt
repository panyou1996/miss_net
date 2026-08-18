@file:androidx.media3.common.util.UnstableApi

package com.panyou.missnet.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.panyou.missnet.ui.theme.MotionTokens
import com.panyou.missnet.ui.theme.mediaScrim

@Composable
fun PlayerControls(
    showControls: Boolean,
    isFullscreen: Boolean,
    isPlaying: Boolean,
    currentPos: Long,
    duration: Long,
    onTogglePlay: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleFullscreen: () -> Unit,
    onBack: () -> Unit,
    onSpeed: () -> Unit
) {
    var dragValue by remember(duration) { mutableStateOf<Float?>(null) }
    val displayedPosition = (dragValue?.toLong() ?: currentPos).coerceAtLeast(0L)

    AnimatedVisibility(
        visible = showControls,
        enter = fadeIn(animationSpec = MotionTokens.standard(MotionTokens.DurationShort4)) +
            scaleIn(initialScale = 0.98f, animationSpec = MotionTokens.standard(MotionTokens.DurationShort4)),
        exit = fadeOut(animationSpec = MotionTokens.exit(MotionTokens.DurationShort3)) +
            scaleOut(targetScale = 0.98f, animationSpec = MotionTokens.exit(MotionTokens.DurationShort3))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(mediaScrim(alpha = 0.42f))
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(
                        top = if (isFullscreen) 20.dp else 8.dp,
                        start = 10.dp,
                        end = 10.dp
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OverlayControlButton(
                    onClick = onBack,
                    icon = if (isFullscreen) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = if (isFullscreen) "退出全屏" else "返回",
                    iconSize = 20.dp,
                    buttonSize = 36.dp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OverlayControlButton(
                        onClick = onSpeed,
                        icon = Icons.Default.Speed,
                        contentDescription = "倍速",
                        iconSize = 20.dp,
                        buttonSize = 36.dp
                    )
                }
            }

            // Center Control Buttons - Compact & Non-intrusive
            Row(
                modifier = Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (isFullscreen) 32.dp else 20.dp)
            ) {
                OverlayControlButton(
                    onClick = onSeekBack,
                    icon = Icons.Default.Replay10,
                    contentDescription = "后退10秒",
                    iconSize = 20.dp,
                    buttonSize = 38.dp
                )
                CenterPlayPauseButton(
                    isPlaying = isPlaying,
                    isFullscreen = isFullscreen,
                    onClick = onTogglePlay
                )
                OverlayControlButton(
                    onClick = onSeekForward,
                    icon = Icons.Default.Forward10,
                    contentDescription = "前进10秒",
                    iconSize = 20.dp,
                    buttonSize = 38.dp
                )
            }

            // Bottom Bar & Slider - Positioned cleanly at the bottom edge
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(
                        bottom = if (isFullscreen) 16.dp else 4.dp,
                        start = 10.dp,
                        end = 10.dp
                    )
            ) {
                AnimatedVisibility(
                    visible = dragValue != null,
                    enter = fadeIn(animationSpec = MotionTokens.standard(MotionTokens.DurationShort3)) +
                        slideInVertically(
                            initialOffsetY = { it / 3 },
                            animationSpec = MotionTokens.standard(MotionTokens.DurationShort3)
                        ),
                    exit = fadeOut(animationSpec = MotionTokens.exit(MotionTokens.DurationShort2)) +
                        slideOutVertically(
                            targetOffsetY = { it / 3 },
                            animationSpec = MotionTokens.exit(MotionTokens.DurationShort2)
                        )
                ) {
                    Surface(
                        color = mediaScrim(alpha = 0.65f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(
                            text = "定位到 ${formatTime(displayedPosition)}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${formatTime(displayedPosition)} / ${formatTime(duration)}",
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    Slider(
                        value = dragValue ?: currentPos.toFloat(),
                        onValueChange = { dragValue = it },
                        onValueChangeFinished = {
                            dragValue?.let { onSeekTo(it.toLong()) }
                            dragValue = null
                        },
                        valueRange = 0f..(duration.toFloat().coerceAtLeast(1f)),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.28f),
                            disabledThumbColor = Color.White.copy(alpha = 0.5f),
                            disabledActiveTrackColor = Color.White.copy(alpha = 0.5f),
                            disabledInactiveTrackColor = Color.White.copy(alpha = 0.2f)
                        )
                    )
                    OverlayControlButton(
                        onClick = onToggleFullscreen,
                        icon = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = if (isFullscreen) "退出全屏" else "进入全屏",
                        iconSize = 18.dp,
                        buttonSize = 34.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun OverlayControlButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    iconSize: Dp = 20.dp,
    buttonSize: Dp = 38.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1f,
        animationSpec = MotionTokens.standard(MotionTokens.DurationShort3),
        label = "overlay-control-scale"
    )
    val backgroundAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.56f else 0.38f,
        animationSpec = MotionTokens.standard(MotionTokens.DurationShort3),
        label = "overlay-control-bg"
    )

    Box(
        modifier = Modifier
            .size(buttonSize)
            .scale(scale)
            .clip(CircleShape)
            .background(mediaScrim(alpha = backgroundAlpha))
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
private fun CenterPlayPauseButton(
    isPlaying: Boolean,
    isFullscreen: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1f,
        animationSpec = MotionTokens.standard(MotionTokens.DurationShort3),
        label = "play-pause-scale"
    )

    Box(
        modifier = Modifier
            .size(if (isFullscreen) 56.dp else 48.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(mediaScrim(alpha = 0.42f))
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Crossfade(
            targetState = isPlaying,
            animationSpec = MotionTokens.standard(MotionTokens.DurationShort4),
            label = "play-pause-icon"
        ) { playing ->
            Icon(
                imageVector = if (playing) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                contentDescription = if (playing) "暂停" else "播放",
                tint = Color.White,
                modifier = Modifier.size(if (isFullscreen) 34.dp else 30.dp)
            )
        }
    }
}
