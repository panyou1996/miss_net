@file:androidx.media3.common.util.UnstableApi

package com.panyou.missnet.data.media

import android.content.Context
import android.content.SharedPreferences
import androidx.media3.exoplayer.offline.Download
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import kotlin.math.roundToLong

object DownloadTracker {
    private const val PREFS_NAME = "missnet_downloads"
    private const val KEY_DOWNLOADS = "downloads"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private var prefs: SharedPreferences? = null
    private val _downloads = MutableStateFlow<List<DownloadStatusEntry>>(emptyList())
    val downloads: StateFlow<List<DownloadStatusEntry>> = _downloads.asStateFlow()

    @Synchronized
    fun initialize(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _downloads.value = readEntries()
    }

    fun upsertQueued(context: Context, metadata: DownloadMetadata) {
        initialize(context)
        upsertEntry(
            DownloadStatusEntry(
                id = metadata.id,
                title = metadata.title,
                coverUrl = metadata.coverUrl,
                sourceUrl = metadata.sourceUrl,
                requestUri = metadata.requestUri ?: metadata.sourceUrl,
                mimeType = metadata.mimeType,
                state = Download.STATE_QUEUED,
                progressPercent = 0f,
                bytesDownloaded = 0L,
                contentLength = 0L,
                speedBytesPerSecond = 0L,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    fun onDownloadChanged(context: Context, download: Download) {
        initialize(context)
        val metadata = DownloadMetadata.fromDownload(download)
        val existing = _downloads.value.firstOrNull { it.id == download.request.id }
        val now = System.currentTimeMillis()
        val progress = download.percentDownloaded.takeIf { it >= 0f } ?: existing?.progressPercent ?: 0f
        val speedBytesPerSecond = calculateSpeed(download = download, previous = existing, now = now)
        val sourceUri = download.request.uri.toString().ifBlank { metadata?.requestUri ?: existing?.requestUri }
        val entry = DownloadStatusEntry(
            id = download.request.id,
            title = metadata?.title ?: existing?.title ?: download.request.id,
            coverUrl = metadata?.coverUrl ?: existing?.coverUrl,
            sourceUrl = metadata?.sourceUrl ?: existing?.sourceUrl,
            requestUri = sourceUri,
            mimeType = download.request.mimeType ?: metadata?.mimeType ?: existing?.mimeType,
            state = download.state,
            progressPercent = progress.coerceIn(0f, 100f),
            bytesDownloaded = download.bytesDownloaded,
            contentLength = download.contentLength,
            speedBytesPerSecond = speedBytesPerSecond,
            failureReason = download.failureReason,
            stopReason = download.stopReason,
            exportState = if (download.state != Download.STATE_COMPLETED && existing?.exportState != ExportState.EXPORT_FAILED && existing?.exportState != ExportState.EXPORT_UNSUPPORTED) ExportState.NOT_EXPORTED else existing?.exportState ?: ExportState.NOT_EXPORTED,
            exportedUri = existing?.exportedUri,
            exportRelativePath = existing?.exportRelativePath,
            exportFileName = existing?.exportFileName,
            exportMimeType = existing?.exportMimeType,
            exportError = existing?.exportError,
            exportNote = existing?.exportNote,
            exportedAt = existing?.exportedAt ?: 0L,
            updatedAt = now
        )
        upsertEntry(entry.sanitizeForCapability())
        PublicVideoExporter.scheduleIfNeeded(context, download, entry)
    }

    fun onDownloadRemoved(context: Context, downloadId: String) {
        initialize(context)
        val next = _downloads.value.filterNot { it.id == downloadId }
        persist(next)
    }

    fun markExportQueued(context: Context, downloadId: String) {
        initialize(context)
        updateEntry(downloadId) {
            val note = when (MediaSourceClassifier.classify(it.requestUri ?: it.sourceUrl, it.mimeType)) {
                MediaSourceKind.HLS -> "下载完成，准备通过 FFmpeg 导出单个视频文件"
                else -> "下载完成，准备导出视频文件"
            }
            it.copy(exportState = ExportState.EXPORT_QUEUED, exportError = null, exportNote = note)
        }
    }

    fun markExporting(context: Context, downloadId: String) {
        initialize(context)
        updateEntry(downloadId) {
            val note = when (MediaSourceClassifier.classify(it.requestUri ?: it.sourceUrl, it.mimeType)) {
                MediaSourceKind.HLS -> "正在通过 FFmpeg 导出单个视频文件到系统目录"
                else -> "正在导出单个视频文件到系统目录"
            }
            it.copy(exportState = ExportState.EXPORTING, exportError = null, exportNote = note)
        }
    }

    fun markExported(
        context: Context,
        downloadId: String,
        exportedUri: String,
        relativePath: String,
        fileName: String,
        mimeType: String,
        note: String
    ) {
        initialize(context)
        updateEntry(downloadId) {
            it.copy(
                exportState = ExportState.EXPORTED,
                exportedUri = exportedUri,
                exportRelativePath = relativePath,
                exportFileName = fileName,
                exportMimeType = mimeType,
                exportError = null,
                exportNote = note,
                exportedAt = System.currentTimeMillis()
            )
        }
    }

    fun markExportFailed(context: Context, downloadId: String, error: String) {
        initialize(context)
        updateEntry(downloadId) {
            it.copy(
                exportState = ExportState.EXPORT_FAILED,
                exportError = error,
                exportNote = "导出视频文件失败"
            )
        }
    }

    fun markExportUnsupported(context: Context, downloadId: String, reason: String) {
        initialize(context)
        updateEntry(downloadId) {
            it.copy(
                exportState = ExportState.EXPORT_UNSUPPORTED,
                exportedUri = null,
                exportRelativePath = null,
                exportFileName = null,
                exportMimeType = null,
                exportError = reason,
                exportNote = "当前此类资源暂不支持导出为单个视频文件",
                exportedAt = 0L
            )
        }
    }

    fun clearExport(context: Context, downloadId: String) {
        initialize(context)
        updateEntry(downloadId) {
            it.copy(
                exportState = ExportState.NOT_EXPORTED,
                exportedUri = null,
                exportRelativePath = null,
                exportFileName = null,
                exportMimeType = null,
                exportError = null,
                exportNote = null,
                exportedAt = 0L
            )
        }
    }

    fun find(downloadId: String): DownloadStatusEntry? = _downloads.value.firstOrNull { it.id == downloadId }

    private fun updateEntry(downloadId: String, transform: (DownloadStatusEntry) -> DownloadStatusEntry) {
        val existing = _downloads.value.firstOrNull { it.id == downloadId } ?: return
        upsertEntry(transform(existing).copy(updatedAt = System.currentTimeMillis()).sanitizeForCapability())
    }

    private fun calculateSpeed(download: Download, previous: DownloadStatusEntry?, now: Long): Long {
        if (download.state != Download.STATE_DOWNLOADING || previous == null) return 0L
        val deltaBytes = download.bytesDownloaded - previous.bytesDownloaded
        val deltaTimeMs = now - previous.updatedAt
        if (deltaBytes <= 0L || deltaTimeMs <= 0L) return previous.speedBytesPerSecond.takeIf { it > 0L } ?: 0L
        return ((deltaBytes.toDouble() * 1000.0) / deltaTimeMs.toDouble()).roundToLong().coerceAtLeast(0L)
    }

    private fun upsertEntry(entry: DownloadStatusEntry) {
        val next = _downloads.value
            .filterNot { it.id == entry.id }
            .toMutableList()
            .apply { add(0, entry) }
            .sortedByDescending { it.updatedAt }
        persist(next)
    }

    private fun persist(entries: List<DownloadStatusEntry>) {
        _downloads.value = entries
        prefs?.edit()?.putString(KEY_DOWNLOADS, json.encodeToString(entries))?.apply()
    }

    private fun readEntries(): List<DownloadStatusEntry> = runCatching {
        prefs?.getString(KEY_DOWNLOADS, null)
            ?.takeIf { it.isNotBlank() }
            ?.let { json.decodeFromString<List<DownloadStatusEntry>>(it) }
            ?.map { it.sanitizeForCapability() }
            ?: emptyList()
    }.getOrDefault(emptyList())
}

@Serializable
data class DownloadMetadata(
    val id: String,
    val title: String,
    val coverUrl: String? = null,
    val sourceUrl: String? = null,
    val requestUri: String? = null,
    val mimeType: String? = null
) {
    fun toByteArray(): ByteArray = Json.encodeToString(this).toByteArray(StandardCharsets.UTF_8)

    companion object {
        fun fromBytes(bytes: ByteArray?): DownloadMetadata? {
            if (bytes == null || bytes.isEmpty()) return null
            return runCatching {
                Json.decodeFromString<DownloadMetadata>(bytes.toString(StandardCharsets.UTF_8))
            }.getOrNull()
        }

        fun fromDownload(download: Download): DownloadMetadata? = fromBytes(download.request.data)
    }
}

@Serializable
enum class ExportState {
    NOT_EXPORTED,
    EXPORT_QUEUED,
    EXPORTING,
    EXPORTED,
    EXPORT_FAILED,
    EXPORT_UNSUPPORTED
}

@Serializable
data class DownloadStatusEntry(
    val id: String,
    val title: String,
    val coverUrl: String? = null,
    val sourceUrl: String? = null,
    val requestUri: String? = null,
    val mimeType: String? = null,
    val state: Int,
    val progressPercent: Float = 0f,
    val bytesDownloaded: Long = 0L,
    val contentLength: Long = 0L,
    val speedBytesPerSecond: Long = 0L,
    val failureReason: Int = 0,
    val stopReason: Int = 0,
    val exportState: ExportState = ExportState.NOT_EXPORTED,
    val exportedUri: String? = null,
    val exportRelativePath: String? = null,
    val exportFileName: String? = null,
    val exportMimeType: String? = null,
    val exportError: String? = null,
    val exportNote: String? = null,
    val exportedAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    val stateLabel: String
        get() = when (state) {
            Download.STATE_QUEUED -> "排队中"
            Download.STATE_STOPPED -> if (stopReason == DownloadCommands.STOP_REASON_NONE) "已停止" else "已暂停"
            Download.STATE_DOWNLOADING -> "下载中"
            Download.STATE_COMPLETED -> "已完成"
            Download.STATE_FAILED -> "失败"
            Download.STATE_REMOVING -> "移除中"
            Download.STATE_RESTARTING -> "重新开始中"
            else -> "未知"
        }

    val normalizedProgress: Float
        get() = when {
            state == Download.STATE_COMPLETED -> 1f
            progressPercent.isNaN() -> 0f
            else -> (progressPercent / 100f).coerceIn(0f, 1f)
        }

    val canPause: Boolean
        get() = state == Download.STATE_QUEUED || state == Download.STATE_DOWNLOADING || state == Download.STATE_RESTARTING

    val canResume: Boolean
        get() = state == Download.STATE_STOPPED

    val canRetry: Boolean
        get() = state == Download.STATE_FAILED || state == Download.STATE_COMPLETED

    val canRemove: Boolean
        get() = state != Download.STATE_REMOVING

    val exportLabel: String
        get() = when (exportState) {
            ExportState.NOT_EXPORTED -> if (state == Download.STATE_COMPLETED) "等待导出视频" else "未导出"
            ExportState.EXPORT_QUEUED -> "等待导出视频"
            ExportState.EXPORTING -> "导出视频中"
            ExportState.EXPORTED -> "已导出视频文件"
            ExportState.EXPORT_FAILED -> "导出视频失败"
            ExportState.EXPORT_UNSUPPORTED -> "不支持导出为视频文件"
        }

    val exportLocationText: String?
        get() = when {
            exportRelativePath.isNullOrBlank() && exportFileName.isNullOrBlank() -> null
            exportRelativePath.isNullOrBlank() -> exportFileName
            exportFileName.isNullOrBlank() -> exportRelativePath
            else -> exportRelativePath + exportFileName
        }

    val canOpenExport: Boolean
        get() = exportState == ExportState.EXPORTED && !exportedUri.isNullOrBlank()

    fun sanitizeForCapability(): DownloadStatusEntry {
        if (state != Download.STATE_COMPLETED) return this
        return when (MediaSourceClassifier.classify(requestUri ?: sourceUrl, mimeType)) {
            MediaSourceKind.HLS -> {
                if (exportState == ExportState.EXPORT_UNSUPPORTED) {
                    copy(
                        exportState = ExportState.NOT_EXPORTED,
                        exportedUri = null,
                        exportRelativePath = null,
                        exportFileName = null,
                        exportMimeType = null,
                        exportError = null,
                        exportNote = null,
                        exportedAt = 0L
                    )
                } else {
                    this
                }
            }
            MediaSourceKind.UNKNOWN -> copy(
                exportState = ExportState.EXPORT_UNSUPPORTED,
                exportedUri = null,
                exportRelativePath = null,
                exportFileName = null,
                exportMimeType = null,
                exportError = "当前下载资源无法确认是单个直链视频文件，已停止自动导出，避免再把素材包或 jpeg 资源误标记为已导出视频。",
                exportNote = "当前资源类型未识别为单文件视频，暂不支持导出到系统目录",
                exportedAt = 0L
            )
            MediaSourceKind.DIRECT_VIDEO -> this
        }
    }
}
