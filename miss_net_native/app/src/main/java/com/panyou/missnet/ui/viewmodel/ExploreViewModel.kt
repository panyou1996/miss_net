package com.panyou.missnet.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.panyou.missnet.data.repository.VideoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.panyou.missnet.data.model.ActorInfo

data class ExploreUiState(
    val isLoading: Boolean = true,
    val actors: List<ActorInfo> = emptyList(), // Trending (Top 20)
    val groupedActors: Map<Char, List<ActorInfo>> = emptyMap(), // A-Z Index
    val tags: List<String> = emptyList()
)

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val repository: VideoRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState

    init {
        loadExploreData()
    }

    private fun loadExploreData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            // Fetch actors with covers
            val allActors = repository.getActorsWithCovers(limit = 300)
            val tags = repository.getPopularTags()
            
            val trendingActors = allActors.take(21)
            val grouped = allActors
                .filter { it.name.isNotEmpty() }
                .sortedBy { it.name }
                .groupBy { it.name.first().uppercaseChar() }
                .toSortedMap()

            _uiState.value = ExploreUiState(
                isLoading = false, 
                actors = trendingActors, 
                groupedActors = grouped,
                tags = tags
            )
        }
    }
}
