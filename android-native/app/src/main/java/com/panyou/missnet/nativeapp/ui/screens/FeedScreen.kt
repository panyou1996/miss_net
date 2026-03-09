package com.panyou.missnet.nativeapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.panyou.missnet.nativeapp.core.model.Video
import com.panyou.missnet.nativeapp.ui.components.EmptyState
import com.panyou.missnet.nativeapp.ui.components.HeaderRow
import com.panyou.missnet.nativeapp.ui.components.LoadingState
import com.panyou.missnet.nativeapp.ui.components.VideoCard
import com.panyou.missnet.nativeapp.ui.viewmodel.FeedViewModel

@Composable
fun FeedScreen(
    viewModel: FeedViewModel,
    title: String,
    category: String,
    actor: String,
    onBack: () -> Unit,
    onOpenVideo: (Video) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(title, category, actor) {
        viewModel.load(title = title, category = category, actor = actor)
    }

    if (state.isLoading) {
        LoadingState()
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(220.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            HeaderRow(
                title = title,
                subtitle = if (actor.isNotBlank()) "Videos featuring $actor" else "Latest videos for $title",
                actionIcon = Icons.AutoMirrored.Rounded.ArrowBack,
                onActionClick = onBack,
            )
        }
        if (state.error != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(title = "Feed unavailable", body = state.error ?: "Unknown error")
            }
        } else {
            items(state.videos) { video ->
                VideoCard(video = video, onClick = { onOpenVideo(video) })
            }
        }
    }
}
