package com.panyou.missnet.nativeapp.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MissNetDao {
    @Query("SELECT * FROM favorites ORDER BY savedAtEpochMs DESC")
    fun observeFavorites(): Flow<List<FavoriteVideoEntity>>

    @Query("SELECT * FROM favorites ORDER BY savedAtEpochMs DESC")
    suspend fun getFavorites(): List<FavoriteVideoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFavorite(entity: FavoriteVideoEntity)

    @Query("DELETE FROM favorites WHERE videoId = :videoId")
    suspend fun removeFavorite(videoId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE videoId = :videoId)")
    suspend fun isFavorite(videoId: String): Boolean

    @Query("SELECT COUNT(*) FROM favorites")
    suspend fun favoriteCount(): Int

    @Query("SELECT * FROM history ORDER BY watchedAtEpochMs DESC")
    fun observeHistory(): Flow<List<HistoryVideoEntity>>

    @Query("SELECT * FROM history ORDER BY watchedAtEpochMs DESC LIMIT :limit")
    suspend fun getHistory(limit: Int = 20): List<HistoryVideoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(entity: HistoryVideoEntity)

    @Query("SELECT lastPositionMs FROM history WHERE videoId = :videoId")
    suspend fun getProgress(videoId: String): Long?

    @Query("DELETE FROM history")
    suspend fun clearHistory()

    @Query("DELETE FROM history WHERE videoId NOT IN (SELECT videoId FROM history ORDER BY watchedAtEpochMs DESC LIMIT :keep)")
    suspend fun trimHistory(keep: Int)

    @Query("SELECT COUNT(*) FROM history")
    suspend fun historyCount(): Int

    @Query("SELECT * FROM search_history ORDER BY usedAtEpochMs DESC")
    fun observeSearchHistory(): Flow<List<SearchQueryEntity>>

    @Query("SELECT * FROM search_history ORDER BY usedAtEpochMs DESC")
    suspend fun getSearchHistory(): List<SearchQueryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSearchQuery(entity: SearchQueryEntity)

    @Query("DELETE FROM search_history")
    suspend fun clearSearchHistory()

    @Query("DELETE FROM search_history WHERE query NOT IN (SELECT query FROM search_history ORDER BY usedAtEpochMs DESC LIMIT :keep)")
    suspend fun trimSearchHistory(keep: Int)

    @Query("SELECT * FROM download_entries ORDER BY addedAtEpochMs DESC")
    fun observeDownloads(): Flow<List<DownloadEntryEntity>>

    @Query("SELECT * FROM download_entries ORDER BY addedAtEpochMs DESC")
    suspend fun getDownloads(): List<DownloadEntryEntity>

    @Query("SELECT * FROM download_entries WHERE videoId = :videoId LIMIT 1")
    suspend fun getDownload(videoId: String): DownloadEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDownload(entity: DownloadEntryEntity)

    @Query("DELETE FROM download_entries WHERE videoId = :videoId")
    suspend fun removeDownload(videoId: String)

    @Query("DELETE FROM download_entries")
    suspend fun clearDownloads()

    @Query("SELECT COUNT(*) FROM download_entries")
    suspend fun downloadCount(): Int
}
