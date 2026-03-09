package com.panyou.missnet.nativeapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.panyou.missnet.nativeapp.core.data.VideoRepository
import com.panyou.missnet.nativeapp.core.model.Video
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class FavoritesUiState(
    val videos: List<Video> = emptyList(),
)

class FavoritesViewModel(repository: VideoRepository) : ViewModel() {
    val uiState: StateFlow<FavoritesUiState> = repository.observeFavorites()
        .map { FavoritesUiState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), FavoritesUiState())
}
