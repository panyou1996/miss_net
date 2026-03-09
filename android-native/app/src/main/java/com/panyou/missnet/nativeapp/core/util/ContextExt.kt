package com.panyou.missnet.nativeapp.core.util

import android.content.Context
import com.panyou.missnet.nativeapp.MissNetApplication
import com.panyou.missnet.nativeapp.core.AppGraph

val Context.appGraph: AppGraph
    get() = (applicationContext as MissNetApplication).appGraph
