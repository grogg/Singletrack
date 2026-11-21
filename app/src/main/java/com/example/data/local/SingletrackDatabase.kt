package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [OfflineRegionEntity::class, CachedTrailEntity::class, RideEntity::class],
    version = 6,
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

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recorded_rides ADD COLUMN syncedToStrava INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE recorded_rides ADD COLUMN stravaActivityId INTEGER")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS recorded_rides_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        trailName TEXT,
                        startTimeMillis INTEGER NOT NULL,
                        endTimeMillis INTEGER NOT NULL,
                        durationSeconds INTEGER NOT NULL,
                        distanceMiles REAL NOT NULL,
                        avgSpeedMph REAL NOT NULL,
                        maxSpeedMph REAL NOT NULL,
                        elevationGainFeet REAL NOT NULL,
                        routeGeometryEncoded TEXT NOT NULL,
                        samplesEncoded TEXT NOT NULL,
                        syncedToHealthConnect INTEGER NOT NULL,
                        healthConnectSessionId TEXT,
                        avgCadenceRpm INTEGER,
                        maxCadenceRpm INTEGER,
                        sensorName TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO recorded_rides_new (
                        id, title, trailName, startTimeMillis, endTimeMillis, durationSeconds,
                        distanceMiles, avgSpeedMph, maxSpeedMph, elevationGainFeet,
                        routeGeometryEncoded, samplesEncoded, syncedToHealthConnect,
                        healthConnectSessionId, avgCadenceRpm, maxCadenceRpm, sensorName
                    )
                    SELECT
                        id, title, trailName, startTimeMillis, endTimeMillis, durationSeconds,
                        distanceMiles, avgSpeedMph, maxSpeedMph, elevationGainFeet,
                        routeGeometryEncoded, samplesEncoded, syncedToHealthConnect,
                        healthConnectSessionId, avgCadenceRpm, maxCadenceRpm, sensorName
                    FROM recorded_rides
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE recorded_rides")
                db.execSQL("ALTER TABLE recorded_rides_new RENAME TO recorded_rides")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recorded_rides ADD COLUMN avgHeartRateBpm INTEGER")
                db.execSQL("ALTER TABLE recorded_rides ADD COLUMN maxHeartRateBpm INTEGER")
                db.execSQL("ALTER TABLE recorded_rides ADD COLUMN hrSensorName TEXT")
            }
        }

        fun getInstance(context: Context): SingletrackDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SingletrackDatabase::class.java,
                    "singletrack_offline.db"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6
                    )
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
