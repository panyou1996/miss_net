package com.panyou.missnet.nativeapp.core.util

import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Long.toRelativeDateString(): String {
    return SimpleDateFormat("MMM d", Locale.US).format(Date(this))
}

fun Float.toPercentLabel(): String = "${toInt()}%"

fun Long.toStorageLabel(): String {
    if (this <= 0L) return "0 B"
    val units = listOf("B", "KB", "MB", "GB")
    var value = toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index += 1
    }
    return "${DecimalFormat("#.#").format(value)} ${units[index]}"
}

fun Long.toTimeLabel(): String {
    val totalSeconds = this / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
