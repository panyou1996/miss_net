package com.panyou.missnet.data.local

import android.content.Context
import android.content.SharedPreferences
import com.panyou.missnet.data.model.Video
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class LocalVideoStateStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun getFavorites(): List<Video> = readList(KEY_FAVORITES)

    fun isFavorite(videoId: String): Boolean = getFavorites().any { it.id == videoId }

    fun toggleFavorite(video: Video): Boolean {
        val current = getFavorites().toMutableList()
        val index = current.indexOfFirst { it.id == video.id }
        val isFavorite = if (index >= 0) {
            current.removeAt(index)
            false
        } else {
            current.add(0, video)
            trimToSize(current, MAX_FAVORITES)
            true
        }
        writeList(KEY_FAVORITES, current)
        return isFavorite
    }

    fun getWatchHistory(): List<Video> =
        readProgressEntries()
            .sortedByDescending { it.updatedAt }
            .map { it.video }

    fun getProgress(videoId: String): WatchProgressEntry? =
        readProgressEntries().firstOrNull { it.video.id == videoId }

    fun upsertWatchProgress(video: Video, positionMs: Long, durationMs: Long) {
        val safeDuration = durationMs.coerceAtLeast(0L)
        val safePosition = positionMs.coerceIn(0L, if (safeDuration > 0L) safeDuration else Long.MAX_VALUE)
        val entries = readProgressEntries().toMutableList()
        val newEntry = WatchProgressEntry(
            video = video,
            positionMs = safePosition,
            durationMs = safeDuration,
            progress = calculateProgress(safePosition, safeDuration),
            updatedAt = System.currentTimeMillis()
        )

        val index = entries.indexOfFirst { it.video.id == video.id }
        if (index >= 0) {
            entries[index] = newEntry
        } else {
            entries.add(0, newEntry)
        }
        val normalized = entries
            .sortedByDescending { it.updatedAt }
            .distinctBy { it.video.id }
            .toMutableList()
        trimToSize(normalized, MAX_HISTORY)
        writeProgressEntries(normalized)
    }

    fun getHistoryEntries(): List<WatchProgressEntry> =
        readProgressEntries().sortedByDescending { it.updatedAt }

    fun getSearchHistory(): List<String> = readStringList(KEY_SEARCH_HISTORY)

    fun addSearchHistory(query: String) {
        val normalized = query.trim()
        if (normalized.isBlank()) return
        val list = readStringList(KEY_SEARCH_HISTORY)
            .filterNot { it.equals(normalized, ignoreCase = true) }
            .toMutableList()
        list.add(0, normalized)
        trimToSize(list, MAX_SEARCH_HISTORY)
        writeStringList(KEY_SEARCH_HISTORY, list)
    }

    fun clearSearchHistory() {
        prefs.edit().remove(KEY_SEARCH_HISTORY).apply()
    }

    fun removeSearchHistory(query: String) {
        val next = readStringList(KEY_SEARCH_HISTORY).filterNot { it == query }
        writeStringList(KEY_SEARCH_HISTORY, next)
    }

    private fun calculateProgress(positionMs: Long, durationMs: Long): Float {
        if (durationMs <= 0L) return 0f
        return (positionMs.toFloat() / max(durationMs, 1L).toFloat()).coerceIn(0f, 1f)
    }

    private fun readProgressEntries(): List<WatchProgressEntry> =
        runCatching {
            prefs.getString(KEY_HISTORY, null)
                ?.takeIf { it.isNotBlank() }
                ?.let { json.decodeFromString<List<WatchProgressEntry>>(it) }
                ?: emptyList()
        }.getOrDefault(emptyList())

    private fun writeProgressEntries(entries: List<WatchProgressEntry>) {
        prefs.edit().putString(KEY_HISTORY, json.encodeToString(entries)).apply()
    }

    private fun readList(key: String): List<Video> =
        runCatching {
            prefs.getString(key, null)
                ?.takeIf { it.isNotBlank() }
                ?.let { json.decodeFromString<List<Video>>(it) }
                ?: emptyList()
        }.getOrDefault(emptyList())

    private fun writeList(key: String, videos: List<Video>) {
        prefs.edit().putString(key, json.encodeToString(videos)).apply()
    }

    private fun readStringList(key: String): List<String> =
        runCatching {
            prefs.getString(key, null)
                ?.takeIf { it.isNotBlank() }
                ?.let { json.decodeFromString<List<String>>(it) }
                ?: emptyList()
        }.getOrDefault(emptyList())

    private fun writeStringList(key: String, values: List<String>) {
        prefs.edit().putString(key, json.encodeToString(values)).apply()
    }

    private fun <T> trimToSize(list: MutableList<T>, maxSize: Int) {
        while (list.size > maxSize) {
            list.removeLast()
        }
    }

    companion object {
        private const val PREFS_NAME = "missnet_local_state"
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_HISTORY = "history"
        private const val KEY_SEARCH_HISTORY = "search_history"
        private const val MAX_FAVORITES = 200
        private const val MAX_HISTORY = 200
        private const val MAX_SEARCH_HISTORY = 20
    }
}

@Serializable
data class WatchProgressEntry(
    val video: Video,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val progress: Float = 0f,
    val updatedAt: Long = 0L
)
