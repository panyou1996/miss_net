package com.panyou.missnet.nativeapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.panyou.missnet.nativeapp.core.data.SettingsRepository
import com.panyou.missnet.nativeapp.core.data.VideoRepository
import com.panyou.missnet.nativeapp.core.media.MediaDownloadCoordinator
import com.panyou.missnet.nativeapp.core.model.AppSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val favoriteCount: Int = 0,
    val historyCount: Int = 0,
    val downloadCount: Int = 0,
    val offlineStorageBytes: Long = 0L,
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val videoRepository: VideoRepository,
    private val downloadCoordinator: MediaDownloadCoordinator,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
        refreshStats()
    }

    fun refreshStats() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    favoriteCount = videoRepository.favoriteCount(),
                    historyCount = videoRepository.historyCount(),
                    downloadCount = downloadCoordinator.observeDownloads().first().size,
                    offlineStorageBytes = downloadCoordinator.cacheSpaceBytes(),
                )
            }
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setUseDynamicColor(enabled) }
    }

    fun setAutoplayRelated(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoplayRelated(enabled) }
    }

    fun setIncognito(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setIncognitoMode(enabled) }
    }

    fun setWifiOnlyDownloads(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setPreferWifiDownloads(enabled) }
    }

    fun setKeepScreenAwake(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setKeepScreenAwake(enabled) }
    }

    fun clearHistory() {
        viewModelScope.launch {
            videoRepository.clearHistory()
            refreshStats()
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch { videoRepository.clearSearchHistory() }
    }

    fun clearDownloads() {
        viewModelScope.launch {
            downloadCoordinator.removeAll()
            refreshStats()
        }
    }
}
