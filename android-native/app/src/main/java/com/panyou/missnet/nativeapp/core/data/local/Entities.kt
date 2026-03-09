package com.panyou.missnet.nativeapp.core.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.panyou.missnet.nativeapp.core.model.DownloadStatus
import com.panyou.missnet.nativeapp.core.model.Video

@Entity(tableName = "favorites")
data class FavoriteVideoEntity(
    @PrimaryKey val videoId: String,
    val externalId: String,
    val title: String,
    val coverUrl: String?,
    val sourceUrl: String,
    val createdAtEpochMs: Long,
    val duration: String?,
    val releaseDate: String?,
    val actors: List<String>,
    val categories: List<String>,
    val savedAtEpochMs: Long,
)

@Entity(tableName = "history")
data class HistoryVideoEntity(
    @PrimaryKey val videoId: String,
    val externalId: String,
    val title: String,
    val coverUrl: String?,
    val sourceUrl: String,
    val createdAtEpochMs: Long,
    val duration: String?,
    val releaseDate: String?,
    val actors: List<String>,
    val categories: List<String>,
    val lastPositionMs: Long,
    val totalDurationMs: Long,
    val watchedAtEpochMs: Long,
)

@Entity(tableName = "search_history")
data class SearchQueryEntity(
    @PrimaryKey val query: String,
    val usedAtEpochMs: Long,
)

@Entity(tableName = "download_entries")
data class DownloadEntryEntity(
    @PrimaryKey val videoId: String,
    val externalId: String,
    val title: String,
    val coverUrl: String?,
    val sourceUrl: String,
    val createdAtEpochMs: Long,
    val duration: String?,
    val releaseDate: String?,
    val actors: List<String>,
    val categories: List<String>,
    val streamUrl: String,
    val mimeType: String?,
    val headersJson: String,
    val status: DownloadStatus,
    val progressPercent: Float,
    val bytesDownloaded: Long,
    val contentLength: Long,
    val failureReason: String?,
    val addedAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val completedAtEpochMs: Long?,
)

fun FavoriteVideoEntity.asVideo(): Video = Video(
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
)

fun HistoryVideoEntity.asVideo(): Video = Video(
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
    lastPositionMs = lastPositionMs,
    totalDurationMs = totalDurationMs,
)
