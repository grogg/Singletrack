package com.example.ui.viewmodel

import com.example.data.health.HealthConnectAvailability
import com.example.data.health.HealthConnectStatus
import com.example.domain.model.ActiveRideMetrics
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
    val isDownloadingRegion: Boolean = false,
    val downloadingRegionName: String? = null,
    val isCurrentAreaSavedOffline: Boolean = false,
    val placeSearchQuery: String = "",
    val placeSearchResults: List<LocationPlace> = emptyList(),
    val isSearchingPlaces: Boolean = false,
    val placeSearchError: String? = null,
    val activeRide: ActiveRideMetrics = ActiveRideMetrics(),
    val recordedRides: List<RecordedRide> = emptyList(),
    val healthConnectStatus: HealthConnectStatus = HealthConnectStatus(
        availability = HealthConnectAvailability.UNAVAILABLE,
        permissionsGranted = false
    ),
    val autoSyncToHealthConnect: Boolean = true,
    val syncingRideId: String? = null,
    val snackbarMessage: String? = null
)
