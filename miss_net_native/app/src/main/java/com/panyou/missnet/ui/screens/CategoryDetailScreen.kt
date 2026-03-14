package com.panyou.missnet.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.panyou.missnet.data.model.Video
import com.panyou.missnet.ui.components.BrowseSummaryCard
import com.panyou.missnet.ui.components.HeroCarouselItem
import com.panyou.missnet.ui.components.MissNetCoverImage
import com.panyou.missnet.ui.components.MissNetListDivider
import com.panyou.missnet.ui.components.MissNetErrorState
import com.panyou.missnet.ui.components.MissNetLoading
import com.panyou.missnet.ui.components.MissNetStatePane
import com.panyou.missnet.ui.components.SecondaryPageSurface
import com.panyou.missnet.ui.components.SmallBadge
import com.panyou.missnet.ui.theme.ContainerTokens
import com.panyou.missnet.ui.theme.MotionTokens
import com.panyou.missnet.ui.theme.ThumbnailShape
import com.panyou.missnet.ui.util.bouncyClick
import com.panyou.missnet.ui.viewmodel.CategoryDetailViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class, ExperimentalLayoutApi::class)
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
    val pageTitle = remember(category, actor) { resolveCategoryPageTitle(category, actor) }
    val pageHelper = if (actor != null) "点击卡片进入播放页，也可继续浏览该演员相关内容。" else "点击卡片可继续进入播放页。"

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

        uiState.videos.isEmpty() -> {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
                    SecondaryPageSurface {
                        Column(modifier = Modifier.fillMaxSize()) {
                            BrowseSummaryCard(
                                title = pageTitle,
                                summary = "当前暂无可浏览内容",
                                helper = "请稍后再试，或返回首页继续浏览其他内容。",
                                modifier = Modifier.padding(ContainerTokens.ScreenContentPadding)
                            )
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                MissNetStatePane(
                                    icon = Icons.Default.PlayArrow,
                                    title = "暂未收录内容",
                                    subtitle = "当前入口还没有可展示的视频内容。",
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        else -> {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
                    // Note: Sort/Filter UI requires backend support for: sort (newest/hottest), filter (subtitle/uncensored/high-res)
                    SecondaryPageSurface {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(vertical = ContainerTokens.ScreenContentPadding),
                            verticalArrangement = Arrangement.spacedBy(ContainerTokens.SectionVerticalSpacing),
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(scrollBehavior.nestedScrollConnection)
                        ) {
                            item {
                                val carouselVideos = uiState.videos.take(5)
                                BrowseSummaryCard(
                                    title = pageTitle,
                                    summary = "共 ${uiState.videos.size} 项 · 默认按最新收录排序",
                                    helper = pageHelper,
                                    footer = {
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            SmallBadge(
                                                text = "列表 ${uiState.videos.size}",
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            SmallBadge(
                                                text = "精选 ${carouselVideos.size}",
                                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                            SmallBadge(
                                                text = "默认最新",
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    modifier = Modifier.padding(horizontal = ContainerTokens.ScreenCompactHorizontalPadding)
                                )
                            }

                            item {
                                val carouselVideos = uiState.videos.take(5)
                                val carouselState = rememberCarouselState { carouselVideos.size }

                                AnimatedVisibility(
                                    visible = carouselVideos.size > 1,
                                    enter = fadeIn(animationSpec = MotionTokens.standard()) + expandVertically(animationSpec = MotionTokens.standard()),
                                    exit = fadeOut(animationSpec = MotionTokens.exit())
                                ) {
                                    HorizontalMultiBrowseCarousel(
                                        state = carouselState,
                                        preferredItemWidth = 320.dp,
                                        itemSpacing = 12.dp,
                                        contentPadding = PaddingValues(horizontal = ContainerTokens.ScreenCompactHorizontalPadding),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(220.dp)
                                    ) { index ->
                                        Box(modifier = Modifier.fillMaxSize().padding(vertical = 4.dp)) {
                                            HeroCarouselItem(
                                                video = carouselVideos[index],
                                                modifier = Modifier.fillMaxSize(),
                                                onClick = { onVideoClick(carouselVideos[index].id) },
                                                sharedTransitionScope = null,
                                                animatedVisibilityScope = null
                                            )
                                        }
                                    }
                                }
                            }

                            itemsIndexed(uiState.videos, key = { _, video -> video.id }) { index, video ->
                                CategoryVideoItem(
                                    video = video,
                                    onClick = { onVideoClick(video.id) },
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = animatedVisibilityScope
                                )
                                if (index < uiState.videos.lastIndex) {
                                    MissNetListDivider()
                                }
                            }

                            if (uiState.isMoreLoading) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                                    }
                                }
                            }

                            item { Spacer(modifier = Modifier.height(ContainerTokens.ScreenBottomPadding)) }
                        }
                    }
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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = ContainerTokens.ScreenContentPadding,
                vertical = 2.dp
            ),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .bouncyClick { onClick() }
                .padding(
                    horizontal = ContainerTokens.ListRowHorizontalPadding,
                    vertical = ContainerTokens.ListRowVerticalPadding
                ),
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
                        .clip(ThumbnailShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .then(imageModifier)
                ) {
                    MissNetCoverImage(
                        coverUrl = video.coverUrl,
                        contentDescription = video.title,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = video.tags.joinToString(" · ").ifEmpty { "暂无标签" },
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
                        text = "${video.createdAt?.take(10) ?: "最近更新"} · ${video.duration ?: "高清"}",
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
                modifier = Modifier.size(ContainerTokens.ListTrailingIconSize)
            )
        }
    }
}

private fun resolveCategoryPageTitle(category: String?, actor: String?): String {
    actor?.takeIf { it.isNotBlank() }?.let { return it }
    return when (category) {
        "new" -> "最新发布"
        "monthly_hot" -> "本月热选"
        "weekly_hot" -> "本周热门"
        "uncensored" -> "无码资源"
        "subtitled" -> "字幕内容"
        else -> "内容列表"
    }
}
