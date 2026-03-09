package com.panyou.missnet.nativeapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.panyou.missnet.nativeapp.core.model.Video
import com.panyou.missnet.nativeapp.ui.theme.InkBlack
import com.panyou.missnet.nativeapp.ui.theme.Signal
import com.panyou.missnet.nativeapp.ui.theme.SurfaceRaised

@Composable
fun VideoCard(
    video: Video,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .clickable(onClick = onClick),
        color = SurfaceRaised.copy(alpha = 0.95f),
        tonalElevation = 0.dp,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(178.dp),
            ) {
                AsyncImage(
                    model = video.coverUrl,
                    contentDescription = video.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    InkBlack.copy(alpha = 0.05f),
                                    InkBlack.copy(alpha = 0.15f),
                                    InkBlack.copy(alpha = 0.82f),
                                ),
                            ),
                        ),
                )
                if (video.isOfflineReady) {
                    OverlineLabel(
                        text = "offline",
                    )
                }
            }
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = buildString {
                    if (video.duration != null) append(video.duration)
                    if (video.releaseDate != null && video.releaseDate != "Unknown") {
                        if (isNotBlank()) append(" • ")
                        append(video.releaseDate)
                    }
                }
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                    )
                }
                if (video.progress > 0f) {
                    LinearProgressIndicator(
                        progress = { video.progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = Signal,
                        trackColor = Signal.copy(alpha = 0.15f),
                    )
                }
            }
        }
    }
}

@Composable
fun CompactVideoCard(
    video: Video,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .width(220.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
        color = SurfaceRaised.copy(alpha = 0.9f),
    ) {
        Column {
            AsyncImage(
                model = video.coverUrl,
                contentDescription = video.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp),
                contentScale = ContentScale.Crop,
            )
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (video.progress > 0f) {
                    LinearProgressIndicator(
                        progress = { video.progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = Signal,
                        trackColor = Signal.copy(alpha = 0.15f),
                    )
                }
            }
        }
    }
}
