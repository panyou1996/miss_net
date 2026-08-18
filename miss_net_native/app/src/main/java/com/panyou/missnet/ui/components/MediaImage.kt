package com.panyou.missnet.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.panyou.missnet.data.media.SourceRequestHeaders

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
            ?.takeIf { it.isNotBlank() && !it.startsWith("data:image") }
            ?.let { url ->
                val referer = if (url.contains("51cg") || url.contains("pic.")) "https://51cg1.com/" else "https://missav.ws/"
                ImageRequest.Builder(context)
                    .data(url)
                    .addHeader("Referer", referer)
                    .addHeader("User-Agent", SourceRequestHeaders.browserUserAgent)
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
