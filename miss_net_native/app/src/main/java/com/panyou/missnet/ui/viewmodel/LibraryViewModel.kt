package com.panyou.missnet.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.panyou.missnet.data.local.LocalVideoStateStore
import com.panyou.missnet.data.local.WatchProgressEntry
import com.panyou.missnet.data.media.DownloadCommands
import com.panyou.missnet.data.media.DownloadStatusEntry
import com.panyou.missnet.data.media.DownloadTracker
import com.panyou.missnet.data.media.ExportState
import com.panyou.missnet.data.model.Video
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val isLoading: Boolean = false,
    val likes: List<Video> = emptyList(),
    val history: List<Video> = emptyList(),
    val historyEntries: List<WatchProgressEntry> = emptyList(),
    val historyProgress: Map<String, Float> = emptyMap(),
    val downloads: List<DownloadStatusEntry> = emptyList()
)

@HiltViewModel
@UnstableApi
class LibraryViewModel @Inject constructor(
    private val localStore: LocalVideoStateStore,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState(downloads = DownloadTracker.downloads.value))
    val uiState: StateFlow<LibraryUiState> = _uiState

    init {
        loadAll()
        viewModelScope.launch {
            DownloadTracker.downloads.collect { downloads ->
                downloads
                    .filter { it.state == androidx.media3.exoplayer.offline.Download.STATE_COMPLETED && it.exportState == ExportState.NOT_EXPORTED }
                    .forEach { DownloadCommands.export(appContext, it) }
                _uiState.update { it.copy(downloads = downloads) }
            }
        }
    }

    fun loadAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val likes = localStore.getFavorites()
            val historyEntries = localStore.getHistoryEntries()
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                likes = likes,
                history = historyEntries.map { it.video },
                historyEntries = historyEntries,
                historyProgress = historyEntries.associate { it.video.id to it.progress }
            )
        }
    }

    fun pauseDownload(downloadId: String) {
        DownloadCommands.pause(appContext, downloadId)
    }

    fun resumeDownload(downloadId: String) {
        DownloadCommands.resume(appContext, downloadId)
    }

    fun retryDownload(entry: DownloadStatusEntry) {
        DownloadCommands.retry(appContext, entry)
    }

    fun exportDownload(entry: DownloadStatusEntry) {
        DownloadCommands.export(appContext, entry)
    }

    fun removeDownload(entry: DownloadStatusEntry) {
        DownloadCommands.remove(appContext, entry)
    }
}
