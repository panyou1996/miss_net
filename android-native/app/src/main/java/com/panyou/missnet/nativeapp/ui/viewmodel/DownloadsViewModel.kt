package com.panyou.missnet.nativeapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.panyou.missnet.nativeapp.core.media.MediaDownloadCoordinator
import com.panyou.missnet.nativeapp.core.model.DownloadRecord
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DownloadsUiState(
    val items: List<DownloadRecord> = emptyList(),
)

class DownloadsViewModel(
    private val coordinator: MediaDownloadCoordinator,
) : ViewModel() {
    val uiState: StateFlow<DownloadsUiState> = coordinator.observeDownloads()
        .map { DownloadsUiState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), DownloadsUiState())

    fun remove(videoId: String) {
        viewModelScope.launch {
            coordinator.remove(videoId)
        }
    }
}
