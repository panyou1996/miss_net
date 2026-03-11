@file:androidx.media3.common.util.UnstableApi

package com.panyou.missnet.data.media

import android.content.Context
import android.net.Uri
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.panyou.missnet.service.MissNetDownloadService

object DownloadCommands {
    const val STOP_REASON_NONE = 0
    const val STOP_REASON_PAUSED_BY_USER = 1

    fun pause(context: Context, downloadId: String) {
        DownloadService.sendSetStopReason(
            context,
            MissNetDownloadService::class.java,
            downloadId,
            STOP_REASON_PAUSED_BY_USER,
            false
        )
    }

    fun resume(context: Context, downloadId: String) {
        DownloadService.sendSetStopReason(
            context,
            MissNetDownloadService::class.java,
            downloadId,
            STOP_REASON_NONE,
            false
        )
    }

    fun remove(context: Context, entry: DownloadStatusEntry) {
        PublicVideoExporter.removeExported(context, entry)
        DownloadService.sendRemoveDownload(
            context,
            MissNetDownloadService::class.java,
            entry.id,
            false
        )
    }

    fun export(context: Context, entry: DownloadStatusEntry) {
        PublicVideoExporter.export(context, entry)
    }

    fun retry(context: Context, entry: DownloadStatusEntry) {
        if (entry.exportState == ExportState.EXPORTED || entry.exportState == ExportState.EXPORT_FAILED || entry.exportState == ExportState.EXPORTING || entry.exportState == ExportState.EXPORT_QUEUED) {
            PublicVideoExporter.removeExported(context, entry)
        }
        val uri = entry.requestUri ?: entry.sourceUrl ?: return
        val metadata = DownloadMetadata(
            id = entry.id,
            title = entry.title,
            coverUrl = entry.coverUrl,
            sourceUrl = entry.sourceUrl,
            requestUri = uri,
            mimeType = entry.mimeType ?: MediaSourceClassifier.inferDownloadMimeType(uri)
        )
        val request = DownloadRequest.Builder(entry.id, Uri.parse(uri))
            .setMimeType(entry.mimeType ?: MediaSourceClassifier.inferDownloadMimeType(uri))
            .setData(metadata.toByteArray())
            .build()
        DownloadService.sendAddDownload(
            context,
            MissNetDownloadService::class.java,
            request,
            STOP_REASON_NONE,
            false
        )
    }
}
