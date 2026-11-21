package com.example.domain.model

data class RideSample(
    val lat: Double,
    val lon: Double,
    val altitudeMeters: Double? = null,
    val speedMps: Float? = null,
    val timestampMillis: Long
)

data class RecordedRide(
    val id: String,
    val title: String,
    val trailName: String?,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val durationSeconds: Long,
    val distanceMiles: Double,
    val avgSpeedMph: Double,
    val maxSpeedMph: Double,
    val elevationGainFeet: Double,
    val routePoints: List<GeoPoint>,
    val samples: List<RideSample> = emptyList(),
    val syncedToHealthConnect: Boolean = false,
    val healthConnectSessionId: String? = null
)

enum class RideRecordingStatus {
    IDLE,
    RECORDING,
    PAUSED
}

data class ActiveRideMetrics(
    val status: RideRecordingStatus = RideRecordingStatus.IDLE,
    val targetTrailName: String? = null,
    val startTimeMillis: Long = 0L,
    val elapsedSeconds: Long = 0L,
    val distanceMiles: Double = 0.0,
    val currentSpeedMph: Double = 0.0,
    val maxSpeedMph: Double = 0.0,
    val elevationGainFeet: Double = 0.0,
    val points: List<GeoPoint> = emptyList(),
    val samples: List<RideSample> = emptyList()
)
