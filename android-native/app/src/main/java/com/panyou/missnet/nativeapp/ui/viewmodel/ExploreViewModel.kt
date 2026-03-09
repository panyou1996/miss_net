package com.panyou.missnet.nativeapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.panyou.missnet.nativeapp.core.data.VideoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ExploreUiState(
    val isLoading: Boolean = true,
    val popularActors: List<String> = emptyList(),
    val popularTags: List<String> = emptyList(),
)

class ExploreViewModel(
    private val repository: VideoRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = ExploreUiState(isLoading = true)
            val actors = runCatching { repository.getPopularActors() }.getOrDefault(emptyList())
            val tags = runCatching { repository.getPopularTags() }.getOrDefault(emptyList())
            _uiState.value = ExploreUiState(
                isLoading = false,
                popularActors = actors,
                popularTags = tags,
            )
        }
    }
}
