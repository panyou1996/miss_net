@file:androidx.media3.common.util.UnstableApi

package com.panyou.missnet.data.media

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors

object MediaDownloadManager {
    private val ioExecutor: Executor = Executors.newFixedThreadPool(4)
    private var downloadManager: DownloadManager? = null
    private var cache: Cache? = null
    private var databaseProvider: DatabaseProvider? = null
    private var listenerAttached = false

    @Synchronized
    fun getDownloadManager(context: Context): DownloadManager {
        val appContext = context.applicationContext
        DownloadTracker.initialize(appContext)
        if (downloadManager == null) {
            downloadManager = DownloadManager(
                appContext,
                getDatabaseProvider(appContext),
                getCache(appContext),
                getHttpDataSourceFactory(),
                ioExecutor
            ).apply {
                maxParallelDownloads = 2
            }
        }
        if (!listenerAttached) {
            downloadManager?.addListener(object : DownloadManager.Listener {
                override fun onDownloadChanged(
                    downloadManager: DownloadManager,
                    download: Download,
                    finalException: java.lang.Exception?
                ) {
                    DownloadTracker.onDownloadChanged(appContext, download)
                }

                override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
                    DownloadTracker.onDownloadRemoved(appContext, download.request.id)
                }
            })
            listenerAttached = true
        }
        return downloadManager!!
    }

    @Synchronized
    fun getCache(context: Context): Cache {
        val appContext = context.applicationContext
        if (cache == null) {
            val cacheDir = File(appContext.getExternalFilesDir(null), "downloads")
            cache = SimpleCache(cacheDir, NoOpCacheEvictor(), getDatabaseProvider(appContext))
        }
        return cache!!
    }

    @Synchronized
    private fun getDatabaseProvider(context: Context): DatabaseProvider {
        val appContext = context.applicationContext
        if (databaseProvider == null) {
            databaseProvider = StandaloneDatabaseProvider(appContext)
        }
        return databaseProvider!!
    }

    fun getHttpDataSourceFactory(): DataSource.Factory {
        return DefaultHttpDataSource.Factory()
            .setUserAgent("MissNet/1.0")
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(
                mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                    "Referer" to "https://missav.ws/"
                )
            )
    }

    fun getReadOnlyDataSourceFactory(context: Context): DataSource.Factory {
        return CacheDataSource.Factory()
            .setCache(getCache(context))
            .setUpstreamDataSourceFactory(getHttpDataSourceFactory())
            .setCacheWriteDataSinkFactory(null)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }
}
