package com.panyou.missnet.nativeapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.panyou.missnet.nativeapp.core.model.Video
import com.panyou.missnet.nativeapp.ui.components.EmptyState
import com.panyou.missnet.nativeapp.ui.components.HeaderRow
import com.panyou.missnet.nativeapp.ui.components.VideoCard
import com.panyou.missnet.nativeapp.ui.viewmodel.FavoritesViewModel

@Composable
fun FavoritesScreen(
    viewModel: FavoritesViewModel,
    onOpenVideo: (Video) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.videos.isEmpty()) {
        androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxSize()) {
            HeaderRow(title = "Favorites", subtitle = "Saved locally in Room for quick access.")
            EmptyState(title = "No favorites yet", body = "Save videos from the player and they will appear here.")
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(220.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            HeaderRow(title = "Favorites", subtitle = "Saved locally in Room for quick access.")
        }
        items(state.videos) { video ->
            VideoCard(video = video, onClick = { onOpenVideo(video) })
        }
    }
}
