package com.panyou.missnet.nativeapp.core.data

import com.panyou.missnet.nativeapp.core.data.local.FavoriteVideoEntity
import com.panyou.missnet.nativeapp.core.data.local.HistoryVideoEntity
import com.panyou.missnet.nativeapp.core.data.local.MissNetDao
import com.panyou.missnet.nativeapp.core.data.local.SearchQueryEntity
import com.panyou.missnet.nativeapp.core.data.local.asVideo
import com.panyou.missnet.nativeapp.core.data.remote.SupabaseVideoRemoteSource
import com.panyou.missnet.nativeapp.core.model.FeedCategory
import com.panyou.missnet.nativeapp.core.model.HomeSection
import com.panyou.missnet.nativeapp.core.model.Video
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class VideoRepository(
    private val remoteSource: SupabaseVideoRemoteSource,
    private val dao: MissNetDao,
    private val settingsRepository: SettingsRepository,
) {
    fun observeFavorites(): Flow<List<Video>> = dao.observeFavorites().map { list -> list.map { it.asVideo() } }
    fun observeHistory(): Flow<List<Video>> = dao.observeHistory().map { list -> list.map { it.asVideo() } }
    fun observeSearchHistory(): Flow<List<String>> = dao.observeSearchHistory().map { list -> list.map { it.query } }

    suspend fun loadHomeSections(): Pair<List<HomeSection>, List<Video>> = coroutineScope {
        val newRelease = async { remoteSource.getRecentVideos(limit = 15, category = "new") }
        val monthlyHot = async { remoteSource.getRecentVideos(limit = 10, category = "monthly_hot") }
        val weeklyHot = async { remoteSource.getRecentVideos(limit = 10, category = "weekly_hot") }
        val uncensored = async { remoteSource.getRecentVideos(limit = 10, category = "uncensored") }
        val eatMelon = async { remoteSource.getRecentVideos(limit = 10, category = "51cg") }
        val dailyCompetition = async { remoteSource.getRecentVideos(limit = 10, category = "51mrds") }
        val history = async { dao.getHistory().map { it.asVideo() } }

        val sections = buildList {
            addIfNotEmpty("New Releases", "new", newRelease.await())
            addIfNotEmpty("Monthly Hot", "monthly_hot", monthlyHot.await())
            addIfNotEmpty("Weekly Hot", "weekly_hot", weeklyHot.await())
            addIfNotEmpty("Uncensored", "uncensored", uncensored.await())
            addIfNotEmpty("51 Eating Melon", "51cg", eatMelon.await())
            addIfNotEmpty("Daily Competition", "51mrds", dailyCompetition.await())
        }

        sections to history.await()
    }

    suspend fun loadFeed(category: String? = null, actor: String? = null, limit: Int = 40): List<Video> {
        return remoteSource.getRecentVideos(limit = limit, category = category, actor = actor)
    }

    suspend fun searchVideos(query: String): List<Video> = remoteSource.searchVideos(query)

    suspend fun getSearchSuggestions(query: String): List<String> = remoteSource.getSearchSuggestions(query)

    suspend fun getRelatedVideos(video: Video): List<Video> = remoteSource.getRelatedVideos(video)

    suspend fun getPopularActors(): List<String> = remoteSource.getPopularActors()

    suspend fun getPopularTags(): List<String> = remoteSource.getPopularTags()

    suspend fun getFavorites(): List<Video> = dao.getFavorites().map { it.asVideo() }

    suspend fun isFavorite(videoId: String): Boolean = dao.isFavorite(videoId)

    suspend fun saveFavorite(video: Video) {
        dao.upsertFavorite(
            FavoriteVideoEntity(
                videoId = video.id,
                externalId = video.externalId,
                title = video.title,
                coverUrl = video.coverUrl,
                sourceUrl = video.sourceUrl,
                createdAtEpochMs = video.createdAtEpochMs,
                duration = video.duration,
                releaseDate = video.releaseDate,
                actors = video.actors,
                categories = video.categories,
                savedAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun removeFavorite(videoId: String) {
        dao.removeFavorite(videoId)
    }

    suspend fun saveWatchProgress(video: Video, positionMs: Long, totalDurationMs: Long) {
        val incognitoEnabled = settingsRepository.settings.first().incognitoMode
        if (incognitoEnabled) return
        dao.upsertHistory(
            HistoryVideoEntity(
                videoId = video.id,
                externalId = video.externalId,
                title = video.title,
                coverUrl = video.coverUrl,
                sourceUrl = video.sourceUrl,
                createdAtEpochMs = video.createdAtEpochMs,
                duration = video.duration,
                releaseDate = video.releaseDate,
                actors = video.actors,
                categories = video.categories,
                lastPositionMs = positionMs,
                totalDurationMs = totalDurationMs,
                watchedAtEpochMs = System.currentTimeMillis(),
            ),
        )
        dao.trimHistory(20)
    }

    suspend fun getProgress(videoId: String): Long = dao.getProgress(videoId) ?: 0L

    suspend fun clearHistory() {
        dao.clearHistory()
    }

    suspend fun saveSearchQuery(query: String) {
        if (query.isBlank()) return
        dao.upsertSearchQuery(SearchQueryEntity(query.trim(), System.currentTimeMillis()))
        dao.trimSearchHistory(10)
    }

    suspend fun clearSearchHistory() {
        dao.clearSearchHistory()
    }

    suspend fun favoriteCount(): Int = dao.favoriteCount()

    suspend fun historyCount(): Int = dao.historyCount()

    private fun MutableList<HomeSection>.addIfNotEmpty(title: String, category: String, videos: List<Video>) {
        if (videos.isNotEmpty()) {
            add(HomeSection(title = title, category = FeedCategory(category), videos = videos))
        }
    }
}
