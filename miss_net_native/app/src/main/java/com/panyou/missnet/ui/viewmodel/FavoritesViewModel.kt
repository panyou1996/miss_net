package com.panyou.missnet.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.panyou.missnet.data.local.LocalVideoStateStore
import com.panyou.missnet.data.model.Video
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FavoritesUiState(
    val isLoading: Boolean = true,
    val videos: List<Video> = emptyList()
)

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val localStore: LocalVideoStateStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _uiState

    init {
        loadFavorites()
    }

    fun loadFavorites() {
        viewModelScope.launch {
            _uiState.value = FavoritesUiState(
                isLoading = false,
                videos = localStore.getFavorites()
            )
        }
    }
}
