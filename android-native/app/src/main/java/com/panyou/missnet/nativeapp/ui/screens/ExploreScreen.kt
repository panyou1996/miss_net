package com.panyou.missnet.nativeapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.panyou.missnet.nativeapp.ui.components.GlassCard
import com.panyou.missnet.nativeapp.ui.components.HeaderRow
import com.panyou.missnet.nativeapp.ui.components.LoadingState
import com.panyou.missnet.nativeapp.ui.components.OverlineLabel
import com.panyou.missnet.nativeapp.ui.viewmodel.ExploreViewModel

private val exploreCategories = listOf(
    "51 Eating Melon" to "51cg",
    "School" to "School",
    "Office" to "Office",
    "Mature" to "Mature",
    "Exclusive" to "Exclusive",
    "Nympho" to "Nympho",
    "Voyeur" to "Voyeur",
    "Sister" to "Sister",
    "Story" to "Story",
    "Subtitled" to "Subtitled",
    "Uncensored" to "uncensored",
    "Amateur" to "Amateur",
    "Big Tits" to "BigTits",
    "Creampie" to "Creampie",
    "Beautiful" to "Beautiful",
    "Oral" to "Oral",
    "Group" to "Group",
)

@Composable
fun ExploreScreen(
    viewModel: ExploreViewModel,
    onSearch: () -> Unit,
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
                title = "Explore",
                subtitle = "Actors, tags, and genre shortcuts backed by the live catalog.",
                actionIcon = Icons.Rounded.Search,
                onActionClick = onSearch,
            )
        }

        if (state.popularActors.isNotEmpty()) {
            item {
                GlassCard {
                    OverlineLabel(text = "popular actresses")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(state.popularActors) { actor ->
                            AssistChip(
                                onClick = { onOpenFeed(actor, null, actor) },
                                label = { Text(actor) },
                            )
                        }
                    }
                }
            }
        }

        if (state.popularTags.isNotEmpty()) {
            item {
                GlassCard {
                    OverlineLabel(text = "trending topics")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(state.popularTags.take(20)) { tag ->
                            AssistChip(
                                onClick = { onOpenFeed(tag, tag, null) },
                                label = { Text(tag) },
                            )
                        }
                    }
                }
            }
        }

        item {
            Text("Browse Categories", style = MaterialTheme.typography.headlineSmall)
        }

        items(exploreCategories.chunked(2)) { row ->
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                row.forEach { (label, category) ->
                    GlassCard(
                        modifier = Modifier.weight(1f),
                    ) {
                        OverlineLabel(text = "category")
                        Text(label, style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "Open ${label.lowercase()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                        )
                        androidx.compose.material3.TextButton(
                            onClick = { onOpenFeed(label, category, null) },
                            modifier = Modifier.padding(top = 6.dp),
                        ) {
                            Text("Open")
                        }
                    }
                }
                if (row.size == 1) {
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
