package com.panyou.missnet.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.panyou.missnet.data.local.LocalVideoStateStore
import com.panyou.missnet.data.local.WatchProgressEntry
import com.panyou.missnet.data.media.DownloadStatusEntry
import com.panyou.missnet.data.media.DownloadTracker
import com.panyou.missnet.data.model.Video
import com.panyou.missnet.data.repository.VideoRepository
import com.panyou.missnet.data.result.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val heroVideos: List<Video> = emptyList(),
    val newVideos: List<Video> = emptyList(),
    val monthlyVideos: List<Video> = emptyList(),
    val weeklyVideos: List<Video> = emptyList(),
    val uncensoredVideos: List<Video> = emptyList(),
    val subtitleVideos: List<Video> = emptyList(),
    val vrVideos: List<Video> = emptyList(),
    val chiguaVideos: List<Video> = emptyList(),
    val continueWatching: List<WatchProgressEntry> = emptyList(),
    val recentFavorites: List<Video> = emptyList(),
    val recentDownloads: List<DownloadStatusEntry> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: VideoRepository,
    private val localStore: LocalVideoStateStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        observeLocalData()
        observeDownloads()
        loadDashboard()
    }

    fun retry() {
        loadDashboard()
    }

    fun refresh() {
        loadDashboard(forceRefresh = true)
    }

    private fun observeLocalData() {
        viewModelScope.launch {
            localStore.observeHistoryEntries().collect { entries ->
                _uiState.update { it.copy(continueWatching = entries.take(4)) }
            }
        }
        viewModelScope.launch {
            localStore.observeFavorites().collect { favorites ->
                _uiState.update { it.copy(recentFavorites = favorites.take(4)) }
            }
        }
    }

    private fun observeDownloads() {
        viewModelScope.launch {
            DownloadTracker.downloads.collect { downloads ->
                _uiState.update {
                    it.copy(
                        recentDownloads = prioritizeDownloads(downloads).take(4)
                    )
                }
            }
        }
    }

    private fun loadDashboard(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val hasContent = _uiState.value.heroVideos.isNotEmpty() ||
                _uiState.value.newVideos.isNotEmpty() ||
                _uiState.value.monthlyVideos.isNotEmpty() ||
                _uiState.value.uncensoredVideos.isNotEmpty()
            _uiState.value = _uiState.value.copy(
                isLoading = !hasContent,
                isRefreshing = hasContent,
                errorMessage = null
            )
            when (val result = repository.getHomePayloadResult(sectionLimit = 10, weeklyLimit = 15, forceRefresh = forceRefresh)) {
                AppResult.Empty -> {
                    _uiState.value = _uiState.value.copy(
                        heroVideos = emptyList(),
                        newVideos = emptyList(),
                        monthlyVideos = emptyList(),
                        weeklyVideos = emptyList(),
                        uncensoredVideos = emptyList(),
                        subtitleVideos = emptyList(),
                        vrVideos = emptyList(),
                        chiguaVideos = emptyList(),
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = null
                    )
                }

                is AppResult.Failure -> {
                    Log.e("HomeViewModel", "Failed to load dashboard", result.cause)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = result.message
                    )
                }

                is AppResult.Success -> {
                    val payload = result.data
                    val hero = payload.weeklyVideos.take(3)
                    val weeklyList = payload.weeklyVideos.drop(3)

                    _uiState.value = _uiState.value.copy(
                        heroVideos = hero,
                        newVideos = payload.newVideos,
                        monthlyVideos = payload.monthlyVideos,
                        weeklyVideos = weeklyList,
                        uncensoredVideos = payload.uncensoredVideos,
                        subtitleVideos = payload.subtitleVideos,
                        vrVideos = payload.vrVideos,
                        chiguaVideos = payload.chiguaVideos,
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = null
                    )
                }
            }
        }
    }

    private fun prioritizeDownloads(downloads: List<DownloadStatusEntry>): List<DownloadStatusEntry> {
        return downloads.sortedWith(
            compareBy<DownloadStatusEntry> { entry ->
                when {
                    entry.state == androidx.media3.exoplayer.offline.Download.STATE_FAILED ||
                        entry.exportState == com.panyou.missnet.data.media.ExportState.EXPORT_FAILED -> 0
                    entry.state == androidx.media3.exoplayer.offline.Download.STATE_QUEUED ||
                        entry.state == androidx.media3.exoplayer.offline.Download.STATE_DOWNLOADING ||
                        entry.state == androidx.media3.exoplayer.offline.Download.STATE_RESTARTING ||
                        entry.state == androidx.media3.exoplayer.offline.Download.STATE_STOPPED ||
                        entry.exportState == com.panyou.missnet.data.media.ExportState.EXPORTING ||
                        entry.exportState == com.panyou.missnet.data.media.ExportState.EXPORT_QUEUED -> 1
                    else -> 2
                }
            }.thenByDescending { it.updatedAt }
        )
    }
}
