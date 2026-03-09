package com.panyou.missnet.nativeapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.panyou.missnet.nativeapp.core.model.Video
import com.panyou.missnet.nativeapp.ui.components.CompactVideoCard
import com.panyou.missnet.nativeapp.ui.components.EmptyState
import com.panyou.missnet.nativeapp.ui.components.GlassCard
import com.panyou.missnet.nativeapp.ui.components.HeaderRow
import com.panyou.missnet.nativeapp.ui.components.LoadingState
import com.panyou.missnet.nativeapp.ui.components.OverlineLabel
import com.panyou.missnet.nativeapp.ui.components.VideoCard
import com.panyou.missnet.nativeapp.ui.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onSearch: () -> Unit,
    onOpenVideo: (Video) -> Unit,
    onOpenFeed: (title: String, category: String?, actor: String?) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.isLoading) {
        LoadingState()
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            HeaderRow(
                title = "MissNet",
                subtitle = "Android-native build with offline playback and smooth Compose flows.",
                actionIcon = Icons.Rounded.Search,
                onActionClick = onSearch,
            )
        }

        state.error?.let { error ->
            item {
                EmptyState(title = "Home feed unavailable", body = error)
            }
        }

        if (state.sections.isNotEmpty()) {
            val featured = state.sections.first().videos.take(5)
            if (featured.isNotEmpty()) {
                item {
                    GlassCard {
                        OverlineLabel(text = "featured")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(featured) { video ->
                                CompactVideoCard(video = video, onClick = { onOpenVideo(video) })
                            }
                        }
                    }
                }
            }
        }

        if (state.continueWatching.isNotEmpty()) {
            item {
                Text("Continue Watching", style = MaterialTheme.typography.headlineSmall)
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(state.continueWatching) { video ->
                        CompactVideoCard(video = video, onClick = { onOpenVideo(video) })
                    }
                }
            }
        }

        state.sections.forEach { section ->
            item {
                HeaderRow(
                    title = section.title,
                    subtitle = "Fresh from Supabase",
                    actionIcon = Icons.Rounded.Search,
                    onActionClick = { onOpenFeed(section.title, section.category.value, null) },
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(section.videos) { video ->
                        VideoCard(
                            video = video,
                            modifier = Modifier.width(320.dp),
                            onClick = { onOpenVideo(video) },
                        )
                    }
                }
            }
        }
    }
}
