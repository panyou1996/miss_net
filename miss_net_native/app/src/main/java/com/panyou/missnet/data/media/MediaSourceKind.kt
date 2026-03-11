@file:androidx.media3.common.util.UnstableApi

package com.panyou.missnet.data.media

import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.media3.common.MimeTypes
import java.util.Locale

enum class MediaSourceKind {
    DIRECT_VIDEO,
    HLS,
    UNKNOWN
}

object MediaSourceClassifier {
    private val directVideoExtensions = setOf("mp4", "m4v", "webm", "mkv", "mov", "avi", "3gp")

    fun classify(sourceUri: String?, mimeType: String?): MediaSourceKind {
        val normalizedMime = mimeType?.lowercase(Locale.US).orEmpty()
        val extension = sourceUri
            ?.let { MimeTypeMap.getFileExtensionFromUrl(it)?.substringBefore('?')?.lowercase(Locale.US) }
            .orEmpty()

        return when {
            normalizedMime.contains("mpegurl") || normalizedMime.contains("m3u8") || sourceUri.orEmpty().contains(".m3u8", ignoreCase = true) -> MediaSourceKind.HLS
            normalizedMime.startsWith("video/") && !normalizedMime.contains("mpegurl") -> MediaSourceKind.DIRECT_VIDEO
            extension in directVideoExtensions -> MediaSourceKind.DIRECT_VIDEO
            else -> MediaSourceKind.UNKNOWN
        }
    }

    fun inferDownloadMimeType(sourceUri: String?): String? {
        val extension = sourceUri
            ?.let { Uri.parse(it).lastPathSegment }
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.substringBefore('?')
            ?.lowercase(Locale.US)
            .orEmpty()

        return when (extension) {
            "m3u8" -> MimeTypes.APPLICATION_M3U8
            "mp4", "m4v" -> MimeTypes.VIDEO_MP4
            "webm" -> MimeTypes.VIDEO_WEBM
            "mkv" -> "video/x-matroska"
            "mov" -> "video/quicktime"
            "avi" -> "video/x-msvideo"
            "3gp" -> "video/3gpp"
            else -> null
        }
    }
}
