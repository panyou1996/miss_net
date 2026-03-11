package com.panyou.missnet.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.panyou.missnet.data.repository.VideoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TagsUiState(
    val tags: List<String> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class TagsViewModel @Inject constructor(
    private val repository: VideoRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TagsUiState())
    val uiState: StateFlow<TagsUiState> = _uiState

    init {
        loadTags()
    }

    private fun loadTags() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val list = repository.getPopularTags()
            _uiState.value = TagsUiState(tags = list, isLoading = false)
        }
    }
}
