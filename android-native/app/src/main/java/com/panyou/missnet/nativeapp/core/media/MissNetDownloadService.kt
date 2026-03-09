package com.panyou.missnet.nativeapp.core.media

import android.app.Notification
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import com.panyou.missnet.nativeapp.R
import com.panyou.missnet.nativeapp.core.util.appGraph

@OptIn(UnstableApi::class)
class MissNetDownloadService : DownloadService(
    /* foregroundNotificationId = */ 1201,
    /* foregroundNotificationUpdateInterval = */ 1_000L,
    MediaDownloadCoordinator.DOWNLOAD_CHANNEL_ID,
    R.string.download_channel_name,
    R.string.download_channel_description,
) {
    override fun getDownloadManager(): DownloadManager = applicationContext.appGraph.downloadCoordinator.downloadManager

    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(downloads: MutableList<Download>, notMetRequirements: Int): Notification {
        return applicationContext.appGraph.downloadCoordinator.foregroundNotification(downloads, notMetRequirements)
    }
}
