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
    val isLoading: Boolean = true
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

    private fun loadActresses() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val list = repository.getActorsWithCovers(limit = 100)
            _uiState.value = ActressUiState(actresses = list, isLoading = false)
        }
    }
}
