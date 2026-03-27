package com.panyou.missnet.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.panyou.missnet.data.repository.VideoRepository
import com.panyou.missnet.data.result.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TagsUiState(
    val tags: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null
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

    fun refresh() {
        loadTags(forceRefresh = true)
    }

    private fun loadTags(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val hasContent = _uiState.value.tags.isNotEmpty()
            _uiState.value = _uiState.value.copy(isLoading = !hasContent, isRefreshing = hasContent, errorMessage = null)
            when (val result = repository.getPopularTagsResult(forceRefresh = forceRefresh)) {
                AppResult.Empty -> {
                    _uiState.value = TagsUiState(tags = emptyList(), isLoading = false, isRefreshing = false)
                }

                is AppResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = result.message
                    )
                }

                is AppResult.Success -> {
                    _uiState.value = TagsUiState(
                        tags = result.data,
                        isLoading = false,
                        isRefreshing = false
                    )
                }
            }
        }
    }
}
