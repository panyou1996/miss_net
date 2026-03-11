package com.panyou.missnet

import com.panyou.missnet.data.media.DownloadTracker
import com.panyou.missnet.data.media.MediaDownloadManager
import dagger.hilt.android.HiltAndroidApp
import android.app.Application

@HiltAndroidApp
class MissNetApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DownloadTracker.initialize(this)
        MediaDownloadManager.getDownloadManager(this)
    }
}
