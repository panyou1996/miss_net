package com.panyou.missnet.nativeapp.core

import android.app.Application
import androidx.room.Room
import com.panyou.missnet.nativeapp.core.data.SettingsRepository
import com.panyou.missnet.nativeapp.core.data.VideoRepository
import com.panyou.missnet.nativeapp.core.data.local.MissNetDatabase
import com.panyou.missnet.nativeapp.core.data.remote.SupabaseVideoRemoteSource
import com.panyou.missnet.nativeapp.core.media.MediaDownloadCoordinator
import com.panyou.missnet.nativeapp.core.media.MediaHeaderStore
import com.panyou.missnet.nativeapp.core.media.VideoStreamResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

class AppGraph(application: Application) {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val database: MissNetDatabase = Room.databaseBuilder(
        application,
        MissNetDatabase::class.java,
        "missnet-native.db",
    ).fallbackToDestructiveMigration().build()

    private val apiClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()

    val settingsRepository = SettingsRepository(application)
    val mediaHeaderStore = MediaHeaderStore(application)
    private val mediaClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val originalRequest = chain.request()
            val headers = mediaHeaderStore.snapshot()
            val request = originalRequest.newBuilder().apply {
                headers.forEach { (name, value) ->
                    if (originalRequest.header(name) == null) {
                        header(name, value)
                    }
                }
            }.build()
            chain.proceed(request)
        }
        .build()

    private val remoteSource = SupabaseVideoRemoteSource(apiClient)
    val videoRepository = VideoRepository(remoteSource, database.missNetDao(), settingsRepository)
    val downloadCoordinator = MediaDownloadCoordinator(
        context = application,
        dao = database.missNetDao(),
        mediaClient = mediaClient,
        settingsRepository = settingsRepository,
        applicationScope = applicationScope,
    )
    val streamResolver = VideoStreamResolver(application, mediaHeaderStore, mediaClient)
}
