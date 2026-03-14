package com.panyou.missnet.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.panyou.missnet.data.model.ActorInfo
import com.panyou.missnet.data.repository.VideoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ActressUiState(
    val actresses: List<ActorInfo> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false
)

@HiltViewModel
class ActressViewModel @Inject constructor(
    private val repository: VideoRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ActressUiState())
    val uiState: StateFlow<ActressUiState> = _uiState

    init {
        loadActresses()
    }

    fun refresh() {
        loadActresses(forceRefresh = true)
    }

    private fun loadActresses(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val hasContent = _uiState.value.actresses.isNotEmpty()
            _uiState.value = _uiState.value.copy(isLoading = !hasContent, isRefreshing = hasContent)
            val list = repository.getActorsWithCovers(limit = 100, forceRefresh = forceRefresh)
            _uiState.value = ActressUiState(actresses = list, isLoading = false, isRefreshing = false)
        }
    }
}
