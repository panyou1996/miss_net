@file:androidx.media3.common.util.UnstableApi

package com.panyou.missnet.ui.screens.player

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

enum class DoubleTapSeekAction {
    Backward,
    Forward,
}

fun resolveDoubleTapSeekAction(tapX: Float, width: Float): DoubleTapSeekAction? {
    if (width <= 0f) return null
    return when {
        tapX < width / 3f -> DoubleTapSeekAction.Backward
        tapX > width * 2f / 3f -> DoubleTapSeekAction.Forward
        else -> null
    }
}

fun shareVideo(context: Context, title: String, url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val shareText = buildString {
        if (title.isNotBlank()) appendLine(title)
        append(url)
    }
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title.ifBlank { "MissNet" })
        putExtra(Intent.EXTRA_TEXT, shareText)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return try {
        context.startActivity(Intent.createChooser(shareIntent, "分享视频"))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

fun castOrOpenExternalPlayer(context: Context, title: String, url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(Uri.parse(url), if (url.contains(".m3u8")) "application/x-mpegURL" else "video/*")
        putExtra(Intent.EXTRA_TITLE, title)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return try {
        context.startActivity(Intent.createChooser(intent, "投屏 / 使用外部播放器播放"))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

fun formatTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSecs = ms / 1000
    val hours = totalSecs / 3600
    val mins = (totalSecs % 3600) / 60
    val secs = totalSecs % 60
    return if (hours > 0) "%02d:%02d:%02d".format(hours, mins, secs) else "%02d:%02d".format(mins, secs)
}
