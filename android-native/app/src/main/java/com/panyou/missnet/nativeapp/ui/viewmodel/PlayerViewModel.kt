package com.panyou.missnet.nativeapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.panyou.missnet.nativeapp.core.data.SettingsRepository
import com.panyou.missnet.nativeapp.core.data.VideoRepository
import com.panyou.missnet.nativeapp.core.media.MediaDownloadCoordinator
import com.panyou.missnet.nativeapp.core.media.VideoStreamResolver
import com.panyou.missnet.nativeapp.core.model.DownloadRecord
import com.panyou.missnet.nativeapp.core.model.DownloadStatus
import com.panyou.missnet.nativeapp.core.model.StreamInfo
import com.panyou.missnet.nativeapp.core.model.Video
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerUiState(
    val video: Video? = null,
    val streamInfo: StreamInfo? = null,
    val related: List<Video> = emptyList(),
    val isFavorite: Boolean = false,
    val download: DownloadRecord? = null,
    val isResolving: Boolean = true,
    val autoplayRelated: Boolean = false,
    val keepScreenAwake: Boolean = true,
    val error: String? = null,
)

class PlayerViewModel(
    private val repository: VideoRepository,
    private val settingsRepository: SettingsRepository,
    private val streamResolver: VideoStreamResolver,
    private val downloadCoordinator: MediaDownloadCoordinator,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var activeVideoId: String? = null
    private var downloadJob: Job? = null

    fun bind(video: Video) {
        if (activeVideoId == video.id) return
        activeVideoId = video.id
        _uiState.value = PlayerUiState(video = video)

        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update {
                    it.copy(
                        autoplayRelated = settings.autoplayRelated,
                        keepScreenAwake = settings.keepScreenAwakeInPlayer,
                    )
                }
            }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isFavorite = repository.isFavorite(video.id)) }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(related = repository.getRelatedVideos(video)) }
        }

        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            downloadCoordinator.observeDownloads().collect { downloads ->
                _uiState.update {
                    it.copy(download = downloads.firstOrNull { record -> record.video.id == video.id })
                }
            }
        }

        resolveStream(video)
    }

    private fun resolveStream(video: Video) {
        viewModelScope.launch {
            val existingDownload = downloadCoordinator.getDownload(video.id)
            if (existingDownload?.status == DownloadStatus.Completed) {
                _uiState.update { it.copy(download = existingDownload, isResolving = false) }
                return@launch
            }
            _uiState.update { it.copy(isResolving = true, error = null) }
            runCatching { streamResolver.resolve(video.sourceUrl) }
                .onSuccess { info ->
                    _uiState.update {
                        it.copy(
                            streamInfo = info,
                            isResolving = false,
                            error = null,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            streamInfo = null,
                            isResolving = false,
                            error = error.message ?: "Failed to resolve stream.",
                        )
                    }
                }
        }
    }

    fun toggleFavorite() {
        val video = _uiState.value.video ?: return
        viewModelScope.launch {
            if (repository.isFavorite(video.id)) {
                repository.removeFavorite(video.id)
                _uiState.update { it.copy(isFavorite = false) }
            } else {
                repository.saveFavorite(video)
                _uiState.update { it.copy(isFavorite = true) }
            }
        }
    }

    fun download() {
        val state = _uiState.value
        val video = state.video ?: return
        val stream = state.streamInfo ?: return
        viewModelScope.launch {
            runCatching {
                downloadCoordinator.enqueue(video, stream.streamUrl, stream.mimeType, stream.headers)
            }.onFailure { error ->
                _uiState.update { it.copy(error = error.message ?: "Download failed to start.") }
            }
        }
    }

    fun saveProgress(positionMs: Long, totalDurationMs: Long) {
        val video = _uiState.value.video ?: return
        if (totalDurationMs <= 0L) return
        viewModelScope.launch {
            repository.saveWatchProgress(video, positionMs, totalDurationMs)
        }
    }
}
