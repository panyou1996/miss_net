package com.panyou.missnet.data.local

import android.content.Context
import android.content.SharedPreferences
import com.panyou.missnet.data.model.Video
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
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
    private val database = MissNetLocalDatabase.getInstance(context)
    private val favoriteDao = database.favoriteVideoDao()
    private val watchProgressDao = database.watchProgressDao()
    private val searchHistoryDao = database.searchHistoryDao()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val incognitoMode = MutableStateFlow(prefs.getBoolean(KEY_INCOGNITO_MODE, false))

    init {
        runBlocking(Dispatchers.IO) {
            migrateLegacyStateIfNeeded()
        }
    }

    fun observeFavorites(): Flow<List<Video>> = favoriteDao.observeAll().map { items ->
        items.map { it.toVideo() }
    }

    fun observeHistoryEntries(): Flow<List<WatchProgressEntry>> =
        combine(watchProgressDao.observeAll(), incognitoMode) { items, isIncognito ->
            if (isIncognito) emptyList() else items.map { it.toWatchProgressEntry() }
        }

    fun observeSearchHistory(): Flow<List<String>> =
        combine(searchHistoryDao.observeAll(), incognitoMode) { items, isIncognito ->
            if (isIncognito) emptyList() else items.map { it.query }
        }

    fun getFavorites(): List<Video> = runBlocking(Dispatchers.IO) {
        favoriteDao.getAll().map { it.toVideo() }
    }

    fun isFavorite(videoId: String): Boolean = runBlocking(Dispatchers.IO) {
        favoriteDao.countById(videoId) > 0
    }

    fun isIncognitoModeEnabled(): Boolean = prefs.getBoolean(KEY_INCOGNITO_MODE, false)

    fun setIncognitoMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_INCOGNITO_MODE, enabled).apply()
        incognitoMode.value = enabled
    }

    fun toggleFavorite(video: Video): Boolean {
        return runBlocking(Dispatchers.IO) {
            val isFavorite = favoriteDao.countById(video.id) > 0
            if (isFavorite) {
                favoriteDao.deleteById(video.id)
                false
            } else {
                favoriteDao.upsert(video.toFavoriteEntity())
                trimFavoritesToLimit()
                true
            }
        }
    }

    fun getWatchHistory(): List<Video> {
        if (isIncognitoModeEnabled()) return emptyList()
        return runBlocking(Dispatchers.IO) {
            watchProgressDao.getAll().map { it.toVideo() }
        }
    }

    fun getProgress(videoId: String): WatchProgressEntry? {
        if (isIncognitoModeEnabled()) return null
        return runBlocking(Dispatchers.IO) {
            watchProgressDao.getById(videoId)?.toWatchProgressEntry()
        }
    }

    fun upsertWatchProgress(video: Video, positionMs: Long, durationMs: Long) {
        if (isIncognitoModeEnabled()) return
        val safeDuration = durationMs.coerceAtLeast(0L)
        val safePosition = positionMs.coerceIn(0L, if (safeDuration > 0L) safeDuration else Long.MAX_VALUE)
        runBlocking(Dispatchers.IO) {
            watchProgressDao.upsert(
                WatchProgressEntry(
                    video = video,
                    positionMs = safePosition,
                    durationMs = safeDuration,
                    progress = calculateProgress(safePosition, safeDuration),
                    updatedAt = System.currentTimeMillis()
                ).toEntity()
            )
            trimHistoryToLimit()
        }
    }

    fun getHistoryEntries(): List<WatchProgressEntry> {
        if (isIncognitoModeEnabled()) return emptyList()
        return runBlocking(Dispatchers.IO) {
            watchProgressDao.getAll().map { it.toWatchProgressEntry() }
        }
    }

    fun getSearchHistory(): List<String> {
        if (isIncognitoModeEnabled()) return emptyList()
        return runBlocking(Dispatchers.IO) {
            searchHistoryDao.getAll().map { it.query }
        }
    }

    fun addSearchHistory(query: String) {
        if (isIncognitoModeEnabled()) return
        val normalized = query.trim()
        if (normalized.isBlank()) return
        runBlocking(Dispatchers.IO) {
            searchHistoryDao.getAll()
                .firstOrNull { it.query.equals(normalized, ignoreCase = true) }
                ?.let { searchHistoryDao.deleteByQuery(it.query) }
            searchHistoryDao.upsert(SearchHistoryEntity(query = normalized, updatedAt = System.currentTimeMillis()))
            trimSearchHistoryToLimit()
        }
    }

    fun clearSearchHistory() {
        runBlocking(Dispatchers.IO) {
            searchHistoryDao.clearAll()
        }
    }

    fun removeSearchHistory(query: String) {
        runBlocking(Dispatchers.IO) {
            searchHistoryDao.deleteByQuery(query)
        }
    }

    private fun calculateProgress(positionMs: Long, durationMs: Long): Float {
        if (durationMs <= 0L) return 0f
        return (positionMs.toFloat() / max(durationMs, 1L).toFloat()).coerceIn(0f, 1f)
    }

    private fun readList(key: String): List<Video> =
        runCatching {
            prefs.getString(key, null)
                ?.takeIf { it.isNotBlank() }
                ?.let { json.decodeFromString<List<Video>>(it) }
                ?: emptyList()
        }.getOrDefault(emptyList())

    private fun readStringList(key: String): List<String> =
        runCatching {
            prefs.getString(key, null)
                ?.takeIf { it.isNotBlank() }
                ?.let { json.decodeFromString<List<String>>(it) }
                ?: emptyList()
        }.getOrDefault(emptyList())

    private fun <T> trimToSize(list: MutableList<T>, maxSize: Int) {
        while (list.size > maxSize) {
            list.removeAt(list.lastIndex)
        }
    }

    private suspend fun migrateLegacyStateIfNeeded() {
        if (prefs.getBoolean(KEY_ROOM_MIGRATED, false)) return

        if (favoriteDao.countAll() == 0) {
            readList(KEY_FAVORITES)
                .take(MAX_FAVORITES)
                .forEachIndexed { index, video ->
                    favoriteDao.upsert(video.toFavoriteEntity(addedAt = System.currentTimeMillis() - index))
                }
        }

        if (watchProgressDao.countAll() == 0) {
            readLegacyProgressEntries()
                .sortedByDescending { it.updatedAt }
                .take(MAX_HISTORY)
                .forEach { watchProgressDao.upsert(it.toEntity()) }
        }

        if (searchHistoryDao.countAll() == 0) {
            readStringList(KEY_SEARCH_HISTORY)
                .take(MAX_SEARCH_HISTORY)
                .forEachIndexed { index, query ->
                    searchHistoryDao.upsert(
                        SearchHistoryEntity(
                            query = query,
                            updatedAt = System.currentTimeMillis() - index
                        )
                    )
                }
        }

        prefs.edit()
            .remove(KEY_FAVORITES)
            .remove(KEY_HISTORY)
            .remove(KEY_SEARCH_HISTORY)
            .putBoolean(KEY_ROOM_MIGRATED, true)
            .commit()
    }

    private fun readLegacyProgressEntries(): List<WatchProgressEntry> =
        runCatching {
            prefs.getString(KEY_HISTORY, null)
                ?.takeIf { it.isNotBlank() }
                ?.let { json.decodeFromString<List<WatchProgressEntry>>(it) }
                ?: emptyList()
        }.getOrDefault(emptyList())

    private suspend fun trimFavoritesToLimit() {
        favoriteDao.trimToLimit(MAX_FAVORITES)
    }

    private suspend fun trimHistoryToLimit() {
        watchProgressDao.trimToLimit(MAX_HISTORY)
    }

    private suspend fun trimSearchHistoryToLimit() {
        searchHistoryDao.trimToLimit(MAX_SEARCH_HISTORY)
    }

    companion object {
        private const val PREFS_NAME = "missnet_local_state"
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_HISTORY = "history"
        private const val KEY_SEARCH_HISTORY = "search_history"
        private const val KEY_INCOGNITO_MODE = "incognito_mode"
        private const val KEY_ROOM_MIGRATED = "room_migrated_v1"
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

private fun Video.toFavoriteEntity(addedAt: Long = System.currentTimeMillis()): FavoriteVideoEntity =
    FavoriteVideoEntity(
        id = id,
        externalId = externalId,
        title = title,
        coverUrl = coverUrl,
        sourceUrl = sourceUrl,
        duration = duration,
        sourceReleaseDate = sourceReleaseDate,
        createdAt = createdAt,
        actorsJson = Json.encodeToString(actors),
        tagsJson = Json.encodeToString(tags),
        inventoryStatus = inventoryStatus,
        detailStatus = detailStatus,
        addedAt = addedAt
    )

private fun FavoriteVideoEntity.toVideo(): Video =
    Video(
        id = id,
        externalId = externalId,
        title = title,
        coverUrl = coverUrl,
        sourceUrl = sourceUrl,
        duration = duration,
        sourceReleaseDate = sourceReleaseDate,
        createdAt = createdAt,
        actors = Json.decodeFromString(actorsJson),
        tags = Json.decodeFromString(tagsJson),
        inventoryStatus = inventoryStatus,
        detailStatus = detailStatus
    )

private fun WatchProgressEntry.toEntity(): WatchProgressEntity =
    WatchProgressEntity(
        videoId = video.id,
        externalId = video.externalId,
        title = video.title,
        coverUrl = video.coverUrl,
        sourceUrl = video.sourceUrl,
        duration = video.duration,
        sourceReleaseDate = video.sourceReleaseDate,
        createdAt = video.createdAt,
        actorsJson = Json.encodeToString(video.actors),
        tagsJson = Json.encodeToString(video.tags),
        inventoryStatus = video.inventoryStatus,
        detailStatus = video.detailStatus,
        positionMs = positionMs,
        durationMs = durationMs,
        progress = progress,
        updatedAt = updatedAt
    )

private fun WatchProgressEntity.toWatchProgressEntry(): WatchProgressEntry =
    WatchProgressEntry(
        video = toVideo(),
        positionMs = positionMs,
        durationMs = durationMs,
        progress = progress,
        updatedAt = updatedAt
    )

private fun WatchProgressEntity.toVideo(): Video =
    Video(
        id = videoId,
        externalId = externalId,
        title = title,
        coverUrl = coverUrl,
        sourceUrl = sourceUrl,
        duration = duration,
        sourceReleaseDate = sourceReleaseDate,
        createdAt = createdAt,
        actors = Json.decodeFromString(actorsJson),
        tags = Json.decodeFromString(tagsJson),
        inventoryStatus = inventoryStatus,
        detailStatus = detailStatus
    )
