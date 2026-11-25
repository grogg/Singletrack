package com.example.ui.viewmodel

import androidx.compose.runtime.Immutable
import com.example.data.health.HealthConnectAvailability
import com.example.data.health.HealthConnectStatus
import com.example.domain.model.ActiveRideMetrics
import com.example.domain.model.BleSensorUiState
import com.example.domain.model.GpxNavigationState
import com.example.domain.model.LocationPlace
import com.example.domain.model.MeasurementUnit
import com.example.domain.model.OfflineRegion
import com.example.domain.model.RecordedRide
import com.example.domain.model.Trail
import com.example.domain.model.TrailDifficulty
import com.example.domain.model.TrailSortOrder
import com.example.domain.model.TrailViewMode

enum class AppDestination(val label: String) {
    EXPLORE("Explore"),
    OFFLINE("Offline"),
    RIDE("Ride")
}

@Immutable
data class MainUiState(
    val currentDestination: AppDestination = AppDestination.EXPLORE,
    val measurementUnit: MeasurementUnit = MeasurementUnit.IMPERIAL,
    val hasLocationPermission: Boolean = false,
    val isLocating: Boolean = false,
    val locationUnavailableReason: String? = null,
    val activeLocation: LocationPlace? = null,
    val trails: List<Trail> = emptyList(),
    val filteredTrails: List<Trail> = emptyList(),
    val selectedDifficulty: TrailDifficulty? = null,
    val sortOrder: TrailSortOrder = TrailSortOrder.CLOSEST,
    val searchQuery: String = "",
    val viewMode: TrailViewMode = TrailViewMode.LIST,
    val isLoadingTrails: Boolean = false,
    val trailErrorMessage: String? = null,
    val isFromOfflineCache: Boolean = false,
    val activeOfflineRegionName: String? = null,
    val usedSingletrackFallback: Boolean = false,
    val selectedTrail: Trail? = null,
    val isLoadingTrailGeometry: Boolean = false,
    val isLoadingElevation: Boolean = false,
    val isFullScreenMap: Boolean = false,
    val offlineRegions: List<OfflineRegion> = emptyList(),
    val loadOfflineMapsByDefault: Boolean = false,
    val defaultOfflineRegionId: String? = null,
    val isDownloadingRegion: Boolean = false,
    val downloadingRegionName: String? = null,
    val isCurrentAreaSavedOffline: Boolean = false,
    val placeSearchQuery: String = "",
    val placeSearchResults: List<LocationPlace> = emptyList(),
    val isSearchingPlaces: Boolean = false,
    val placeSearchError: String? = null,
    val activeRide: ActiveRideMetrics = ActiveRideMetrics(),
    val isRecordingScreenOpen: Boolean = false,
    val openRecordingScreenOnStart: Boolean = false,
    val recordedRides: List<RecordedRide> = emptyList(),
    val selectedRide: RecordedRide? = null,
    val isFullScreenRideMap: Boolean = false,
    val gpxNavigation: GpxNavigationState = GpxNavigationState(),
    val voiceCuesEnabled: Boolean = true,
    val gpxFullVoiceNavigationEnabled: Boolean = false,
    val bleSensorState: BleSensorUiState = BleSensorUiState(),
    val healthConnectStatus: HealthConnectStatus = HealthConnectStatus(
        availability = HealthConnectAvailability.UNAVAILABLE,
        permissionsGranted = false
    ),
    val autoSyncToHealthConnect: Boolean = true,
    val syncingRideId: String? = null,
    val snackbarMessage: String? = null
)
