package com.panyou.missnet.ui.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.panyou.missnet.data.local.LocalVideoStateStore
import com.panyou.missnet.data.model.Video
import com.panyou.missnet.data.repository.VideoRepository
import com.panyou.missnet.data.util.VideoResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayerUiState(
    val isLoading: Boolean = true,
    val video: Video? = null,
    val streamUrl: String? = null,
    val relatedVideos: List<Video> = emptyList(),
    val isFavorite: Boolean = false,
    val errorMessage: String? = null,
    val downloadMessage: String? = null,
    val lastPositionMs: Long = 0L,
    val lastDurationMs: Long = 0L
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: VideoRepository,
    private val resolver: VideoResolver,
    private val localStore: LocalVideoStateStore,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState

    private var currentVideoId: String? = null

    init {
        val videoId: String? = savedStateHandle["videoId"]
        Log.d("PlayerViewModel", "Init with videoId: $videoId")
        if (videoId != null) {
            loadVideoDetails(videoId)
        } else {
            _uiState.value = PlayerUiState(isLoading = false, errorMessage = "无法播放：缺少视频标识")
        }
    }

    fun retry() {
        currentVideoId?.let(::loadVideoDetails)
    }

    private fun loadVideoDetails(videoId: String) {
        currentVideoId = videoId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, downloadMessage = null)
            try {
                Log.d("PlayerViewModel", "Fetching video metadata...")
                val video = repository.getVideoById(videoId)
                if (video != null) {
                    Log.d("PlayerViewModel", "Metadata fetched. Resolving stream URL: ${video.sourceUrl}")
                    val streamUrl = try {
                        resolver.resolve(video.sourceUrl)
                    } catch (e: Exception) {
                        Log.e("PlayerViewModel", "Resolver crash", e)
                        video.sourceUrl
                    }
                    val related = repository.getRecentVideos(5)
                    val progressEntry = localStore.getProgress(video.id)

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        video = video,
                        streamUrl = streamUrl,
                        relatedVideos = related.filterNot { it.id == video.id },
                        isFavorite = localStore.isFavorite(video.id),
                        lastPositionMs = progressEntry?.positionMs ?: 0L,
                        lastDurationMs = progressEntry?.durationMs ?: 0L
                    )
                } else {
                    Log.e("PlayerViewModel", "Video not found in DB")
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "资源不存在或已不可用")
                }
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "General error in loadVideoDetails", e)
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = e.message ?: "加载失败，请稍后重试")
            }
        }
    }

    fun toggleFavorite() {
        val currentVideo = _uiState.value.video ?: return
        viewModelScope.launch {
            val newState = localStore.toggleFavorite(currentVideo)
            _uiState.value = _uiState.value.copy(isFavorite = newState)
        }
    }

    fun updatePlaybackProgress(positionMs: Long, durationMs: Long) {
        val currentVideo = _uiState.value.video ?: return
        if (durationMs <= 0L) return
        val previous = _uiState.value
        val delta = kotlin.math.abs(positionMs - previous.lastPositionMs)
        if (delta < 5_000L && durationMs == previous.lastDurationMs) return

        localStore.upsertWatchProgress(currentVideo, positionMs, durationMs)
        _uiState.value = previous.copy(lastPositionMs = positionMs, lastDurationMs = durationMs)
    }

    fun showDownloadMessage(message: String) {
        _uiState.value = _uiState.value.copy(downloadMessage = message)
    }

    fun consumeDownloadMessage() {
        _uiState.value = _uiState.value.copy(downloadMessage = null)
    }

    fun setVideo(videoId: String) {
        loadVideoDetails(videoId)
    }
}
