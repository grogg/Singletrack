package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [OfflineRegionEntity::class, CachedTrailEntity::class, RideEntity::class],
    version = 3,
    exportSchema = false
)
abstract class SingletrackDatabase : RoomDatabase() {
    abstract fun offlineTrailDao(): OfflineTrailDao

    companion object {
        @Volatile
        private var INSTANCE: SingletrackDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cached_trails ADD COLUMN elevationGainFeet INTEGER")
                db.execSQL("ALTER TABLE cached_trails ADD COLUMN elevationProfileEncoded TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recorded_rides ADD COLUMN avgCadenceRpm INTEGER")
                db.execSQL("ALTER TABLE recorded_rides ADD COLUMN maxCadenceRpm INTEGER")
                db.execSQL("ALTER TABLE recorded_rides ADD COLUMN sensorName TEXT")
            }
        }

        fun getInstance(context: Context): SingletrackDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SingletrackDatabase::class.java,
                    "singletrack_offline.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
