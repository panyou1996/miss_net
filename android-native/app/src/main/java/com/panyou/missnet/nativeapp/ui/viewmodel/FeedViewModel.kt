package com.panyou.missnet.nativeapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.panyou.missnet.nativeapp.core.data.VideoRepository
import com.panyou.missnet.nativeapp.core.model.Video
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FeedUiState(
    val isLoading: Boolean = true,
    val title: String = "",
    val videos: List<Video> = emptyList(),
    val error: String? = null,
)

class FeedViewModel(
    private val repository: VideoRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    fun load(title: String, category: String?, actor: String?) {
        if (_uiState.value.title == title && (_uiState.value.videos.isNotEmpty() || _uiState.value.error != null)) return
        viewModelScope.launch {
            _uiState.value = FeedUiState(isLoading = true, title = title)
            runCatching { repository.loadFeed(category = category?.takeIf { it.isNotBlank() }, actor = actor?.takeIf { it.isNotBlank() }) }
                .onSuccess { videos ->
                    _uiState.value = FeedUiState(
                        isLoading = false,
                        title = title,
                        videos = videos,
                    )
                }
                .onFailure { error ->
                    _uiState.value = FeedUiState(
                        isLoading = false,
                        title = title,
                        error = error.message ?: "Failed to load videos.",
                    )
                }
        }
    }
}
