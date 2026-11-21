package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "offline_regions")
data class OfflineRegionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val centerLat: Double,
    val centerLon: Double,
    val radiusMiles: Double,
    val trailCount: Int,
    val downloadedAtMillis: Long,
    val approxSizeBytes: Long,
    val hasFullGeometry: Boolean
)

@Entity(
    tableName = "cached_trails",
    primaryKeys = ["regionId", "osmKey"],
    foreignKeys = [
        ForeignKey(
            entity = OfflineRegionEntity::class,
            parentColumns = ["id"],
            childColumns = ["regionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["regionId"])]
)
data class CachedTrailEntity(
    val regionId: String,
    val osmKey: String,
    val osmType: String,
    val osmId: Long,
    val name: String,
    val difficultyName: String,
    val rawDifficultyTag: String?,
    val surface: String,
    val centerLat: Double,
    val centerLon: Double,
    val lengthMiles: Double?,
    val operatorName: String?,
    val networkName: String?,
    val description: String?,
    val oneway: String?,
    val uphillScale: String?,
    val isDesignatedMtb: Boolean,
    val geometryEncoded: String,
    val elevationGainFeet: Int? = null,
    val elevationProfileEncoded: String = ""
)

@Entity(tableName = "recorded_rides")
data class RideEntity(
    @PrimaryKey val id: String,
    val title: String,
    val trailName: String?,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val durationSeconds: Long,
    val distanceMiles: Double,
    val avgSpeedMph: Double,
    val maxSpeedMph: Double,
    val elevationGainFeet: Double,
    val routeGeometryEncoded: String,
    val samplesEncoded: String,
    val syncedToHealthConnect: Boolean,
    val healthConnectSessionId: String?
)
