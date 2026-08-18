@file:androidx.media3.common.util.UnstableApi

package com.panyou.missnet.ui.screens

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.panyou.missnet.data.media.DownloadMetadata
import com.panyou.missnet.data.media.MediaSourceClassifier
import com.panyou.missnet.data.media.DownloadTracker
import com.panyou.missnet.data.model.Video
import com.panyou.missnet.service.MissNetDownloadService
import com.panyou.missnet.service.PlaybackService
import com.panyou.missnet.ui.components.SecondaryPageSurface
import com.panyou.missnet.ui.theme.ActionTokens
import com.panyou.missnet.ui.theme.ContainerTokens
import com.panyou.missnet.ui.screens.player.EpisodeSelectorSection
import com.panyou.missnet.ui.screens.player.PlayerControls
import com.panyou.missnet.ui.screens.player.PlayerLoadingState
import com.panyou.missnet.ui.screens.player.PlayerPlaybackSurface
import com.panyou.missnet.ui.screens.player.PrimaryActionsRow
import com.panyou.missnet.ui.screens.player.RecommendItem
import com.panyou.missnet.ui.screens.player.RecommendSectionHeader
import com.panyou.missnet.ui.screens.player.SecondaryActionsRow
import com.panyou.missnet.ui.screens.player.VideoInfoSection
import com.panyou.missnet.ui.screens.player.findActivity
import com.panyou.missnet.ui.screens.player.shareVideo
import com.panyou.missnet.ui.screens.player.castOrOpenExternalPlayer
import com.panyou.missnet.ui.theme.videoSharedTransitionKey
import com.panyou.missnet.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay


private data class PendingDownload(
    val request: DownloadRequest
)

private const val DOWNLOAD_QUEUED_MESSAGE =
    "任务已加入队列，可在资源库 > 任务查看「进行中 / 需要处理 / 最近完成」。"
private const val DOWNLOAD_QUEUED_WITHOUT_NOTIFICATION_MESSAGE =
    "通知权限未开启，任务仍已加入队列；请在资源库 > 任务查看状态。"
private const val DOWNLOAD_UNAVAILABLE_MESSAGE = "下载失败：当前没有可用的视频地址。"
private const val SHARE_UNAVAILABLE_MESSAGE = "当前没有可分享的链接。"
private const val PIP_NOT_SUPPORTED_MESSAGE = "当前系统版本不支持画中画（PiP）。"
private const val PLAYBACK_FAILED_MESSAGE = "播放失败，请重试。"
private const val FAVORITE_ADDED_MESSAGE = "已加入收藏。"
private const val FAVORITE_REMOVED_MESSAGE = "已取消收藏。"
private const val RELATED_SWITCH_MESSAGE = "已切换到相关推荐。"
private const val SEEK_INTERVAL_MS = 10_000L

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun PlayerScreen(
    videoId: String,
    onBack: () -> Unit,
    onTagClick: (String) -> Unit,
    onActorClick: (String) -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val context = LocalContext.current
    val activity = remember { context.findActivity() as? ComponentActivity }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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
    var bufferedPos by remember { mutableStateOf(0L) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var hasRequestedExit by remember { mutableStateOf(false) }
    var resumeAppliedForMediaId by remember { mutableStateOf<String?>(null) }

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

    fun seekBy(deltaMs: Long) {
        val controlledPlayer = player ?: return
        val targetPosition = (controlledPlayer.currentPosition + deltaMs).coerceAtLeast(0L)
            .let { position ->
                val maxDuration = controlledPlayer.duration.coerceAtLeast(0L)
                if (maxDuration > 0L) position.coerceAtMost(maxDuration) else position
            }
        controlledPlayer.seekTo(targetPosition)
        currentPos = targetPosition
        if (duration > 0L) {
            viewModel.updatePlaybackProgress(targetPosition, duration)
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
                        bufferedPos = player.bufferedPosition.coerceAtLeast(0L)
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
            bufferedPos = player?.bufferedPosition?.coerceAtLeast(0L) ?: 0L
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
            resumeAppliedForMediaId = null
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
                    resumeAppliedForMediaId = targetMediaId
                }
                p.playWhenReady = true
            } else if (p.playbackState == Player.STATE_IDLE) {
                p.prepare()
            }
        }
    }

    LaunchedEffect(player, uiState.video?.id, uiState.lastPositionMs) {
        val p = player ?: return@LaunchedEffect
        val mediaId = uiState.video?.id ?: return@LaunchedEffect
        val resumePosition = uiState.lastPositionMs
        if (resumePosition <= 0L || resumeAppliedForMediaId == mediaId) return@LaunchedEffect
        if (p.currentMediaItem?.mediaId != mediaId) return@LaunchedEffect
        delay(250)
        if (p.currentPosition < 2_000L) {
            p.seekTo(resumePosition)
            currentPos = resumePosition
            resumeAppliedForMediaId = mediaId
        }
    }

    LaunchedEffect(isFullscreen) {
        val window = activity?.window ?: return@LaunchedEffect
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (isFullscreen) {
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
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
            PlayerLoadingState(
                title = "正在准备播放器",
                subtitle = "请稍候，正在同步视频信息与播放地址。"
            )
        } else {
            val effectiveError = uiState.errorMessage ?: playbackError
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                val videoBoxModifier = Modifier
                    .fillMaxWidth()
                    .then(if (isFullscreen) Modifier.fillMaxHeight() else Modifier.aspectRatio(16f / 9f))
                    .background(Color.Black)

                val finalModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                    with(sharedTransitionScope) {
                        videoBoxModifier.sharedElement(
                            state = rememberSharedContentState(key = videoSharedTransitionKey(videoId)),
                            animatedVisibilityScope = animatedVisibilityScope
                        )
                    }
                } else {
                    videoBoxModifier
                }

                Box(modifier = finalModifier) {
                    val showPosterArtwork =
                        !uiState.video?.coverUrl.isNullOrBlank() &&
                            (player == null ||
                                player?.playbackState == Player.STATE_IDLE ||
                                (uiState.isLoading && uiState.streamUrl == null))

                    PlayerPlaybackSurface(
                        player = player,
                        coverUrl = uiState.video?.coverUrl,
                        title = uiState.video?.title,
                        showPosterArtwork = showPosterArtwork,
                        isBuffering = isBuffering,
                        isLoadingStream = uiState.isLoading && uiState.streamUrl == null,
                        bufferedProgress = if (duration > 0L && bufferedPos > 0L) {
                            (bufferedPos.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                        } else {
                            null
                        },
                        errorMessage = effectiveError,
                        showControls = showControls,
                        onToggleControls = { showControls = !showControls },
                        onSeekBack = { seekBy(-SEEK_INTERVAL_MS) },
                        onSeekForward = { seekBy(SEEK_INTERVAL_MS) },
                        onRetry = {
                            playbackError = null
                            isBuffering = true
                            viewModel.retry()
                            player?.let { controlledPlayer ->
                                controlledPlayer.prepare()
                                controlledPlayer.playWhenReady = true
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        controls = {
                            PlayerControls(
                                showControls = showControls,
                                isFullscreen = isFullscreen,
                                isPlaying = isPlaying,
                                currentPos = currentPos,
                                duration = duration,
                                onTogglePlay = {
                                    if (isPlaying) player?.pause() else player?.play()
                                },
                                onSeekBack = { seekBy(-SEEK_INTERVAL_MS) },
                                onSeekForward = { seekBy(SEEK_INTERVAL_MS) },
                                onSeekTo = {
                                    player?.seekTo(it)
                                    currentPos = it
                                    viewModel.updatePlaybackProgress(it, duration)
                                },
                                onToggleFullscreen = {
                                    val next = !isFullscreen
                                    isFullscreen = next
                                    activity?.requestedOrientation = if (next) {
                                        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                    } else {
                                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                    }
                                },
                                onBack = {
                                    if (isFullscreen) {
                                        isFullscreen = false
                                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                    } else {
                                        exitPlayer()
                                    }
                                },
                                onSpeed = { showSpeedSheet = true }
                            )
                        }
                    )
                }

                if (!isFullscreen) {
                    SecondaryPageSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        fillMaxSize = false
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(ContainerTokens.ScreenContentPadding),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // 视频信息区
                            item {
                                VideoInfoSection(
                                    title = uiState.video?.title ?: "加载中...",
                                    primaryDate = uiState.video?.displayDate,
                                    lastPositionMs = uiState.lastPositionMs,
                                    tags = uiState.video?.tags ?: emptyList(),
                                    actors = uiState.video?.actors ?: emptyList(),
                                    onTagClick = onTagClick,
                                    onActorClick = onActorClick
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
                                    onCast = {
                                        val success = castOrOpenExternalPlayer(
                                            context = context,
                                            title = uiState.video?.title.orEmpty(),
                                            url = uiState.streamUrl ?: uiState.video?.sourceUrl
                                        )
                                        if (!success) {
                                            viewModel.showDownloadMessage("未找到可用的投屏接收器或外部播放器")
                                        }
                                    }
                                )
                            }

                            val currentVideo = uiState.video
                            if (currentVideo != null && (currentVideo.videoCount > 1 || currentVideo.videos.isNotEmpty())) {
                                item {
                                    EpisodeSelectorSection(
                                        currentVideoId = currentVideo.id,
                                        videoCount = currentVideo.videoCount,
                                        episodes = currentVideo.videos,
                                        onEpisodeSelect = { epId ->
                                            stopPlaybackSession()
                                            viewModel.setVideo(epId)
                                        }
                                    )
                                }
                            }

                            if (uiState.relatedVideos.isNotEmpty()) {
                                item {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )
                                }

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
