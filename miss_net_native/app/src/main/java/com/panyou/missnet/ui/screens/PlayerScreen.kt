@file:androidx.media3.common.util.UnstableApi

package com.panyou.missnet.ui.screens

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ActivityNotFoundException
import android.content.pm.ActivityInfo
import android.os.Build
import android.util.Log
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.panyou.missnet.data.media.DownloadMetadata
import com.panyou.missnet.data.media.MediaSourceClassifier
import com.panyou.missnet.data.media.DownloadTracker
import com.panyou.missnet.data.model.Video
import com.panyou.missnet.service.MissNetDownloadService
import com.panyou.missnet.service.PlaybackService
import com.panyou.missnet.ui.components.DurationBadge
import com.panyou.missnet.ui.components.StatusBadge
import com.panyou.missnet.ui.theme.ContainerTokens
import com.panyou.missnet.ui.theme.ThumbnailShape
import com.panyou.missnet.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}


private data class PendingDownload(
    val request: DownloadRequest
)

private const val DOWNLOAD_QUEUED_MESSAGE =
    "任务已加入队列，可在资源库 > 任务查看「进行中 / 需要处理 / 最近完成」。"
private const val DOWNLOAD_QUEUED_WITHOUT_NOTIFICATION_MESSAGE =
    "通知权限未开启，任务仍已加入队列；请在资源库 > 任务查看状态。"
private const val DOWNLOAD_UNAVAILABLE_MESSAGE = "下载失败：当前没有可用的视频地址。"
private const val SHARE_UNAVAILABLE_MESSAGE = "当前没有可分享的链接。"
private const val CAST_NOT_READY_MESSAGE = "投屏暂未接入，后续补齐。"
private const val PIP_NOT_SUPPORTED_MESSAGE = "当前系统版本不支持画中画（PiP）。"
private const val PLAYBACK_FAILED_MESSAGE = "播放失败，请重试。"
private const val FAVORITE_ADDED_MESSAGE = "已加入收藏。"
private const val FAVORITE_REMOVED_MESSAGE = "已取消收藏。"
private const val RELATED_SWITCH_MESSAGE = "已切换到相关推荐。"
private const val CONTINUE_PLAYBACK_MESSAGE = "已恢复到上次播放位置。"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun PlayerScreen(
    videoId: String,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val context = LocalContext.current
    val activity = remember { context.findActivity() as? ComponentActivity }
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }

    var controllerFuture: ListenableFuture<MediaController>? by remember { mutableStateOf(null) }
    var player: Player? by remember { mutableStateOf(null) }
    var pipRequested by remember { mutableStateOf(false) }
    var pendingDownload by remember { mutableStateOf<PendingDownload?>(null) }

    var isPlaying by remember { mutableStateOf(false) }
    var currentPos by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }
    var isFullscreen by remember { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableStateOf(1.0f) }
    var isBuffering by remember { mutableStateOf(true) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var hasRequestedExit by remember { mutableStateOf(false) }

    val primaryColor = MaterialTheme.colorScheme.primary

    fun persistPlaybackProgress() {
        val controlledPlayer = player ?: return
        val latestDuration = controlledPlayer.duration.coerceAtLeast(0L)
        val latestPosition = controlledPlayer.currentPosition.coerceAtLeast(0L)
        currentPos = latestPosition
        duration = latestDuration
        if (latestDuration > 0L) {
            viewModel.updatePlaybackProgress(latestPosition, latestDuration)
        }
    }

    fun stopPlaybackSession() {
        persistPlaybackProgress()
        val controlledPlayer = player ?: return
        controlledPlayer.pause()
        controlledPlayer.stop()
        controlledPlayer.clearMediaItems()
        isPlaying = false
    }

    fun exitPlayer() {
        hasRequestedExit = true
        pipRequested = false
        if (isFullscreen) {
            isFullscreen = false
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        stopPlaybackSession()
        onBack()
    }

    fun enqueueDownload(request: DownloadRequest) {
        DownloadTracker.upsertQueued(
            context = context,
            metadata = DownloadMetadata.fromBytes(request.data)
                ?: DownloadMetadata(id = request.id, title = request.id, coverUrl = uiState.video?.coverUrl, sourceUrl = uiState.streamUrl)
        )
        DownloadService.sendAddDownload(
            context,
            MissNetDownloadService::class.java,
            request,
            true
        )
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pending = pendingDownload ?: return@rememberLauncherForActivityResult
        enqueueDownload(pending.request)
        val message = if (granted) DOWNLOAD_QUEUED_MESSAGE else DOWNLOAD_QUEUED_WITHOUT_NOTIFICATION_MESSAGE
        viewModel.showDownloadMessage(message)
        pendingDownload = null
    }

    LaunchedEffect(uiState.downloadMessage) {
        uiState.downloadMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeDownloadMessage()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()

        controllerFuture?.addListener({
            try {
                val controller = controllerFuture?.get()
                player = controller
                controller?.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        isBuffering = state == Player.STATE_BUFFERING
                        if (state == Player.STATE_READY) playbackError = null
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        val reason = error.localizedMessage?.takeIf { it.isNotBlank() }?.let { "（$it）" }.orEmpty()
                        playbackError = "$PLAYBACK_FAILED_MESSAGE$reason"
                        isBuffering = false
                    }

                    override fun onEvents(player: Player, events: Player.Events) {
                        duration = player.duration.coerceAtLeast(0L)
                    }
                })
            } catch (e: Exception) {
                Log.e("PlayerScreen", "Failed to get MediaController", e)
            }
        }, MoreExecutors.directExecutor())

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    persistPlaybackProgress()
                    val isInPip = activity?.isInPictureInPictureMode == true
                    val isChangingConfigurations = activity?.isChangingConfigurations == true
                    if (!isInPip && !pipRequested && !isChangingConfigurations) {
                        player?.pause()
                        isPlaying = false
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (activity?.isInPictureInPictureMode != true) {
                        pipRequested = false
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            persistPlaybackProgress()
            val isInPip = activity?.isInPictureInPictureMode == true
            val isChangingConfigurations = activity?.isChangingConfigurations == true
            if (!isInPip && !pipRequested && (hasRequestedExit || !isChangingConfigurations)) {
                stopPlaybackSession()
            }
            controllerFuture?.let { MediaController.releaseFuture(it) }
        }
    }

    BackHandler {
        if (isFullscreen) {
            isFullscreen = false
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            exitPlayer()
        }
    }

    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(4000)
            showControls = false
        }
    }

    LaunchedEffect(player, isPlaying) {
        while (true) {
            currentPos = player?.currentPosition ?: 0L
            val currentDuration = player?.duration?.coerceAtLeast(0L) ?: 0L
            duration = currentDuration
            if (currentDuration > 0L) {
                viewModel.updatePlaybackProgress(currentPos, currentDuration)
            }
            delay(1000)
        }
    }

    LaunchedEffect(uiState.streamUrl, player, uiState.video?.id) {
        val streamUrl = uiState.streamUrl
        val p = player
        if (streamUrl != null && p != null) {
            playbackError = null
            isBuffering = true
            val targetMediaId = uiState.video?.id ?: videoId
            val currentMediaItem = p.currentMediaItem
            val currentUri = currentMediaItem?.localConfiguration?.uri?.toString()
            val shouldReplaceMediaItem = p.mediaItemCount == 0 ||
                currentMediaItem?.mediaId != targetMediaId ||
                currentUri != streamUrl

            if (shouldReplaceMediaItem) {
                val mediaItem = MediaItem.Builder()
                    .setUri(streamUrl)
                    .setMimeType(MimeTypes.APPLICATION_M3U8)
                    .setMediaId(targetMediaId)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(uiState.video?.title)
                            .setArtworkUri(android.net.Uri.parse(uiState.video?.coverUrl ?: ""))
                            .build()
                    )
                    .build()

                p.setMediaItem(mediaItem)
                p.prepare()
                val resumePosition = uiState.lastPositionMs
                if (resumePosition > 0L) {
                    p.seekTo(resumePosition)
                    currentPos = resumePosition
                }
                p.playWhenReady = true
            } else if (p.playbackState == Player.STATE_IDLE) {
                p.prepare()
            }
        }
    }

    LaunchedEffect(isFullscreen) {
        val window = activity?.window ?: return@LaunchedEffect
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        if (isFullscreen) {
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        } else {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (!isFullscreen) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            uiState.video?.title ?: "播放",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { exitPlayer() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                pipRequested = true
                                activity?.enterPictureInPictureMode(android.app.PictureInPictureParams.Builder().build())
                            } else {
                                viewModel.showDownloadMessage(PIP_NOT_SUPPORTED_MESSAGE)
                            }
                        }) { Icon(Icons.Default.PictureInPicture, null) }
                    }
                )
            }
        }
    ) { innerPadding ->
        if (uiState.isLoading && uiState.video == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = primaryColor)
            }
        } else {
            val effectiveError = uiState.errorMessage ?: playbackError
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                val videoBoxModifier = Modifier
                    .fillMaxWidth()
                    .then(if (isFullscreen) Modifier.fillMaxHeight() else Modifier.aspectRatio(16f / 9f))
                    .background(Color.Black)
                    .clickable(remember { MutableInteractionSource() }, null) { showControls = !showControls }

                val finalModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                    with(sharedTransitionScope) {
                        videoBoxModifier.sharedElement(
                            state = rememberSharedContentState(key = "image-$videoId"),
                            animatedVisibilityScope = animatedVisibilityScope
                        )
                    }
                } else {
                    videoBoxModifier
                }

                Box(modifier = finalModifier) {
                    PlayerContainer(player)
                    if (isBuffering || (uiState.isLoading && uiState.streamUrl == null)) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center).size(48.dp),
                            color = primaryColor,
                            strokeWidth = 4.dp
                        )
                    }
                    if (effectiveError != null) {
                        PlayerErrorOverlay(
                            message = effectiveError,
                            onRetry = {
                                playbackError = null
                                isBuffering = true
                                viewModel.retry()
                                player?.let { controlledPlayer ->
                                    controlledPlayer.prepare()
                                    controlledPlayer.playWhenReady = true
                                }
                            },
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    PlayerControls(
                        showControls = showControls,
                        isFullscreen = isFullscreen,
                        isPlaying = isPlaying,
                        currentPos = currentPos,
                        duration = duration,
                        onTogglePlay = {
                            if (isPlaying) player?.pause() else player?.play()
                        },
                        onSeekBack = { player?.seekBack() },
                        onSeekForward = { player?.seekForward() },
                        onSeekTo = {
                            player?.seekTo(it)
                            currentPos = it
                            viewModel.updatePlaybackProgress(it, duration)
                        },
                        onToggleFullscreen = {
                            val next = !isFullscreen
                            isFullscreen = next
                            activity?.requestedOrientation = if (next) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        },
                        onBack = {
                            if (isFullscreen) {
                                isFullscreen = false
                                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            } else {
                                exitPlayer()
                            }
                        },
                        onCast = { viewModel.showDownloadMessage(CAST_NOT_READY_MESSAGE) },
                        onSpeed = { showSpeedSheet = true }
                    )
                }

                if (!isFullscreen) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
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
                            // 续看提示
                            if (uiState.lastPositionMs > 0L && uiState.lastDurationMs > 0L) {
                                item {
                                    ContinueWatchingCard(
                                        lastPositionMs = uiState.lastPositionMs,
                                        onContinue = {
                                            player?.seekTo(uiState.lastPositionMs)
                                            player?.playWhenReady = true
                                            player?.play()
                                            viewModel.showDownloadMessage(CONTINUE_PLAYBACK_MESSAGE)
                                        }
                                    )
                                }
                            }

                            // 视频信息区
                            item {
                                VideoInfoSection(
                                    title = uiState.video?.title ?: "加载中...",
                                    createdAt = uiState.video?.createdAt,
                                    tags = uiState.video?.tags ?: emptyList()
                                )
                            }

                            // 操作按钮区 - 主操作
                            item {
                                PrimaryActionsRow(
                                    onDownload = {
                                        val url = uiState.streamUrl
                                        val video = uiState.video
                                        if (url.isNullOrBlank() || video == null) {
                                            viewModel.showDownloadMessage(DOWNLOAD_UNAVAILABLE_MESSAGE)
                                        } else {
                                            val metadata = DownloadMetadata(
                                                id = video.id,
                                                title = video.title,
                                                coverUrl = video.coverUrl,
                                                sourceUrl = url,
                                                requestUri = url,
                                                mimeType = MediaSourceClassifier.inferDownloadMimeType(url)
                                            )
                                            val downloadRequest = DownloadRequest.Builder(video.id, android.net.Uri.parse(url))
                                                .setMimeType(MediaSourceClassifier.inferDownloadMimeType(url))
                                                .setData(metadata.toByteArray())
                                                .build()

                                            val requiresPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED

                                            if (requiresPermission) {
                                                pendingDownload = PendingDownload(downloadRequest)
                                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            } else {
                                                enqueueDownload(downloadRequest)
                                                viewModel.showDownloadMessage(DOWNLOAD_QUEUED_MESSAGE)
                                            }
                                        }
                                    },
                                    onFavorite = {
                                        viewModel.toggleFavorite()
                                        viewModel.showDownloadMessage(
                                            if (uiState.isFavorite) FAVORITE_REMOVED_MESSAGE else FAVORITE_ADDED_MESSAGE
                                        )
                                    },
                                    isFavorite = uiState.isFavorite
                                )
                            }

                            // 操作按钮区 - 次操作
                            item {
                                SecondaryActionsRow(
                                    onShare = {
                                        val shared = shareVideo(
                                            context = context,
                                            title = uiState.video?.title.orEmpty(),
                                            url = uiState.streamUrl ?: uiState.video?.sourceUrl
                                        )
                                        if (!shared) {
                                            viewModel.showDownloadMessage(SHARE_UNAVAILABLE_MESSAGE)
                                        }
                                    },
                                    onSpeed = { showSpeedSheet = true },
                                    onCast = { viewModel.showDownloadMessage(CAST_NOT_READY_MESSAGE) }
                                )
                            }

                            // 状态区
                            item {
                                PlayerStatusSection(
                                    isPlaying = isPlaying,
                                    isBuffering = isBuffering,
                                    errorMessage = effectiveError
                                )
                            }

                            // 分割线
                            item {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }

                            // 推荐区标题
                            item {
                                RecommendSectionHeader()
                            }

                            items(uiState.relatedVideos) { related ->
                                RecommendItem(
                                    video = related,
                                    onClick = {
                                        viewModel.updatePlaybackProgress(currentPos, duration)
                                        viewModel.setVideo(related.id)
                                        viewModel.showDownloadMessage(RELATED_SWITCH_MESSAGE)
                                    }
                                )
                            }

                            item { Spacer(modifier = Modifier.height(ContainerTokens.ScreenBottomPadding)) }
                        }
                    }
                }
            }
        }

        if (showSpeedSheet) {
            ModalBottomSheet(onDismissRequest = { showSpeedSheet = false }) {
                Column(modifier = Modifier.padding(bottom = 32.dp)) {
                    Text(
                        text = "播放速度",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    HorizontalDivider()
                    listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                        ListItem(
                            headlineContent = { Text("${speed}x") },
                            trailingContent = if (playbackSpeed == speed) {
                                { Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) }
                            } else null,
                            modifier = Modifier.clickable {
                                playbackSpeed = speed
                                player?.setPlaybackSpeed(speed)
                                showSpeedSheet = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerStatusSection(
    isPlaying: Boolean,
    isBuffering: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier
) {
    val (playbackLabel, playbackContainer, playbackContent) = when {
        errorMessage != null -> Triple(
            "需要处理",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
        isBuffering -> Triple(
            "加载中",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        isPlaying -> Triple(
            "播放中",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        else -> Triple(
            "已暂停",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "状态",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "播放",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                StatusBadge(
                    text = playbackLabel,
                    containerColor = playbackContainer,
                    contentColor = playbackContent
                )
            }

            Text(
                text = "下载与导出状态统一在资源库 > 任务查看：",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusBadge(
                    text = "进行中",
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
                StatusBadge(
                    text = "需要处理",
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
                StatusBadge(
                    text = "最近完成",
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
                StatusBadge(
                    text = "已导出",
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
                StatusBadge(
                    text = "导出失败",
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
                StatusBadge(
                    text = "不支持",
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RecommendSectionHeader(modifier: Modifier = Modifier) {
    Text(
        text = "相关推荐",
        modifier = modifier.fillMaxWidth(),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun ContinueWatchingCard(
    lastPositionMs: Long,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
        onClick = onContinue
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.PlayCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "继续播放",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = formatTime(lastPositionMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledTonalButton(onClick = onContinue) {
                Text("继续")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VideoInfoSection(
    title: String,
    createdAt: String?,
    tags: List<String>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // 发布日期
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CalendarToday,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "发布于 ${createdAt?.take(10) ?: "最近"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 标题
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (tags.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            // 标签使用 FlowRow 实现换行
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tags.forEach { tag ->
                    AssistChip(
                        onClick = { },
                        label = {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.height(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PrimaryActionsRow(
    onDownload: () -> Unit,
    onFavorite: () -> Unit,
    isFavorite: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onDownload,
            modifier = Modifier.weight(1.2f)
        ) {
            Icon(Icons.Default.CloudDownload, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("下载")
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
            }
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isFavorite) "已收藏" else "收藏")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SecondaryActionsRow(
    onShare: () -> Unit,
    onSpeed: () -> Unit,
    onCast: () -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
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
            icon = Icons.Default.Cast,
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
        label = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            leadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
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
    onCast: () -> Unit,
    onSpeed: () -> Unit
) {
    AnimatedVisibility(visible = showControls, enter = fadeIn(), exit = fadeOut()) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).padding(if (isFullscreen) 48.dp else 16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = onBack) {
                    Icon(if (isFullscreen) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White, modifier = Modifier.size(32.dp))
                }
                Row {
                    IconButton(onClick = onCast) { Icon(Icons.Default.Cast, null, tint = Color.White) }
                    IconButton(onClick = onSpeed) { Icon(Icons.Default.Speed, null, tint = Color.White) }
                }
            }

            Row(modifier = Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                IconButton(onClick = onSeekBack) { Icon(Icons.Default.Replay10, null, tint = Color.White, modifier = Modifier.size(32.dp)) }
                IconButton(onClick = onTogglePlay, modifier = Modifier.size(80.dp)) {
                    Icon(imageVector = if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle, null, tint = Color.White, modifier = Modifier.fillMaxSize())
                }
                IconButton(onClick = onSeekForward) { Icon(Icons.Default.Forward10, null, tint = Color.White, modifier = Modifier.size(32.dp)) }
            }

            Column(modifier = Modifier.align(Alignment.BottomCenter)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(formatTime(currentPos), color = Color.White, fontSize = 11.sp)
                    Slider(
                        value = currentPos.toFloat(),
                        onValueChange = { onSeekTo(it.toLong()) },
                        valueRange = 0f..(duration.toFloat().coerceAtLeast(1f)),
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White)
                    )
                    Text(formatTime(duration), color = Color.White, fontSize = 11.sp)
                    IconButton(onClick = onToggleFullscreen) { Icon(if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, null, tint = Color.White) }
                }
            }
        }
    }
}

@Composable
fun RecommendItem(video: Video, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp, 60.dp)
                    .clip(ThumbnailShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = video.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // 播放时长徽章
                video.duration?.let { dur ->
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
                    text = video.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = video.tags.take(2).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
        color = Color.Black.copy(alpha = 0.82f)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(imageVector = Icons.Default.ErrorOutline, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "播放失败", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = message, color = Color.White.copy(alpha = 0.88f), style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("重试")
            }
        }
    }
}

private fun shareVideo(context: Context, title: String, url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val shareText = buildString {
        if (title.isNotBlank()) appendLine(title)
        append(url)
    }
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title.ifBlank { "MissNet" })
        putExtra(Intent.EXTRA_TEXT, shareText)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return try {
        context.startActivity(Intent.createChooser(shareIntent, "分享视频"))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSecs = ms / 1000
    val hours = totalSecs / 3600
    val mins = (totalSecs % 3600) / 60
    val secs = totalSecs % 60
    return if (hours > 0) "%02d:%02d:%02d".format(hours, mins, secs) else "%02d:%02d".format(mins, secs)
}
