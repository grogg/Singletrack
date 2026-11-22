package com.example.domain.model

import androidx.compose.runtime.Immutable

enum class TrailDifficulty(
    val label: String,
    val shortLabel: String,
    val description: String,
    val rank: Int
) {
    EASY(
        label = "Easy",
        shortLabel = "Green",
        description = "Smooth singletrack or wide dirt trail with gentle grades and minimal obstacles.",
        rank = 1
    ),
    INTERMEDIATE(
        label = "Intermediate",
        shortLabel = "Blue",
        description = "Moderate climbs, roots, loose rock, and banked turns suited to confident riders.",
        rank = 2
    ),
    ADVANCED(
        label = "Advanced",
        shortLabel = "Black",
        description = "Steep pitches, technical rock gardens, drops, and narrow exposed sections.",
        rank = 3
    ),
    EXPERT(
        label = "Expert",
        shortLabel = "Dbl Black",
        description = "Extreme gradients, mandatory obstacles, and severe technical terrain.",
        rank = 4
    ),
    UNRATED(
        label = "Trail",
        shortLabel = "Unrated",
        description = "Mapped mountain biking or off-road trail without an IMBA/STS difficulty rating.",
        rank = 5
    )
}

enum class TrailSortOrder(val label: String) {
    CLOSEST("Closest"),
    DIFFICULTY("Difficulty"),
    LENGTH("Longest"),
    NAME("A–Z")
}

enum class TrailViewMode {
    LIST,
    MAP
}

enum class MeasurementUnit(val label: String, val summary: String) {
    IMPERIAL("Imperial", "Miles (mi), mph, feet (ft)"),
    METRIC("Metric", "Kilometers (km), km/h, meters (m)")
}

@Immutable
data class GeoPoint(
    val lat: Double,
    val lon: Double
)

@Immutable
data class ElevationPoint(
    val distanceMiles: Double,
    val elevationFeet: Double
)

enum class PhysicalEffortRating(val label: String) {
    GENTLE("Gentle"),
    MODERATE("Moderate"),
    STRENUOUS("Strenuous"),
    GRUELING("Grueling")
}

@Immutable
data class TrailElevationProfile(
    val elevationGainFeet: Int,
    val elevationLossFeet: Int,
    val minElevationFeet: Int,
    val maxElevationFeet: Int,
    val maxGradePercent: Int,
    val points: List<ElevationPoint>,
    val sourceLabel: String
) {
    val effortRating: PhysicalEffortRating
        get() {
            val totalMiles = points.lastOrNull()?.distanceMiles?.coerceAtLeast(0.25) ?: 1.0
            val feetPerMile = elevationGainFeet / totalMiles
            return when {
                elevationGainFeet >= 2500 || (feetPerMile >= 350.0 && elevationGainFeet >= 800) || maxGradePercent >= 20 ->
                    PhysicalEffortRating.GRUELING
                elevationGainFeet >= 1200 || (feetPerMile >= 200.0 && elevationGainFeet >= 400) || maxGradePercent >= 12 ->
                    PhysicalEffortRating.STRENUOUS
                elevationGainFeet >= 350 || feetPerMile >= 90.0 ->
                    PhysicalEffortRating.MODERATE
                else ->
                    PhysicalEffortRating.GENTLE
            }
        }
}

@Immutable
data class Trail(
    val osmKey: String,
    val osmType: String,
    val osmId: Long,
    val name: String,
    val difficulty: TrailDifficulty,
    val rawDifficultyTag: String?,
    val surface: String,
    val centerLat: Double,
    val centerLon: Double,
    val distanceFromUserMiles: Double,
    val bearingDegrees: Double,
    val bearingCardinal: String,
    val lengthMiles: Double?,
    val operatorName: String?,
    val networkName: String?,
    val description: String?,
    val oneway: String?,
    val uphillScale: String?,
    val isDesignatedMtb: Boolean,
    val geometry: List<GeoPoint> = emptyList(),
    val routeSegments: List<List<GeoPoint>> = emptyList(),
    val isOfflineCached: Boolean = false,
    val elevationGainFeet: Int? = null,
    val elevationProfile: TrailElevationProfile? = null
)

@Immutable
data class LocationPlace(
    val name: String,
    val lat: Double,
    val lon: Double,
    val isCurrentGps: Boolean = false
)

@Immutable
data class OfflineRegion(
    val id: String,
    val name: String,
    val centerLat: Double,
    val centerLon: Double,
    val radiusMiles: Double,
    val trailCount: Int,
    val downloadedAtMillis: Long,
    val approxSizeBytes: Long,
    val hasFullGeometry: Boolean
)
