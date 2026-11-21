package com.example.domain.model

data class RideSample(
    val lat: Double,
    val lon: Double,
    val altitudeMeters: Double? = null,
    val speedMps: Float? = null,
    val timestampMillis: Long,
    val cadenceRpm: Int? = null,
    val heartRateBpm: Int? = null
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
    val healthConnectSessionId: String? = null,
    val avgCadenceRpm: Int? = null,
    val maxCadenceRpm: Int? = null,
    val sensorName: String? = null,
    val avgHeartRateBpm: Int? = null,
    val maxHeartRateBpm: Int? = null,
    val hrSensorName: String? = null
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
    val samples: List<RideSample> = emptyList(),
    val currentCadenceRpm: Int? = null,
    val avgCadenceRpm: Int? = null,
    val maxCadenceRpm: Int? = null,
    val sensorSourceLabel: String? = null,
    val currentHeartRateBpm: Int? = null,
    val avgHeartRateBpm: Int? = null,
    val maxHeartRateBpm: Int? = null,
    val hrSensorSourceLabel: String? = null
)

enum class WheelCircumferencePreset(
    val shortLabel: String,
    val fullLabel: String,
    val circumferenceMm: Int
) {
    MTB_29("29\" MTB", "29\" × 2.25 MTB (2288 mm)", 2288),
    MTB_27_5("27.5\" MTB", "27.5\" × 2.25 MTB (2180 mm)", 2180),
    GRAVEL_700C("700c Gravel", "700 × 38c Gravel (2180 mm / 2130 mm)", 2130),
    MTB_26("26\" MTB", "26\" × 2.1 MTB (2070 mm)", 2070)
}

data class DiscoveredBleSensor(
    val name: String,
    val address: String,
    val rssi: Int
)

data class PairedBleSensor(
    val name: String,
    val address: String,
    val wheelCircumferenceMm: Int = WheelCircumferencePreset.MTB_29.circumferenceMm
)

data class PairedHrSensor(
    val name: String,
    val address: String
)

enum class BleConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class BleSensorUiState(
    val isBluetoothSupported: Boolean = true,
    val isBluetoothEnabled: Boolean = false,
    val hasBluetoothPermissions: Boolean = false,
    val pairedSensor: PairedBleSensor? = null,
    val connectionStatus: BleConnectionStatus = BleConnectionStatus.DISCONNECTED,
    val isScanning: Boolean = false,
    val discoveredSensors: List<DiscoveredBleSensor> = emptyList(),
    val liveSpeedMph: Double? = null,
    val liveCadenceRpm: Int? = null,
    val hasWheelCapability: Boolean = false,
    val hasCadenceCapability: Boolean = false,
    val statusMessage: String? = null,
    val pairedHrSensor: PairedHrSensor? = null,
    val hrConnectionStatus: BleConnectionStatus = BleConnectionStatus.DISCONNECTED,
    val isScanningHr: Boolean = false,
    val discoveredHrSensors: List<DiscoveredBleSensor> = emptyList(),
    val liveHeartRateBpm: Int? = null,
    val hrStatusMessage: String? = null
)
