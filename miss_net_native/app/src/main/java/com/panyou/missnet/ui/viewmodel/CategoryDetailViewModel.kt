package com.panyou.missnet.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.panyou.missnet.data.model.Video
import com.panyou.missnet.data.repository.VideoRepository
import com.panyou.missnet.data.result.AppResult
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
            when (val result = fetchData(0)) {
                AppResult.Empty -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        videos = emptyList(),
                        endOfPaginationReached = true,
                        errorMessage = null
                    )
                    currentOffset = 0
                }

                is AppResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        videos = emptyList(),
                        endOfPaginationReached = false,
                        errorMessage = result.message
                    )
                }

                is AppResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        videos = result.data,
                        endOfPaginationReached = result.data.size < pageSize,
                        errorMessage = null
                    )
                    currentOffset = result.data.size
                }
            }
        }
    }

    fun loadMore() {
        if (_uiState.value.isMoreLoading || _uiState.value.endOfPaginationReached || _uiState.value.errorMessage != null) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isMoreLoading = true)
            when (val result = fetchData(currentOffset)) {
                AppResult.Empty -> {
                    _uiState.value = _uiState.value.copy(
                        isMoreLoading = false,
                        endOfPaginationReached = true
                    )
                }

                is AppResult.Failure -> {
                    _uiState.value = _uiState.value.copy(isMoreLoading = false)
                }

                is AppResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isMoreLoading = false,
                        videos = _uiState.value.videos + result.data,
                        endOfPaginationReached = result.data.size < pageSize
                    )
                    currentOffset += result.data.size
                }
            }
        }
    }

    private suspend fun fetchData(offset: Int): AppResult<List<Video>> {
        return if (currentActor != null) {
            repository.getVideosByActorResult(currentActor!!, pageSize, offset)
        } else if (currentCategory != null) {
            repository.getVideosByCategoryResult(currentCategory!!, pageSize, offset)
        } else {
            AppResult.Empty
        }
    }
}
