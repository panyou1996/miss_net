package com.panyou.missnet.nativeapp.core.model

enum class DownloadStatus {
    Queued,
    Downloading,
    Completed,
    Failed,
    Removing,
    Stopped,
}

data class DownloadRecord(
    val video: Video,
    val streamUrl: String,
    val mimeType: String?,
    val headers: Map<String, String>,
    val status: DownloadStatus,
    val progressPercent: Float,
    val bytesDownloaded: Long,
    val contentLength: Long,
    val addedAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val completedAtEpochMs: Long? = null,
    val failureReason: String? = null,
) {
    val isTerminal: Boolean
        get() = status == DownloadStatus.Completed || status == DownloadStatus.Failed
}
