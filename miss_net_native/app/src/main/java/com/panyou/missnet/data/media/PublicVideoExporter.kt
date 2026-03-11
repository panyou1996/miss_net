@file:androidx.media3.common.util.UnstableApi

package com.panyou.missnet.data.media

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap

object PublicVideoExporter {
    private const val ROOT_DIR = "MissNet"
    private const val MOVIES_PRIMARY_DIR = "Movies"
    private const val VIDEO_MIME_FALLBACK = "video/mp4"
    private const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    private const val DEFAULT_REFERER = "https://missav.ws/"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val exportJobs = ConcurrentHashMap<String, Job>()

    fun scheduleIfNeeded(context: Context, download: androidx.media3.exoplayer.offline.Download, existing: DownloadStatusEntry?) {
        if (download.state != androidx.media3.exoplayer.offline.Download.STATE_COMPLETED) return
        if (existing?.exportState == ExportState.EXPORTING || existing?.exportState == ExportState.EXPORTED || existing?.exportState == ExportState.EXPORT_QUEUED) return
        if (exportJobs.containsKey(download.request.id)) return

        val appContext = context.applicationContext
        DownloadTracker.markExportQueued(appContext, download.request.id)
        exportJobs[download.request.id] = scope.launch {
            try {
                val metadata = DownloadMetadata.fromDownload(download)
                val sourceUri = metadata?.requestUri ?: download.request.uri.toString()
                val sourceMimeType = download.request.mimeType ?: metadata?.mimeType
                val exported = exportByKind(
                    context = appContext,
                    entry = existing ?: DownloadTracker.find(download.request.id),
                    downloadId = download.request.id,
                    title = metadata?.title ?: download.request.id,
                    sourceUri = sourceUri,
                    mimeType = sourceMimeType
                )
                DownloadTracker.markExported(
                    context = appContext,
                    downloadId = download.request.id,
                    exportedUri = exported.uri.toString(),
                    relativePath = exported.relativePath,
                    fileName = exported.fileName,
                    mimeType = exported.mimeType,
                    note = exported.note
                )
            } catch (t: Throwable) {
                DownloadTracker.markExportFailed(appContext, download.request.id, formatExportError(t))
            } finally {
                exportJobs.remove(download.request.id)
            }
        }
    }

    fun export(context: Context, entry: DownloadStatusEntry) {
        if (entry.state != androidx.media3.exoplayer.offline.Download.STATE_COMPLETED) return
        if (entry.exportState == ExportState.EXPORTED || entry.exportState == ExportState.EXPORTING || entry.exportState == ExportState.EXPORT_QUEUED) return
        if (exportJobs.containsKey(entry.id)) return

        val appContext = context.applicationContext
        exportJobs[entry.id] = scope.launch {
            try {
                val sourceUri = entry.requestUri ?: entry.sourceUrl
                    ?: throw IOException("缺少导出源地址，请重新下载后再试")
                DownloadTracker.markExportQueued(appContext, entry.id)
                DownloadTracker.markExporting(appContext, entry.id)
                val exported = exportByKind(
                    context = appContext,
                    entry = entry,
                    downloadId = entry.id,
                    title = entry.title,
                    sourceUri = sourceUri,
                    mimeType = entry.mimeType
                )
                DownloadTracker.markExported(
                    context = appContext,
                    downloadId = entry.id,
                    exportedUri = exported.uri.toString(),
                    relativePath = exported.relativePath,
                    fileName = exported.fileName,
                    mimeType = exported.mimeType,
                    note = exported.note
                )
            } catch (t: Throwable) {
                DownloadTracker.markExportFailed(appContext, entry.id, formatExportError(t))
            } finally {
                exportJobs.remove(entry.id)
            }
        }
    }

    fun removeExported(context: Context, entry: DownloadStatusEntry) {
        val appContext = context.applicationContext
        exportJobs.remove(entry.id)?.cancel()

        val resolver = appContext.contentResolver
        val deleted = entry.exportedUri?.let { uriString ->
            runCatching { resolver.delete(Uri.parse(uriString), null, null) > 0 }.getOrDefault(false)
        } ?: false

        if (!deleted && !entry.exportRelativePath.isNullOrBlank() && !entry.exportFileName.isNullOrBlank()) {
            runCatching {
                findExistingUri(appContext, entry.exportRelativePath, entry.exportFileName)?.let { resolver.delete(it, null, null) }
            }
        }
        DownloadTracker.clearExport(appContext, entry.id)
    }

    private fun exportByKind(
        context: Context,
        entry: DownloadStatusEntry?,
        downloadId: String,
        title: String,
        sourceUri: String,
        mimeType: String?
    ): ExportedFile {
        return when (MediaSourceClassifier.classify(sourceUri, mimeType)) {
            MediaSourceKind.DIRECT_VIDEO -> {
                DownloadTracker.markExporting(context, downloadId)
                exportSingleVideo(
                    context = context,
                    downloadId = downloadId,
                    title = title,
                    sourceUri = sourceUri,
                    mimeType = mimeType
                )
            }
            MediaSourceKind.HLS -> {
                DownloadTracker.markExporting(context, downloadId)
                exportHlsVideo(
                    context = context,
                    downloadId = downloadId,
                    title = title,
                    playlistUri = sourceUri,
                    entry = entry
                )
            }
            MediaSourceKind.UNKNOWN -> throw IOException(
                "当前下载资源无法确认是单个视频文件，已停止导出，避免再把素材包或 jpeg 资源误标记为已导出视频。"
            )
        }
    }

    private fun exportSingleVideo(
        context: Context,
        downloadId: String,
        title: String,
        sourceUri: String,
        mimeType: String?
    ): ExportedFile {
        val safeBaseName = buildBaseName(title = title, fallback = downloadId)
        val resolvedMimeType = resolveVideoMimeType(sourceUri = sourceUri, mimeType = mimeType)
        val extension = guessExtension(sourceUri = sourceUri, mimeType = resolvedMimeType, defaultExtension = "mp4")
        val relativePath = buildRelativePath(primaryDirectory = MOVIES_PRIMARY_DIR, ROOT_DIR)
        val fileName = ensureUniqueDisplayName(
            context = context,
            relativePath = relativePath,
            displayName = "$safeBaseName.$extension"
        )
        val uri = createMediaStoreFile(
            context = context,
            relativePath = relativePath,
            displayName = fileName,
            mimeType = resolvedMimeType
        )
        try {
            copyUriToMediaStore(context, sourceUri, uri)
            markPublished(context, uri)
            return ExportedFile(
                uri = uri,
                relativePath = relativePath,
                fileName = fileName,
                mimeType = resolvedMimeType,
                note = "已导出单个视频文件到系统 Movies/$ROOT_DIR"
            )
        } catch (t: Throwable) {
            runCatching { context.contentResolver.delete(uri, null, null) }
            throw t
        }
    }

    private fun exportHlsVideo(
        context: Context,
        downloadId: String,
        title: String,
        playlistUri: String,
        entry: DownloadStatusEntry?
    ): ExportedFile {
        val safeBaseName = buildBaseName(title = title, fallback = downloadId)
        val relativePath = buildRelativePath(primaryDirectory = MOVIES_PRIMARY_DIR, ROOT_DIR)
        val fileName = ensureUniqueDisplayName(
            context = context,
            relativePath = relativePath,
            displayName = "$safeBaseName.mp4"
        )

        val tempDir = File(context.cacheDir, "ffmpeg-export").apply { mkdirs() }
        val tempFile = File(tempDir, "${downloadId}-${System.currentTimeMillis()}.mp4")

        try {
            runFfmpegHlsExport(
                playlistUri = playlistUri,
                outputFile = tempFile,
                title = title,
                entry = entry
            )
            if (!tempFile.exists() || tempFile.length() <= 0L) {
                throw IOException("FFmpeg 已执行，但没有生成可用的 mp4 文件")
            }

            val uri = createMediaStoreFile(
                context = context,
                relativePath = relativePath,
                displayName = fileName,
                mimeType = VIDEO_MIME_FALLBACK
            )
            try {
                context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                    tempFile.inputStream().use { input -> input.copyTo(output) }
                } ?: throw IOException("无法写入系统目录")
                markPublished(context, uri)
            } catch (t: Throwable) {
                runCatching { context.contentResolver.delete(uri, null, null) }
                throw t
            }

            return ExportedFile(
                uri = uri,
                relativePath = relativePath,
                fileName = fileName,
                mimeType = VIDEO_MIME_FALLBACK,
                note = "已通过 FFmpeg 导出 HLS 为单个 mp4 视频到系统 Movies/$ROOT_DIR"
            )
        } finally {
            runCatching { tempFile.delete() }
        }
    }

    private fun runFfmpegHlsExport(
        playlistUri: String,
        outputFile: File,
        title: String,
        entry: DownloadStatusEntry?
    ) {
        val command = buildString {
            append("-y ")
            append("-protocol_whitelist ")
            append(ffmpegQuote("file,http,https,tcp,tls,crypto,data"))
            append(' ')
            append("-allowed_extensions ALL ")
            append("-user_agent ")
            append(ffmpegQuote(DEFAULT_USER_AGENT))
            append(' ')
            append("-headers ")
            append(ffmpegQuote("Referer: $DEFAULT_REFERER\r\nOrigin: $DEFAULT_REFERER\r\n"))
            append(' ')
            append("-i ")
            append(ffmpegQuote(playlistUri))
            append(' ')
            append("-map 0:v:0? -map 0:a? -map 0:s? ")
            append("-c copy ")
            append("-bsf:a aac_adtstoasc ")
            append("-movflags +faststart ")
            append(ffmpegQuote(outputFile.absolutePath))
        }

        val session = FFmpegKit.execute(command)
        val returnCode = session.returnCode
        if (!ReturnCode.isSuccess(returnCode)) {
            val logs = session.getAllLogsAsString().orEmpty().takeLast(4000)
            val baseMessage = buildString {
                append("FFmpeg 导出 HLS 失败")
                if (!entry?.requestUri.isNullOrBlank()) {
                    append("，当前实现依赖源 m3u8 地址重新封装")
                }
                append("；可能原因：源地址失效、需要额外鉴权、流本身不支持无转码 remux，或网络不可用。")
                append(" 标题：")
                append(title)
            }
            val detail = logs
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toList()
                .takeLast(8)
                .joinToString(" | ")
            throw IOException(if (detail.isBlank()) baseMessage else "$baseMessage 详情：$detail")
        }
    }

    private fun copyUriToMediaStore(context: Context, sourceUri: String, targetUri: Uri) {
        val resolver = context.contentResolver
        resolver.openOutputStream(targetUri, "w")?.use { output ->
            copyFromMediaDataSource(context, sourceUri, output)
        } ?: throw IOException("无法写入系统目录")
    }

    private fun copyFromMediaDataSource(context: Context, sourceUri: String, output: OutputStream) {
        val factory = MediaDownloadManager.getReadOnlyDataSourceFactory(context)
        val dataSource = factory.createDataSource()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        try {
            dataSource.open(DataSpec(Uri.parse(sourceUri)))
            while (true) {
                val read = dataSource.read(buffer, 0, buffer.size)
                if (read == C.RESULT_END_OF_INPUT) break
                if (read > 0) output.write(buffer, 0, read)
            }
            output.flush()
        } finally {
            runCatching { dataSource.close() }
        }
    }

    private fun createMediaStoreFile(context: Context, relativePath: String, displayName: String, mimeType: String): Uri {
        val resolver = context.contentResolver
        val normalizedRelativePath = normalizeRelativePath(relativePath)
        val collection = collectionFor(relativePath = normalizedRelativePath, mimeType = mimeType)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, normalizedRelativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        return try {
            resolver.insert(collection, values)
                ?: throw IOException("创建系统目录文件失败：$displayName")
        } catch (e: IllegalArgumentException) {
            throw IOException(
                "创建系统目录文件失败：collection=$collection, path=$normalizedRelativePath, name=$displayName, mime=$mimeType, cause=${e.message}",
                e
            )
        }
    }

    private fun markPublished(context: Context, uri: Uri) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }
        context.contentResolver.update(uri, values, null, null)
    }

    private fun ensureUniqueDisplayName(context: Context, relativePath: String, displayName: String): String {
        val dotIndex = displayName.lastIndexOf('.')
        val base = if (dotIndex > 0) displayName.substring(0, dotIndex) else displayName
        val extension = if (dotIndex > 0) displayName.substring(dotIndex + 1) else ""
        var attempt = displayName
        var suffix = 1
        while (findExistingUri(context, relativePath, attempt) != null) {
            attempt = if (extension.isNotEmpty()) "$base-$suffix.$extension" else "$base-$suffix"
            suffix++
        }
        return attempt
    }

    private fun findExistingUri(context: Context, relativePath: String, displayName: String): Uri? {
        val resolver = context.contentResolver
        val normalizedPath = normalizeRelativePath(relativePath)
        val collection = collectionFor(relativePath = normalizedPath, mimeType = null)
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        resolver.query(
            collection,
            projection,
            "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?",
            arrayOf(normalizedPath, displayName),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return ContentUris.withAppendedId(collection, cursor.getLong(0))
            }
        }
        return null
    }

    private fun buildRelativePath(primaryDirectory: String, vararg parts: String): String {
        val path = buildString {
            append(primaryDirectory)
            for (part in parts) {
                val normalized = part.trim('/').trim()
                if (normalized.isNotEmpty()) {
                    append('/')
                    append(normalized)
                }
            }
        }
        return normalizeRelativePath(path)
    }

    private fun collectionFor(relativePath: String, mimeType: String?): Uri {
        val primaryDirectory = normalizeRelativePath(relativePath).substringBefore('/', "")
        return when {
            primaryDirectory.equals(MOVIES_PRIMARY_DIR, ignoreCase = true) -> MediaStore.Video.Media.getContentUri("external")
            mimeType?.startsWith("video/", ignoreCase = true) == true -> MediaStore.Video.Media.getContentUri("external")
            else -> MediaStore.Files.getContentUri("external")
        }
    }

    private fun formatExportError(error: Throwable): String {
        val message = error.message?.trim().orEmpty()
        return when {
            message.contains("Primary directory", ignoreCase = true) ->
                "导出失败：系统公共目录与 MediaStore 集合不匹配，已切换为按目录自动选择集合。详细信息：$message"
            message.isNotBlank() -> "导出失败：$message"
            else -> "导出失败：未知错误"
        }
    }

    private fun normalizeRelativePath(path: String): String =
        path.trim().trim('/').let { if (it.isBlank()) "" else "$it/" }

    private fun buildBaseName(title: String, fallback: String): String {
        val normalized = title
            .replace(Regex("[\\\\/:*?\"<>|]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(80)
        return normalized.ifBlank { fallback }
    }

    private fun resolveVideoMimeType(sourceUri: String, mimeType: String?): String {
        if (!mimeType.isNullOrBlank() && mimeType.startsWith("video/", ignoreCase = true)) return mimeType
        return MediaSourceClassifier.inferDownloadMimeType(sourceUri)?.takeIf { it.startsWith("video/", ignoreCase = true) }
            ?: guessMimeTypeFromUrl(sourceUri)
            ?: VIDEO_MIME_FALLBACK
    }

    private fun guessExtension(sourceUri: String, mimeType: String?, defaultExtension: String): String {
        val fromUri = MimeTypeMap.getFileExtensionFromUrl(sourceUri)
            ?.substringBefore('?')
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }
        if (!fromUri.isNullOrBlank()) return fromUri
        val fromMime = mimeType?.substringAfterLast('/')
            ?.substringAfter('.')
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }
        return fromMime ?: defaultExtension
    }

    private fun guessMimeTypeFromUrl(sourceUri: String): String? {
        val extension = MimeTypeMap.getFileExtensionFromUrl(sourceUri)
            ?.substringBefore('?')
            ?.lowercase()
            ?: return null
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    }

    private fun ffmpegQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private data class ExportedFile(
        val uri: Uri,
        val relativePath: String,
        val fileName: String,
        val mimeType: String,
        val note: String
    )
}
