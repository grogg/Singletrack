package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflineTrailDao {
    @Query("SELECT * FROM offline_regions ORDER BY downloadedAtMillis DESC")
    fun observeRegions(): Flow<List<OfflineRegionEntity>>

    @Query("SELECT * FROM offline_regions ORDER BY downloadedAtMillis DESC")
    suspend fun getAllRegionsOnce(): List<OfflineRegionEntity>

    @Query("SELECT * FROM cached_trails WHERE regionId = :regionId")
    suspend fun getTrailsForRegion(regionId: String): List<CachedTrailEntity>

    @Query("SELECT * FROM cached_trails WHERE osmKey = :osmKey LIMIT 1")
    suspend fun findCachedTrailByKey(osmKey: String): CachedTrailEntity?

    @Query("UPDATE cached_trails SET elevationGainFeet = :gainFeet, elevationProfileEncoded = :profileEncoded WHERE osmKey = :osmKey")
    suspend fun updateCachedTrailElevation(osmKey: String, gainFeet: Int?, profileEncoded: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRegion(region: OfflineRegionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrails(trails: List<CachedTrailEntity>)

    @Query("DELETE FROM cached_trails WHERE regionId = :regionId")
    suspend fun deleteTrailsForRegion(regionId: String)

    @Query("DELETE FROM offline_regions WHERE id = :regionId")
    suspend fun deleteRegionById(regionId: String)

    @Transaction
    suspend fun replaceRegionWithTrails(
        region: OfflineRegionEntity,
        trails: List<CachedTrailEntity>
    ) {
        deleteTrailsForRegion(region.id)
        insertRegion(region)
        if (trails.isNotEmpty()) {
            insertTrails(trails)
        }
    }

    // Recorded rides
    @Query("SELECT * FROM recorded_rides ORDER BY startTimeMillis DESC")
    fun observeRides(): Flow<List<RideEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRide(ride: RideEntity)

    @Query("UPDATE recorded_rides SET syncedToHealthConnect = 1, healthConnectSessionId = :sessionId WHERE id = :rideId")
    suspend fun markRideSynced(rideId: String, sessionId: String?)

    @Query("DELETE FROM recorded_rides WHERE id = :rideId")
    suspend fun deleteRideById(rideId: String)
}
