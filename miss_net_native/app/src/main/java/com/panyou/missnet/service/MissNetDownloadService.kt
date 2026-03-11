@file:androidx.media3.common.util.UnstableApi

package com.panyou.missnet.service

import android.app.Notification
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import com.panyou.missnet.data.media.DownloadCommands
import com.panyou.missnet.data.media.DownloadMetadata
import com.panyou.missnet.data.media.MediaDownloadManager
import java.util.Locale

class MissNetDownloadService : DownloadService(
    NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    androidx.media3.exoplayer.R.string.exo_download_notification_channel_name,
    0
) {
    override fun getDownloadManager(): DownloadManager {
        return MediaDownloadManager.getDownloadManager(this)
    }

    override fun getScheduler(): androidx.media3.exoplayer.scheduler.Scheduler? {
        return null
    }

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int
    ): Notification {
        val active = downloads.firstOrNull { it.state == Download.STATE_DOWNLOADING || it.state == Download.STATE_QUEUED || it.state == Download.STATE_RESTARTING }
        val paused = downloads.firstOrNull { it.state == Download.STATE_STOPPED }
        val pausedCount = downloads.count { it.state == Download.STATE_STOPPED }
        val failedCount = downloads.count { it.state == Download.STATE_FAILED }
        val completedCount = downloads.count { it.state == Download.STATE_COMPLETED }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(androidx.media3.ui.R.drawable.exo_notification_small_icon)
            .setOngoing(active != null)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentTitle(buildTitle(active, downloads.size, pausedCount, failedCount, completedCount))
            .setContentText(buildMessage(active, downloads.size, pausedCount, failedCount, notMetRequirements))
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    buildBigText(active, downloads.size, pausedCount, failedCount, completedCount, notMetRequirements)
                )
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (active != null) {
            val progress = active.percentDownloaded.takeIf { it >= 0f }?.toInt()?.coerceIn(0, 100)
            if (progress != null) {
                builder.setProgress(100, progress, false)
            } else {
                builder.setProgress(100, 0, true)
            }
            builder.addAction(
                android.R.drawable.ic_media_pause,
                "暂停",
                PendingIntent.getService(
                    this,
                    100,
                    DownloadService.buildSetStopReasonIntent(
                        this,
                        MissNetDownloadService::class.java,
                        active.request.id,
                        DownloadCommands.STOP_REASON_PAUSED_BY_USER,
                        false
                    ),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "取消",
                PendingIntent.getService(
                    this,
                    101,
                    DownloadService.buildRemoveDownloadIntent(this, MissNetDownloadService::class.java, active.request.id, false),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
        } else {
            builder.setProgress(0, 0, false)
            if (paused != null) {
                builder.addAction(
                    android.R.drawable.ic_media_play,
                    "继续",
                    PendingIntent.getService(
                        this,
                        102,
                        DownloadService.buildSetStopReasonIntent(
                            this,
                            MissNetDownloadService::class.java,
                            paused.request.id,
                            DownloadCommands.STOP_REASON_NONE,
                            false
                        ),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                )
            }
        }

        return builder.build()
    }

    private fun buildTitle(
        active: Download?,
        totalCount: Int,
        pausedCount: Int,
        failedCount: Int,
        completedCount: Int
    ): String {
        return when {
            active != null -> DownloadMetadata.fromDownload(active)?.title ?: "MissNet 下载中"
            pausedCount > 0 && totalCount == pausedCount -> "下载已暂停"
            failedCount > 0 && totalCount == failedCount -> "下载失败"
            completedCount > 0 && totalCount == completedCount -> "下载已完成"
            else -> "MissNet 下载管理"
        }
    }

    private fun buildMessage(
        active: Download?,
        totalCount: Int,
        pausedCount: Int,
        failedCount: Int,
        notMetRequirements: Int
    ): String {
        return when {
            active != null -> {
                val percent = active.percentDownloaded.takeIf { it >= 0f }?.let { String.format(Locale.US, "%.0f%%", it) } ?: "--"
                val downloaded = formatBytes(active.bytesDownloaded)
                val total = if (active.contentLength > 0L) formatBytes(active.contentLength) else "--"
                "${stateLabel(active)} · $percent · $downloaded / $total${requirementSuffix(notMetRequirements)}"
            }
            pausedCount > 0 -> "有 $pausedCount 个任务已暂停${if (failedCount > 0) "，$failedCount 个失败" else ""}"
            failedCount > 0 -> "有 $failedCount 个任务失败，请回到 Library > Downloads 重试"
            totalCount > 0 -> "下载任务共 $totalCount 个"
            else -> "准备下载"
        }
    }

    private fun buildBigText(
        active: Download?,
        totalCount: Int,
        pausedCount: Int,
        failedCount: Int,
        completedCount: Int,
        notMetRequirements: Int
    ): String {
        val summary = mutableListOf<String>()
        if (active != null) {
            summary += buildMessage(active, totalCount, pausedCount, failedCount, notMetRequirements)
            summary += "已完成 $completedCount / $totalCount"
        } else {
            summary += "总任务：$totalCount"
            if (pausedCount > 0) summary += "暂停：$pausedCount"
            if (failedCount > 0) summary += "失败：$failedCount"
            if (completedCount > 0) summary += "完成：$completedCount"
            summary += requirementSuffix(notMetRequirements).removePrefix(" · ").ifBlank { "可在 Library > Downloads 管理任务" }
        }
        return summary.joinToString("\n")
    }

    private fun stateLabel(download: Download): String = when (download.state) {
        Download.STATE_QUEUED -> "等待开始"
        Download.STATE_STOPPED -> if (download.stopReason == DownloadCommands.STOP_REASON_PAUSED_BY_USER) "已暂停" else "已停止"
        Download.STATE_DOWNLOADING -> "进行中"
        Download.STATE_COMPLETED -> "最近完成"
        Download.STATE_FAILED -> "需要处理"
        Download.STATE_REMOVING -> "移除中"
        Download.STATE_RESTARTING -> "重新开始中"
        else -> "处理中"
    }

    private fun requirementSuffix(notMetRequirements: Int): String {
        if (notMetRequirements == 0) return ""
        return " · 等待网络条件满足"
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = 1024.0
        val mb = kb * 1024
        val gb = mb * 1024
        return when {
            bytes >= gb -> String.format(Locale.US, "%.2f GB", bytes / gb)
            bytes >= mb -> String.format(Locale.US, "%.1f MB", bytes / mb)
            bytes >= kb -> String.format(Locale.US, "%.1f KB", bytes / kb)
            else -> "$bytes B"
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "download_channel"
    }
}
