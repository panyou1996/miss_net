package com.panyou.missnet.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest

@Composable
fun MissNetCoverImage(
    coverUrl: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    loadingLabel: String = "封面加载中",
    emptyLabel: String = "暂无封面"
) {
    val context = LocalContext.current
    val request = remember(coverUrl, context) {
        coverUrl
            ?.takeIf { it.isNotBlank() }
            ?.let {
                ImageRequest.Builder(context)
                    .data(it)
                    .crossfade(true)
                    .build()
            }
    }

    if (request != null) {
        SubcomposeAsyncImage(
            model = request,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier,
            loading = { MediaPlaceholder(label = loadingLabel) },
            error = { MediaPlaceholder(label = emptyLabel) }
        )
    } else {
        MediaPlaceholder(
            modifier = modifier,
            label = emptyLabel
        )
    }
}
