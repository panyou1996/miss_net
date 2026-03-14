package com.panyou.missnet.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.panyou.missnet.data.local.LocalVideoStateStore
import com.panyou.missnet.data.model.Video
import com.panyou.missnet.data.repository.VideoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val active: Boolean = false,
    val results: List<Video> = emptyList(),
    val history: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val endReached: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: VideoRepository,
    private val localStore: LocalVideoStateStore
) : ViewModel() {
    private val pageSize = 20

    private val _uiState = MutableStateFlow(
        SearchUiState(history = localStore.getSearchHistory())
    )
    val uiState: StateFlow<SearchUiState> = _uiState

    fun onQueryChange(newQuery: String) {
        _uiState.value = _uiState.value.copy(query = newQuery)
    }

    fun onActiveChange(isActive: Boolean) {
        _uiState.value = _uiState.value.copy(active = isActive)
    }

    fun loadSearchHistory() {
        _uiState.value = _uiState.value.copy(history = localStore.getSearchHistory())
    }

    fun removeHistoryItem(query: String) {
        localStore.removeSearchHistory(query)
        loadSearchHistory()
    }

    fun clearSearchHistory() {
        localStore.clearSearchHistory()
        loadSearchHistory()
    }

    fun retry() {
        val query = _uiState.value.query
        if (query.isNotBlank()) {
            search(query)
        }
    }

    fun search(query: String) {
        val normalized = query.trim()
        if (normalized.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                isLoadingMore = false,
                query = normalized,
                active = false,
                endReached = false,
                results = emptyList(),
                errorMessage = null
            )
            try {
                val results = repository.searchVideos(normalized, limit = pageSize, offset = 0)
                localStore.addSearchHistory(normalized)
                _uiState.value = _uiState.value.copy(
                    results = results,
                    history = localStore.getSearchHistory(),
                    isLoading = false,
                    isLoadingMore = false,
                    active = false,
                    endReached = results.size < pageSize,
                    errorMessage = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    results = emptyList(),
                    isLoading = false,
                    isLoadingMore = false,
                    active = false,
                    endReached = true,
                    errorMessage = e.message ?: "搜索失败，请检查网络后重试。"
                )
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        val query = state.query.trim()
        if (query.isBlank() || state.isLoading || state.isLoadingMore || state.endReached) return

        viewModelScope.launch {
            _uiState.value = state.copy(isLoadingMore = true, errorMessage = null)
            runCatching {
                repository.searchVideos(query, limit = pageSize, offset = state.results.size)
            }.onSuccess { more ->
                _uiState.value = _uiState.value.copy(
                    results = _uiState.value.results + more,
                    isLoadingMore = false,
                    endReached = more.size < pageSize
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoadingMore = false,
                    errorMessage = error.message ?: "加载更多失败，请稍后重试。"
                )
            }
        }
    }
}
