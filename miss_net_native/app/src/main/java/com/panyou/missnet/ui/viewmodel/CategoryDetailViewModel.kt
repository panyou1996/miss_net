package com.panyou.missnet.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.panyou.missnet.data.model.Video
import com.panyou.missnet.data.repository.VideoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CategoryDetailUiState(
    val isLoading: Boolean = false,
    val isMoreLoading: Boolean = false,
    val videos: List<Video> = emptyList(),
    val endOfPaginationReached: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class CategoryDetailViewModel @Inject constructor(
    private val repository: VideoRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoryDetailUiState())
    val uiState: StateFlow<CategoryDetailUiState> = _uiState

    private var currentCategory: String? = null
    private var currentActor: String? = null
    private var currentOffset = 0
    private val pageSize = 20

    fun init(category: String?, actor: String?) {
        if (currentCategory == category && currentActor == actor && (_uiState.value.videos.isNotEmpty() || _uiState.value.isLoading)) return

        currentCategory = category
        currentActor = actor
        currentOffset = 0

        loadInitial()
    }

    fun retry() {
        currentOffset = 0
        loadInitial()
    }

    private fun loadInitial() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, videos = emptyList(), errorMessage = null)
            try {
                val videos = fetchData(0)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    videos = videos,
                    endOfPaginationReached = videos.size < pageSize,
                    errorMessage = if (videos.isEmpty()) "当前分类暂无内容，或加载失败。" else null
                )
                currentOffset = videos.size
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    videos = emptyList(),
                    endOfPaginationReached = true,
                    errorMessage = e.message ?: "分类加载失败，请重试。"
                )
            }
        }
    }

    fun loadMore() {
        if (_uiState.value.isMoreLoading || _uiState.value.endOfPaginationReached || _uiState.value.errorMessage != null) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isMoreLoading = true)
            try {
                val moreVideos = fetchData(currentOffset)
                _uiState.value = _uiState.value.copy(
                    isMoreLoading = false,
                    videos = _uiState.value.videos + moreVideos,
                    endOfPaginationReached = moreVideos.size < pageSize
                )
                currentOffset += moreVideos.size
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isMoreLoading = false)
            }
        }
    }

    private suspend fun fetchData(offset: Int): List<Video> {
        return if (currentActor != null) {
            repository.getVideosByActor(currentActor!!, pageSize, offset)
        } else if (currentCategory != null) {
            repository.getVideosByCategory(currentCategory!!, pageSize, offset)
        } else {
            emptyList()
        }
    }
}
