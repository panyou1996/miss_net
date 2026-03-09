package com.panyou.missnet.nativeapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.panyou.missnet.nativeapp.core.model.Video
import com.panyou.missnet.nativeapp.ui.components.EmptyState
import com.panyou.missnet.nativeapp.ui.components.HeaderRow
import com.panyou.missnet.nativeapp.ui.components.VideoCard
import com.panyou.missnet.nativeapp.ui.viewmodel.SearchViewModel

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onBack: () -> Unit,
    onOpenVideo: (Video) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            HeaderRow(
                title = "Search",
                subtitle = "Titles, actors, and categories from the live Supabase catalog.",
                actionIcon = Icons.AutoMirrored.Rounded.ArrowBack,
                onActionClick = onBack,
            )
        }
        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search videos, actors, or categories") },
                singleLine = true,
            )
        }
        if (state.query.isBlank()) {
            if (state.history.isEmpty()) {
                item {
                    EmptyState(title = "Search history is empty", body = "Run a search and it will appear here.")
                }
            } else {
                item {
                    Text("Recent searches", style = MaterialTheme.typography.headlineSmall)
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(state.history) { item ->
                            AssistChip(
                                onClick = {
                                    viewModel.onQueryChange(item)
                                    viewModel.submitQuery(item)
                                },
                                label = { Text(item) },
                            )
                        }
                    }
                }
                item {
                    androidx.compose.material3.TextButton(onClick = viewModel::clearHistory) {
                        Text("Clear history")
                    }
                }
            }
        } else {
            if (state.suggestions.isNotEmpty()) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(state.suggestions) { suggestion ->
                            AssistChip(
                                onClick = {
                                    viewModel.onQueryChange(suggestion)
                                    viewModel.submitQuery(suggestion)
                                },
                                label = { Text(suggestion) },
                            )
                        }
                    }
                }
            }
            if (state.results.isEmpty() && !state.isLoading) {
                item {
                    EmptyState(title = "No results", body = "Try a different title, actor, or category.")
                }
            } else {
                items(state.results) { video ->
                    VideoCard(video = video, onClick = { onOpenVideo(video) })
                }
            }
        }
    }
}
