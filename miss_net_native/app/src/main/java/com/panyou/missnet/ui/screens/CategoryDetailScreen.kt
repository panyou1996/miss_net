package com.panyou.missnet.ui.screens

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.panyou.missnet.data.model.Video
import com.panyou.missnet.ui.components.HeroCarouselItem
import com.panyou.missnet.ui.components.MissNetErrorState
import com.panyou.missnet.ui.components.MissNetLoading
import com.panyou.missnet.ui.util.bouncyClick
import com.panyou.missnet.ui.viewmodel.CategoryDetailViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun CategoryDetailScreen(
    category: String?,
    actor: String?,
    onVideoClick: (String) -> Unit,
    viewModel: CategoryDetailViewModel = hiltViewModel(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    scrollBehavior: TopAppBarScrollBehavior,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(category, actor) {
        viewModel.init(category, actor)
    }

    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisibleItemIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleItemIndex >= uiState.videos.size - 4 && !uiState.isMoreLoading && !uiState.endOfPaginationReached
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) {
            viewModel.loadMore()
        }
    }

    when {
        uiState.isLoading -> MissNetLoading()
        uiState.errorMessage != null && uiState.videos.isEmpty() -> {
            MissNetErrorState(
                message = uiState.errorMessage ?: "分类加载失败",
                onRetry = viewModel::retry
            )
        }
        else -> {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                LazyColumn(
                    state = listState,
                    contentPadding = contentPadding,
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                ) {
                    if (uiState.videos.isNotEmpty()) {
                        item {
                            val carouselVideos = uiState.videos.take(5)
                            val carouselState = rememberCarouselState { carouselVideos.size }

                            HorizontalMultiBrowseCarousel(
                                state = carouselState,
                                preferredItemWidth = 320.dp,
                                itemSpacing = 8.dp,
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .padding(vertical = 16.dp)
                            ) { index ->
                                HeroCarouselItem(
                                    video = carouselVideos[index],
                                    onClick = { onVideoClick(carouselVideos[index].id) },
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = animatedVisibilityScope
                                )
                            }
                        }
                    }

                    itemsIndexed(uiState.videos) { _, video ->
                        CategoryVideoItem(
                            video = video,
                            onClick = { onVideoClick(video.id) },
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 120.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }

                    if (uiState.isMoreLoading) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun CategoryVideoItem(
    video: Video,
    onClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClick { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        with(sharedTransitionScope) {
            val imageModifier = if (this != null && animatedVisibilityScope != null) {
                Modifier.sharedElement(
                    state = rememberSharedContentState(key = "image-${video.id}"),
                    animatedVisibilityScope = animatedVisibilityScope
                )
            } else {
                Modifier
            }

            Box(
                modifier = Modifier
                    .size(width = 100.dp, height = 70.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .then(imageModifier)
            ) {
                AsyncImage(
                    model = video.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = video.tags.joinToString(", ").ifEmpty { "No tags available" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AddCircleOutline,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${video.createdAt?.take(10) ?: "Recent"} • ${video.duration ?: "HD"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}
