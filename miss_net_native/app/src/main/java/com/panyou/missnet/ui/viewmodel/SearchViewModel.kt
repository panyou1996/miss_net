package com.panyou.missnet.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.panyou.missnet.data.local.LocalVideoStateStore
import com.panyou.missnet.data.model.Video
import com.panyou.missnet.data.repository.VideoRepository
import com.panyou.missnet.data.result.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "SearchViewModel"
private const val SEARCH_DEBOUNCE_MS = 350L
private const val MIN_QUERY_LENGTH = 2

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

    // U-1: Debounced auto-search — cancels previous timer on each keystroke
    private var debounceJob: Job? = null

    init {
        viewModelScope.launch {
            localStore.observeSearchHistory().collect { history ->
                _uiState.value = _uiState.value.copy(history = history)
            }
        }
    }

    fun onQueryChange(newQuery: String) {
        _uiState.value = _uiState.value.copy(query = newQuery)

        // Cancel any pending auto-search
        debounceJob?.cancel()

        if (newQuery.length >= MIN_QUERY_LENGTH) {
            debounceJob = viewModelScope.launch {
                delay(SEARCH_DEBOUNCE_MS)
                performSearch(newQuery.trim())
            }
        }
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

    // Called when user explicitly presses Enter / search button — bypasses debounce
    fun search(query: String) {
        val normalized = query.trim()
        if (normalized.isBlank()) return

        // Cancel any pending debounce so it doesn't fire after explicit search
        debounceJob?.cancel()

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
            when (val result = repository.searchVideosResult(normalized, limit = pageSize, offset = 0)) {
                AppResult.Empty -> {
                    localStore.addSearchHistory(normalized)
                    _uiState.value = _uiState.value.copy(
                        results = emptyList(),
                        history = localStore.getSearchHistory(),
                        isLoading = false,
                        isLoadingMore = false,
                        active = false,
                        endReached = true,
                        errorMessage = null
                    )
                }

                is AppResult.Failure -> {
                    Log.e(TAG, "search failed for query=$normalized", result.cause)
                    _uiState.value = _uiState.value.copy(
                        results = emptyList(),
                        isLoading = false,
                        isLoadingMore = false,
                        active = false,
                        endReached = false,
                        errorMessage = result.message
                    )
                }

                is AppResult.Success -> {
                    localStore.addSearchHistory(normalized)
                    _uiState.value = _uiState.value.copy(
                        results = result.data,
                        history = localStore.getSearchHistory(),
                        isLoading = false,
                        isLoadingMore = false,
                        active = false,
                        endReached = result.data.size < pageSize,
                        errorMessage = null
                    )
                }
            }
        }
    }

    private suspend fun performSearch(normalized: String) {
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            isLoadingMore = false,
            active = false,
            endReached = false,
            errorMessage = null
        )
        when (val result = repository.searchVideosResult(normalized, limit = pageSize, offset = 0)) {
            AppResult.Empty -> {
                localStore.addSearchHistory(normalized)
                _uiState.value = _uiState.value.copy(
                    results = emptyList(),
                    history = localStore.getSearchHistory(),
                    isLoading = false,
                    isLoadingMore = false,
                    active = false,
                    endReached = true,
                    errorMessage = null
                )
            }

            is AppResult.Failure -> {
                Log.e(TAG, "performSearch (debounce) failed for query=$normalized", result.cause)
                _uiState.value = _uiState.value.copy(
                    results = emptyList(),
                    isLoading = false,
                    isLoadingMore = false,
                    active = false,
                    endReached = false,
                    errorMessage = result.message
                )
            }

            is AppResult.Success -> {
                localStore.addSearchHistory(normalized)
                _uiState.value = _uiState.value.copy(
                    results = result.data,
                    history = localStore.getSearchHistory(),
                    isLoading = false,
                    isLoadingMore = false,
                    active = false,
                    endReached = result.data.size < pageSize,
                    errorMessage = null
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
            when (val result = repository.searchVideosResult(query, limit = pageSize, offset = state.results.size)) {
                AppResult.Empty -> {
                    _uiState.value = _uiState.value.copy(
                        isLoadingMore = false,
                        endReached = true
                    )
                }

                is AppResult.Failure -> {
                    Log.e(TAG, "loadMore failed for query=$query", result.cause)
                    _uiState.value = _uiState.value.copy(
                        isLoadingMore = false,
                        errorMessage = result.message
                    )
                }

                is AppResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        results = _uiState.value.results + result.data,
                        isLoadingMore = false,
                        endReached = result.data.size < pageSize
                    )
                }
            }
        }
    }
}
