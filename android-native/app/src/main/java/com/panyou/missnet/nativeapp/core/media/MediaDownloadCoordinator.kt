package com.panyou.missnet.nativeapp.core.media

import android.app.Notification
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.PlaceholderDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.offline.DownloadService.sendAddDownload
import androidx.media3.exoplayer.offline.DownloadService.sendRemoveAllDownloads
import androidx.media3.exoplayer.offline.DownloadService.sendRemoveDownload
import androidx.room.withTransaction
import com.panyou.missnet.nativeapp.R
import com.panyou.missnet.nativeapp.core.data.SettingsRepository
import com.panyou.missnet.nativeapp.core.data.local.DownloadEntryEntity
import com.panyou.missnet.nativeapp.core.data.local.MissNetDao
import com.panyou.missnet.nativeapp.core.model.DownloadRecord
import com.panyou.missnet.nativeapp.core.model.DownloadStatus
import com.panyou.missnet.nativeapp.core.model.Video
import com.panyou.missnet.nativeapp.core.util.appJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.Executors

@OptIn(UnstableApi::class)
class MediaDownloadCoordinator(
    private val context: Context,
    private val dao: MissNetDao,
    private val mediaClient: OkHttpClient,
    private val settingsRepository: SettingsRepository,
    private val applicationScope: CoroutineScope,
) {
    val databaseProvider = StandaloneDatabaseProvider(context)
    private val notificationHelper = DownloadNotificationHelper(context, DOWNLOAD_CHANNEL_ID)
    private val downloadCache = SimpleCache(
        File(context.cacheDir, "media_downloads"),
        NoOpCacheEvictor(),
        databaseProvider,
    )
    private val upstreamFactory = OkHttpDataSource.Factory(mediaClient)
    private val downloadExecutor = Executors.newFixedThreadPool(2)

    val downloadManager: DownloadManager = DownloadManager(
        context,
        databaseProvider,
        downloadCache,
        upstreamFactory,
        downloadExecutor,
    ).apply {
        maxParallelDownloads = 2
        minRetryCount = 3
        addListener(
            object : DownloadManager.Listener {
                override fun onInitialized(downloadManager: DownloadManager) {
                    syncCurrentDownloads()
                }

                override fun onDownloadChanged(
                    downloadManager: DownloadManager,
                    download: Download,
                    finalException: Exception?,
                ) {
                    applicationScope.launch(Dispatchers.IO) {
                        syncDownload(download, finalException?.message)
                    }
                }

                override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
                    applicationScope.launch(Dispatchers.IO) {
                        dao.removeDownload(download.request.id)
                    }
                }
            },
        )
    }

    fun observeDownloads(): Flow<List<DownloadRecord>> = dao.observeDownloads().map { list ->
        list.map { entity ->
            DownloadRecord(
                video = entity.asVideo(),
                streamUrl = entity.streamUrl,
                mimeType = entity.mimeType,
                headers = runCatching { appJson.decodeFromString<Map<String, String>>(entity.headersJson) }.getOrDefault(emptyMap()),
                status = entity.status,
                progressPercent = entity.progressPercent,
                bytesDownloaded = entity.bytesDownloaded,
                contentLength = entity.contentLength,
                addedAtEpochMs = entity.addedAtEpochMs,
                updatedAtEpochMs = entity.updatedAtEpochMs,
                completedAtEpochMs = entity.completedAtEpochMs,
                failureReason = entity.failureReason,
            )
        }
    }

    suspend fun getDownload(videoId: String): DownloadRecord? {
        return dao.getDownload(videoId)?.let { entity ->
            DownloadRecord(
                video = entity.asVideo(),
                streamUrl = entity.streamUrl,
                mimeType = entity.mimeType,
                headers = runCatching { appJson.decodeFromString<Map<String, String>>(entity.headersJson) }.getOrDefault(emptyMap()),
                status = entity.status,
                progressPercent = entity.progressPercent,
                bytesDownloaded = entity.bytesDownloaded,
                contentLength = entity.contentLength,
                addedAtEpochMs = entity.addedAtEpochMs,
                updatedAtEpochMs = entity.updatedAtEpochMs,
                completedAtEpochMs = entity.completedAtEpochMs,
                failureReason = entity.failureReason,
            )
        }
    }

    suspend fun enqueue(video: Video, streamUrl: String, mimeType: String?, headers: Map<String, String>) {
        val wifiOnly = settingsRepository.settings.first().preferWifiDownloads
        if (wifiOnly && isMeteredConnection()) {
            error("Wi-Fi only downloads are enabled.")
        }
        val request = DownloadRequest.Builder(video.id, Uri.parse(streamUrl))
            .setMimeType(mimeType)
            .setData(
                appJson.encodeToString<DownloadPayload>(
                    DownloadPayload(
                        video = video,
                        headers = headers,
                        streamUrl = streamUrl,
                        mimeType = mimeType,
                    ),
                ).encodeToByteArray(),
            )
            .build()

        dao.upsertDownload(
            DownloadEntryEntity(
                videoId = video.id,
                externalId = video.externalId,
                title = video.title,
                coverUrl = video.coverUrl,
                sourceUrl = video.sourceUrl,
                createdAtEpochMs = video.createdAtEpochMs,
                duration = video.duration,
                releaseDate = video.releaseDate,
                actors = video.actors,
                categories = video.categories,
                streamUrl = streamUrl,
                mimeType = mimeType,
                headersJson = appJson.encodeToString<Map<String, String>>(headers),
                status = DownloadStatus.Queued,
                progressPercent = 0f,
                bytesDownloaded = 0L,
                contentLength = 0L,
                failureReason = null,
                addedAtEpochMs = System.currentTimeMillis(),
                updatedAtEpochMs = System.currentTimeMillis(),
                completedAtEpochMs = null,
            ),
        )

        withContext(Dispatchers.Main) {
            sendAddDownload(context, MissNetDownloadService::class.java, request, false)
        }
    }

    suspend fun remove(videoId: String) {
        withContext(Dispatchers.Main) {
            sendRemoveDownload(context, MissNetDownloadService::class.java, videoId, false)
        }
    }

    suspend fun removeAll() {
        withContext(Dispatchers.Main) {
            sendRemoveAllDownloads(context, MissNetDownloadService::class.java, false)
        }
        dao.clearDownloads()
    }

    fun buildOnlineMediaItem(streamUrl: String, mimeType: String?): MediaItem {
        return MediaItem.Builder()
            .setUri(streamUrl)
            .setMimeType(mimeType)
            .build()
    }

    fun buildOfflineMediaItem(record: DownloadRecord): MediaItem {
        return DownloadRequest.Builder(record.video.id, Uri.parse(record.streamUrl))
            .setMimeType(record.mimeType)
            .build()
            .toMediaItem()
    }

    fun buildOnlineDataSourceFactory(): DataSource.Factory = upstreamFactory

    fun buildOfflineDataSourceFactory(): DataSource.Factory {
        return CacheDataSource.Factory()
            .setCache(downloadCache)
            .setCacheWriteDataSinkFactory(null)
            .setUpstreamDataSourceFactory(PlaceholderDataSource.FACTORY)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    fun cacheSpaceBytes(): Long = downloadCache.cacheSpace

    fun foregroundNotification(downloads: List<Download>, notMetRequirements: Int): Notification {
        return notificationHelper.buildProgressNotification(
            context,
            R.drawable.ic_launcher_foreground,
            null,
            null,
            downloads,
            notMetRequirements,
        )
    }

    private fun syncCurrentDownloads() {
        applicationScope.launch(Dispatchers.IO) {
            downloadManager.currentDownloads.forEach { download ->
                syncDownload(download, null)
            }
        }
    }

    private suspend fun syncDownload(download: Download, failureReason: String?) {
        val payload = runCatching {
            download.request.data?.decodeToString()?.let { appJson.decodeFromString<DownloadPayload>(it) }
        }.getOrNull()
        val video = payload?.video ?: return
        val status = when (download.state) {
            Download.STATE_COMPLETED -> DownloadStatus.Completed
            Download.STATE_DOWNLOADING -> DownloadStatus.Downloading
            Download.STATE_FAILED -> DownloadStatus.Failed
            Download.STATE_QUEUED -> DownloadStatus.Queued
            Download.STATE_REMOVING -> DownloadStatus.Removing
            Download.STATE_STOPPED -> DownloadStatus.Stopped
            else -> DownloadStatus.Queued
        }
        dao.upsertDownload(
            DownloadEntryEntity(
                videoId = video.id,
                externalId = video.externalId,
                title = video.title,
                coverUrl = video.coverUrl,
                sourceUrl = video.sourceUrl,
                createdAtEpochMs = video.createdAtEpochMs,
                duration = video.duration,
                releaseDate = video.releaseDate,
                actors = video.actors,
                categories = video.categories,
                streamUrl = payload.streamUrl,
                mimeType = payload.mimeType,
                headersJson = appJson.encodeToString<Map<String, String>>(payload.headers),
                status = status,
                progressPercent = if (download.percentDownloaded.isNaN()) 0f else download.percentDownloaded,
                bytesDownloaded = download.bytesDownloaded,
                contentLength = download.contentLength,
                failureReason = failureReason,
                addedAtEpochMs = dao.getDownload(video.id)?.addedAtEpochMs ?: System.currentTimeMillis(),
                updatedAtEpochMs = System.currentTimeMillis(),
                completedAtEpochMs = if (status == DownloadStatus.Completed) System.currentTimeMillis() else null,
            ),
        )
    }

    private fun isMeteredConnection(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return true
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return true
        return !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    @kotlinx.serialization.Serializable
    data class DownloadPayload(
        val video: Video,
        val headers: Map<String, String>,
        val streamUrl: String,
        val mimeType: String?,
    )

    private fun DownloadEntryEntity.asVideo(): Video = Video(
        id = videoId,
        externalId = externalId,
        title = title,
        coverUrl = coverUrl,
        sourceUrl = sourceUrl,
        createdAtEpochMs = createdAtEpochMs,
        duration = duration,
        releaseDate = releaseDate,
        actors = actors,
        categories = categories,
        isOfflineReady = status == DownloadStatus.Completed,
    )

    companion object {
        const val DOWNLOAD_CHANNEL_ID = "missnet-downloads"
    }
}
