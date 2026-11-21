package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [OfflineRegionEntity::class, CachedTrailEntity::class, RideEntity::class],
    version = 1,
    exportSchema = false
)
abstract class SingletrackDatabase : RoomDatabase() {
    abstract fun offlineTrailDao(): OfflineTrailDao

    companion object {
        @Volatile
        private var INSTANCE: SingletrackDatabase? = null

        fun getInstance(context: Context): SingletrackDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SingletrackDatabase::class.java,
                    "singletrack_offline.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
