package com.panyou.missnet.nativeapp.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        FavoriteVideoEntity::class,
        HistoryVideoEntity::class,
        SearchQueryEntity::class,
        DownloadEntryEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(DatabaseConverters::class)
abstract class MissNetDatabase : RoomDatabase() {
    abstract fun missNetDao(): MissNetDao
}
