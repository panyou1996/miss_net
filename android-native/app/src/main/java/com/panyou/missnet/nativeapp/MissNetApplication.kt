package com.panyou.missnet.nativeapp

import android.app.Application
import com.panyou.missnet.nativeapp.core.AppGraph

class MissNetApplication : Application() {
    lateinit var appGraph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        appGraph = AppGraph(this)
    }
}
