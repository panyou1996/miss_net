package com.panyou.missnet.nativeapp.core.model

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlinx.parcelize.Parcelize

@Parcelize
@Immutable
@Serializable
data class Video(
    val id: String,
    val externalId: String,
    val title: String,
    val coverUrl: String?,
    val sourceUrl: String,
    val createdAtEpochMs: Long,
    val duration: String? = null,
    val releaseDate: String? = null,
    val actors: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val lastPositionMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val isOfflineReady: Boolean = false,
) : Parcelable {
    val progress: Float
        get() = if (totalDurationMs <= 0L) 0f else (lastPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
}
