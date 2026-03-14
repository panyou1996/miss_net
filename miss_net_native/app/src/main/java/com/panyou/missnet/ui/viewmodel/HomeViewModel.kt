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
        refreshLocalData()
        observeDownloads()
        loadDashboard()
    }

    fun retry() {
        refreshLocalData()
        loadDashboard()
    }

    private fun observeDownloads() {
        viewModelScope.launch {
            DownloadTracker.downloads.collect { downloads ->
                _uiState.update {
                    it.copy(
                        recentDownloads = downloads
                            .sortedByDescending { item -> item.updatedAt }
                            .take(4)
                    )
                }
            }
        }
    }

    private fun refreshLocalData() {
        _uiState.update {
            it.copy(
                continueWatching = localStore.getHistoryEntries().take(4),
                recentFavorites = localStore.getFavorites().take(4),
                recentDownloads = DownloadTracker.downloads.value
                    .sortedByDescending { item -> item.updatedAt }
                    .take(4)
            )
        }
    }

    private fun loadDashboard() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val payload = repository.getHomePayload(sectionLimit = 10, weeklyLimit = 15)
                val new = payload.newVideos
                val monthly = payload.monthlyVideos
                val weekly = payload.weeklyVideos
                val uncensored = payload.uncensoredVideos
                val subtitle = payload.subtitleVideos
                val vr = payload.vrVideos
                val chigua = payload.chiguaVideos

                val hero = weekly.take(3)
                val weeklyList = weekly.drop(3)
                val allSections = listOf(new, monthly, weekly, uncensored, subtitle, vr, chigua)
                val isAllEmpty = allSections.all { section -> section.isEmpty() }

                _uiState.value = _uiState.value.copy(
                    heroVideos = hero,
                    newVideos = new,
                    monthlyVideos = monthly,
                    weeklyVideos = weeklyList,
                    uncensoredVideos = uncensored,
                    subtitleVideos = subtitle,
                    vrVideos = vr,
                    chiguaVideos = chigua,
                    isLoading = false,
                    errorMessage = if (isAllEmpty) "首页数据为空，可能是网络异常或服务暂时不可用。" else null
                )
                refreshLocalData()
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Failed to load dashboard", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "首页加载失败，请稍后重试。"
                )
                refreshLocalData()
            }
        }
    }
}
