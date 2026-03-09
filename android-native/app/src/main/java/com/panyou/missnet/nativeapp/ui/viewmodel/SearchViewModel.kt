package com.panyou.missnet.nativeapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.panyou.missnet.nativeapp.core.data.VideoRepository
import com.panyou.missnet.nativeapp.core.model.Video
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val history: List<String> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val results: List<Video> = emptyList(),
    val isLoading: Boolean = false,
)

class SearchViewModel(
    private val repository: VideoRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()
    private var searchJob: Job? = null

    init {
        repository.observeSearchHistory()
            .onEach { history -> _uiState.update { it.copy(history = history) } }
            .launchIn(viewModelScope)
    }

    fun onQueryChange(value: String) {
        _uiState.update { it.copy(query = value) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (value.isBlank()) {
                _uiState.update { it.copy(results = emptyList(), suggestions = emptyList(), isLoading = false) }
                return@launch
            }
            delay(220)
            _uiState.update { it.copy(isLoading = true) }
            val suggestions = runCatching { repository.getSearchSuggestions(value) }.getOrDefault(emptyList())
            val results = runCatching { repository.searchVideos(value) }.getOrDefault(emptyList())
            _uiState.update {
                it.copy(
                    suggestions = suggestions,
                    results = results,
                    isLoading = false,
                )
            }
        }
    }

    fun submitQuery(value: String = _uiState.value.query) {
        viewModelScope.launch {
            repository.saveSearchQuery(value)
            onQueryChange(value)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearSearchHistory()
        }
    }
}
