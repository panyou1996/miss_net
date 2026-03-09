package com.panyou.missnet.nativeapp.core.model

data class AppSettings(
    val useDynamicColor: Boolean = true,
    val autoplayRelated: Boolean = false,
    val incognitoMode: Boolean = false,
    val preferWifiDownloads: Boolean = true,
    val keepScreenAwakeInPlayer: Boolean = true,
)
