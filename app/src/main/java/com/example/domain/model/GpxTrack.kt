package com.example.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class GpxTrackPoint(
    val lat: Double,
    val lon: Double,
    val elevationFeet: Double? = null,
    val cumulativeMiles: Double = 0.0
)

@Immutable
data class GpxWaypoint(
    val lat: Double,
    val lon: Double,
    val name: String,
    val description: String? = null
)

@Immutable
data class GpxTrack(
    val id: String,
    val name: String,
    val fileName: String,
    val points: List<GpxTrackPoint>,
    val waypoints: List<GpxWaypoint> = emptyList(),
    val totalDistanceMiles: Double,
    val elevationGainFeet: Double,
    val elevationLossFeet: Double,
    val isReversed: Boolean = false,
    val loadedAtMillis: Long = System.currentTimeMillis()
) {
    val geoPoints: List<GeoPoint>
        get() = points.map { GeoPoint(it.lat, it.lon) }
}

enum class GpxRouteStatus(val label: String) {
    IDLE("Ready"),
    APPROACHING_START("Head to Start"),
    ON_TRACK("On Track"),
    OFF_ROUTE("Off Route"),
    FINISHED("Route Complete")
}

@Immutable
data class GpxNavigationState(
    val track: GpxTrack? = null,
    val isNavigating: Boolean = false,
    val isFullScreenMap: Boolean = false,
    val followUserLocation: Boolean = true,
    val routeStatus: GpxRouteStatus = GpxRouteStatus.IDLE,
    val userLocation: GeoPoint? = null,
    val userHeadingDegrees: Float? = null,
    val distanceCompletedMiles: Double = 0.0,
    val distanceRemainingMiles: Double = 0.0,
    val offRouteFeet: Double? = null,
    val bearingToTargetDegrees: Double? = null,
    val bearingCardinal: String? = null,
    val targetPoint: GeoPoint? = null,
    val nearestSegmentIndex: Int = 0,
    val nearestPointOnTrack: GeoPoint? = null,
    val progressFraction: Float = 0f,
    val turnInstruction: String = "Select a GPX track to navigate",
    val distanceToCueMiles: Double? = null
)
