package com.example.domain.model

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

data class GeoPoint(
    val lat: Double,
    val lon: Double
)

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
    val isOfflineCached: Boolean = false
)

data class LocationPlace(
    val name: String,
    val lat: Double,
    val lon: Double,
    val isCurrentGps: Boolean = false
)

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
