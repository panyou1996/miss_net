package com.panyou.missnet.nativeapp.ui.screens

import android.app.Activity
import android.app.PictureInPictureParams
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.panyou.missnet.nativeapp.core.media.MediaDownloadCoordinator
import com.panyou.missnet.nativeapp.core.media.MediaHeaderStore
import com.panyou.missnet.nativeapp.core.model.DownloadStatus
import com.panyou.missnet.nativeapp.core.model.Video
import com.panyou.missnet.nativeapp.core.util.toTimeLabel
import com.panyou.missnet.nativeapp.ui.components.CompactVideoCard
import com.panyou.missnet.nativeapp.ui.components.EmptyState
import com.panyou.missnet.nativeapp.ui.components.GlassCard
import com.panyou.missnet.nativeapp.ui.components.HeaderRow
import com.panyou.missnet.nativeapp.ui.components.LoadingState
import com.panyou.missnet.nativeapp.ui.components.OverlineLabel
import com.panyou.missnet.nativeapp.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    activity: Activity,
    viewModel: PlayerViewModel,
    video: Video,
    downloadCoordinator: MediaDownloadCoordinator,
    mediaHeaderStore: MediaHeaderStore,
    onBack: () -> Unit,
    onOpenVideo: (Video) -> Unit,
    onOpenFeed: (title: String, category: String?, actor: String?) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(video.id) {
        viewModel.bind(video)
    }

    DisposableEffect(state.keepScreenAwake) {
        if (state.keepScreenAwake) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val mediaItem = remember(state.download, state.streamInfo) {
        when {
            state.download?.status == DownloadStatus.Completed -> downloadCoordinator.buildOfflineMediaItem(state.download!!)
            state.streamInfo != null -> {
                mediaHeaderStore.update(state.streamInfo!!.headers)
                downloadCoordinator.buildOnlineMediaItem(state.streamInfo!!.streamUrl, state.streamInfo!!.mimeType)
            }
            else -> null
        }
    }

    val dataSourceFactory = remember(state.download, state.streamInfo) {
        when {
            state.download?.status == DownloadStatus.Completed -> downloadCoordinator.buildOfflineDataSourceFactory()
            state.streamInfo != null -> downloadCoordinator.buildOnlineDataSourceFactory()
            else -> null
        }
    }

    val player = remember(mediaItem, dataSourceFactory) {
        if (mediaItem == null || dataSourceFactory == null) {
            null
        } else {
            ExoPlayer.Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
                .build()
                .apply {
                    setMediaItem(mediaItem)
                    prepare()
                    playWhenReady = true
                    if (video.lastPositionMs > 0L) {
                        seekTo(video.lastPositionMs)
                    }
                }
        }
    }

    DisposableEffect(player, state.autoplayRelated, state.related) {
        if (player == null) {
            onDispose {}
        } else {
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (
                        playbackState == Player.STATE_ENDED &&
                        state.autoplayRelated &&
                        state.related.isNotEmpty()
                    ) {
                        onOpenVideo(state.related.first())
                    }
                }
            }
            player.addListener(listener)
            onDispose {
                player.removeListener(listener)
                val duration = player.duration.takeIf { it > 0L } ?: 0L
                viewModel.saveProgress(player.currentPosition, duration)
                player.release()
            }
        }
    }

    LaunchedEffect(player) {
        while (player != null) {
            delay(5_000L)
            val duration = player.duration.takeIf { it > 0L } ?: 0L
            viewModel.saveProgress(player.currentPosition, duration)
        }
    }

    if (state.isResolving && mediaItem == null) {
        LoadingState()
        return
    }

    if (state.error != null && mediaItem == null) {
        EmptyState(title = "Player unavailable", body = state.error ?: "Unknown playback error")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            HeaderRow(
                title = "Player",
                subtitle = if (state.download?.status == DownloadStatus.Completed) "Offline playback ready" else "Resolved live stream playback",
                actionIcon = Icons.AutoMirrored.Rounded.ArrowBack,
                onActionClick = onBack,
            )
        }

        item {
            if (player == null) {
                EmptyState(title = "Preparing player", body = "Resolving the best source for this video.")
            } else {
                AndroidView(
                    factory = { viewContext ->
                        PlayerView(viewContext).apply {
                            useController = true
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            this.player = player
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    update = { it.player = player },
                )
            }
        }

        item {
            GlassCard {
                Text(video.title, style = MaterialTheme.typography.headlineSmall)
                val meta = listOfNotNull(
                    video.duration,
                    video.releaseDate?.takeIf { it != "Unknown" },
                    state.download?.takeIf { it.status == DownloadStatus.Completed }?.let { "Offline ready" },
                ).joinToString(" • ")
                if (meta.isNotBlank()) {
                    Text(
                        meta,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IconButton(onClick = viewModel::toggleFavorite) {
                        Icon(
                            imageVector = if (state.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = "Favorite",
                        )
                    }
                    IconButton(
                        onClick = viewModel::download,
                        enabled = state.download?.status != DownloadStatus.Downloading && state.download?.status != DownloadStatus.Completed,
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = "Download")
                    }
                    IconButton(
                        onClick = {
                            activity.enterPictureInPictureMode(PictureInPictureParams.Builder().build())
                        },
                    ) {
                        Icon(Icons.Rounded.PictureInPictureAlt, contentDescription = "Picture in picture")
                    }
                }
                if (state.error != null) {
                    Text(
                        text = state.error ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (state.download != null) {
                    Text(
                        text = when (state.download?.status) {
                            DownloadStatus.Completed -> "Stored for offline playback"
                            DownloadStatus.Downloading -> "Downloading ${state.download?.progressPercent?.toInt() ?: 0}%"
                            DownloadStatus.Failed -> state.download?.failureReason ?: "Download failed"
                            else -> "Download queued"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                    )
                }
            }
        }

        if (video.actors.isNotEmpty()) {
            item {
                GlassCard {
                    OverlineLabel("actors")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(video.actors) { actor ->
                            AssistChip(
                                onClick = { onOpenFeed(actor, null, actor) },
                                label = { Text(actor) },
                            )
                        }
                    }
                }
            }
        }

        if (video.categories.isNotEmpty()) {
            item {
                GlassCard {
                    OverlineLabel("categories")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(video.categories) { category ->
                            AssistChip(
                                onClick = { onOpenFeed(category, category, null) },
                                label = { Text(category) },
                            )
                        }
                    }
                }
            }
        }

        if (state.related.isNotEmpty()) {
            item {
                Text("Related Videos", style = MaterialTheme.typography.headlineSmall)
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(state.related) { related ->
                        CompactVideoCard(video = related, onClick = { onOpenVideo(related) })
                    }
                }
            }
        }
    }
}
