package com.panyou.missnet.nativeapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.panyou.missnet.nativeapp.core.data.VideoRepository
import com.panyou.missnet.nativeapp.core.model.HomeSection
import com.panyou.missnet.nativeapp.core.model.Video
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val sections: List<HomeSection> = emptyList(),
    val continueWatching: List<Video> = emptyList(),
    val error: String? = null,
)

class HomeViewModel(
    private val repository: VideoRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = HomeUiState(isLoading = true)
            runCatching { repository.loadHomeSections() }
                .onSuccess { (sections, history) ->
                    _uiState.value = HomeUiState(
                        isLoading = false,
                        sections = sections,
                        continueWatching = history,
                    )
                }
                .onFailure { error ->
                    _uiState.value = HomeUiState(
                        isLoading = false,
                        error = error.message ?: "Failed to load home feed.",
                    )
                }
        }
    }
}
