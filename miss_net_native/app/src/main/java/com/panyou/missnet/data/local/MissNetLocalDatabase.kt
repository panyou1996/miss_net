package com.panyou.missnet.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "favorites")
data class FavoriteVideoEntity(
    @PrimaryKey val id: String,
    val externalId: String? = null,
    val title: String,
    val coverUrl: String? = null,
    val sourceUrl: String,
    val duration: String? = null,
    val sourceReleaseDate: String? = null,
    val createdAt: String? = null,
    val actorsJson: String = "[]",
    val tagsJson: String = "[]",
    val inventoryStatus: String? = null,
    val detailStatus: String? = null,
    val addedAt: Long = 0L
)

@Entity(tableName = "watch_progress")
data class WatchProgressEntity(
    @PrimaryKey val videoId: String,
    val externalId: String? = null,
    val title: String,
    val coverUrl: String? = null,
    val sourceUrl: String,
    val duration: String? = null,
    val sourceReleaseDate: String? = null,
    val createdAt: String? = null,
    val actorsJson: String = "[]",
    val tagsJson: String = "[]",
    val inventoryStatus: String? = null,
    val detailStatus: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val progress: Float = 0f,
    val updatedAt: Long = 0L
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val updatedAt: Long = 0L
)

@Dao
interface FavoriteVideoDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoriteVideoEntity>>

    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    suspend fun getAll(): List<FavoriteVideoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FavoriteVideoEntity)

    @Query("DELETE FROM favorites WHERE id = :videoId")
    suspend fun deleteById(videoId: String)

    @Query("SELECT COUNT(*) FROM favorites WHERE id = :videoId")
    suspend fun countById(videoId: String): Int

    @Query("SELECT COUNT(*) FROM favorites")
    suspend fun countAll(): Int

    @Query("DELETE FROM favorites WHERE id NOT IN (SELECT id FROM favorites ORDER BY addedAt DESC LIMIT :limit)")
    suspend fun trimToLimit(limit: Int)
}

@Dao
interface WatchProgressDao {
    @Query("SELECT * FROM watch_progress ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<WatchProgressEntity>>

    @Query("SELECT * FROM watch_progress ORDER BY updatedAt DESC")
    suspend fun getAll(): List<WatchProgressEntity>

    @Query("SELECT * FROM watch_progress WHERE videoId = :videoId LIMIT 1")
    suspend fun getById(videoId: String): WatchProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WatchProgressEntity)

    @Query("SELECT COUNT(*) FROM watch_progress")
    suspend fun countAll(): Int

    @Query("DELETE FROM watch_progress WHERE videoId NOT IN (SELECT videoId FROM watch_progress ORDER BY updatedAt DESC LIMIT :limit)")
    suspend fun trimToLimit(limit: Int)
}

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<SearchHistoryEntity>>

    @Query("SELECT * FROM search_history ORDER BY updatedAt DESC")
    suspend fun getAll(): List<SearchHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SearchHistoryEntity)

    @Query("DELETE FROM search_history")
    suspend fun clearAll()

    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun deleteByQuery(query: String)

    @Query("SELECT COUNT(*) FROM search_history")
    suspend fun countAll(): Int

    @Query("DELETE FROM search_history WHERE query NOT IN (SELECT query FROM search_history ORDER BY updatedAt DESC LIMIT :limit)")
    suspend fun trimToLimit(limit: Int)
}

@Database(
    entities = [FavoriteVideoEntity::class, WatchProgressEntity::class, SearchHistoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class MissNetLocalDatabase : RoomDatabase() {
    abstract fun favoriteVideoDao(): FavoriteVideoDao
    abstract fun watchProgressDao(): WatchProgressDao
    abstract fun searchHistoryDao(): SearchHistoryDao

    companion object {
        @Volatile
        private var instance: MissNetLocalDatabase? = null

        fun getInstance(context: Context): MissNetLocalDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MissNetLocalDatabase::class.java,
                    "missnet-local.db"
                ).build().also { instance = it }
            }
        }
    }
}
