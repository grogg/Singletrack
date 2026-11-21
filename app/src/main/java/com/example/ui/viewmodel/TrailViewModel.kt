package com.example.ui.viewmodel

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.health.HealthConnectSyncManager
import com.example.data.local.SingletrackDatabase
import com.example.data.location.DeviceLocationResult
import com.example.data.location.LocationProvider
import com.example.data.location.RideTracker
import com.example.data.repository.GeoUtils
import com.example.data.repository.RideRepository
import com.example.data.repository.TrailRepository
import com.example.domain.model.ActiveRideMetrics
import com.example.domain.model.GeoPoint
import com.example.domain.model.LocationPlace
import com.example.domain.model.OfflineRegion
import com.example.domain.model.RecordedRide
import com.example.domain.model.RideRecordingStatus
import com.example.domain.model.RideSample
import com.example.domain.model.Trail
import com.example.domain.model.TrailDifficulty
import com.example.domain.model.TrailSortOrder
import com.example.domain.model.TrailViewMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

class TrailViewModel(application: Application) : AndroidViewModel(application) {
    private val database = SingletrackDatabase.getInstance(application)
    private val trailRepository = TrailRepository(database.offlineTrailDao())
    private val rideRepository = RideRepository(database.offlineTrailDao())
    private val locationProvider = LocationProvider(application)
    private val rideTracker = RideTracker(application)
    val healthConnectSyncManager = HealthConnectSyncManager(application)

    private val _uiState = MutableStateFlow(
        MainUiState(hasLocationPermission = locationProvider.hasLocationPermission())
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var rideTimerJob: Job? = null
    private var rideGpsJob: Job? = null
    private var lastAltitudeMeters: Double? = null

    init {
        observeLocalData()
        refreshHealthConnectStatus()
        if (locationProvider.hasLocationPermission()) {
            locateAndScanCurrentPosition()
        }
    }

    private fun observeLocalData() {
        viewModelScope.launch {
            trailRepository.observeOfflineRegions().collect { regions ->
                _uiState.update { state ->
                    val savedNearby = isLocationSavedOffline(state.activeLocation, regions)
                    state.copy(
                        offlineRegions = regions,
                        isCurrentAreaSavedOffline = savedNearby
                    )
                }
            }
        }

        viewModelScope.launch {
            rideRepository.observeRecordedRides().collect { rides ->
                _uiState.update { it.copy(recordedRides = rides) }
            }
        }
    }

    fun selectDestination(destination: AppDestination) {
        _uiState.update { it.copy(currentDestination = destination) }
        if (destination == AppDestination.RIDE) {
            refreshHealthConnectStatus()
        }
    }

    fun onLocationPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(hasLocationPermission = granted) }
        if (granted) {
            locateAndScanCurrentPosition()
        } else {
            _uiState.update {
                it.copy(
                    locationUnavailableReason = "Location permission is required to scan trails near you, or choose a region manually."
                )
            }
        }
    }

    fun locateAndScanCurrentPosition() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLocating = true,
                    locationUnavailableReason = null,
                    trailErrorMessage = null
                )
            }

            when (val result = locationProvider.getCurrentLocation()) {
                is DeviceLocationResult.Success -> {
                    val place = LocationPlace(
                        name = result.label,
                        lat = result.lat,
                        lon = result.lon,
                        isCurrentGps = true
                    )
                    _uiState.update {
                        it.copy(
                            isLocating = false,
                            activeLocation = place,
                            hasLocationPermission = true,
                            isCurrentAreaSavedOffline = isLocationSavedOffline(place, it.offlineRegions)
                        )
                    }
                    scanTrailsAt(place.lat, place.lon)
                }

                is DeviceLocationResult.PermissionMissing -> {
                    _uiState.update {
                        it.copy(
                            isLocating = false,
                            hasLocationPermission = false,
                            locationUnavailableReason = "Enable location access to discover MTB trails within 20 miles."
                        )
                    }
                }

                is DeviceLocationResult.Unavailable -> {
                    _uiState.update {
                        it.copy(
                            isLocating = false,
                            locationUnavailableReason = "Unable to acquire a GPS fix right now. Ensure device location is on or pick a riding area."
                        )
                    }
                }
            }
        }
    }

    fun selectCustomLocation(place: LocationPlace) {
        _uiState.update {
            it.copy(
                activeLocation = place,
                locationUnavailableReason = null,
                isCurrentAreaSavedOffline = isLocationSavedOffline(place, it.offlineRegions),
                currentDestination = AppDestination.EXPLORE
            )
        }
        scanTrailsAt(place.lat, place.lon)
    }

    fun refreshActiveLocationTrails() {
        val active = _uiState.value.activeLocation
        if (active != null) {
            if (active.isCurrentGps && locationProvider.hasLocationPermission()) {
                locateAndScanCurrentPosition()
            } else {
                scanTrailsAt(active.lat, active.lon)
            }
        } else if (locationProvider.hasLocationPermission()) {
            locateAndScanCurrentPosition()
        }
    }

    private fun scanTrailsAt(lat: Double, lon: Double) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingTrails = true,
                    trailErrorMessage = null,
                    selectedTrail = null
                )
            }

            val result = trailRepository.fetchTrailsNear(lat, lon)
            result.onSuccess { scan ->
                _uiState.update { state ->
                    val filtered = applyFiltersAndSort(
                        trails = scan.trails,
                        query = state.searchQuery,
                        difficulty = state.selectedDifficulty,
                        sortOrder = state.sortOrder
                    )
                    state.copy(
                        isLoadingTrails = false,
                        trails = scan.trails,
                        filteredTrails = filtered,
                        isFromOfflineCache = scan.isFromOfflineCache,
                        activeOfflineRegionName = scan.offlineRegionName,
                        usedSingletrackFallback = scan.usedSingletrackFallback,
                        trailErrorMessage = null
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoadingTrails = false,
                        trailErrorMessage = error.localizedMessage
                            ?: "Could not reach OpenStreetMap Overpass servers. Check your connection or open an Offline Region."
                    )
                }
            }
        }
    }

    fun setViewMode(mode: TrailViewMode) {
        _uiState.update { it.copy(viewMode = mode) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { state ->
            state.copy(
                searchQuery = query,
                filteredTrails = applyFiltersAndSort(
                    trails = state.trails,
                    query = query,
                    difficulty = state.selectedDifficulty,
                    sortOrder = state.sortOrder
                )
            )
        }
    }

    fun selectDifficultyFilter(difficulty: TrailDifficulty?) {
        _uiState.update { state ->
            val next = if (state.selectedDifficulty == difficulty) null else difficulty
            state.copy(
                selectedDifficulty = next,
                filteredTrails = applyFiltersAndSort(
                    trails = state.trails,
                    query = state.searchQuery,
                    difficulty = next,
                    sortOrder = state.sortOrder
                )
            )
        }
    }

    fun selectSortOrder(sortOrder: TrailSortOrder) {
        _uiState.update { state ->
            state.copy(
                sortOrder = sortOrder,
                filteredTrails = applyFiltersAndSort(
                    trails = state.trails,
                    query = state.searchQuery,
                    difficulty = state.selectedDifficulty,
                    sortOrder = sortOrder
                )
            )
        }
    }

    fun openTrailDetail(trail: Trail) {
        _uiState.update {
            it.copy(
                selectedTrail = trail,
                isLoadingTrailGeometry = trail.geometry.size < 2
            )
        }
        if (trail.geometry.size < 2) {
            viewModelScope.launch {
                val geom = trailRepository.fetchTrailGeometry(trail)
                val updatedLength = if (trail.lengthMiles == null && geom.size >= 2) {
                    GeoUtils.polylineLengthMiles(geom).takeIf { it >= 0.05 }
                } else {
                    trail.lengthMiles
                }
                _uiState.update { state ->
                    val currentSelected = state.selectedTrail
                    if (currentSelected?.osmKey == trail.osmKey) {
                        val enriched = currentSelected.copy(
                            geometry = geom,
                            routeSegments = if (geom.size >= 2) listOf(geom) else currentSelected.routeSegments,
                            lengthMiles = updatedLength
                        )
                        state.copy(
                            selectedTrail = enriched,
                            isLoadingTrailGeometry = false,
                            trails = state.trails.map {
                                if (it.osmKey == enriched.osmKey) enriched else it
                            },
                            filteredTrails = state.filteredTrails.map {
                                if (it.osmKey == enriched.osmKey) enriched else it
                            }
                        )
                    } else {
                        state.copy(isLoadingTrailGeometry = false)
                    }
                }
            }
        }
    }

    fun closeTrailDetail() {
        _uiState.update { it.copy(selectedTrail = null, isLoadingTrailGeometry = false) }
    }

    fun openFullScreenMap(focusTrail: Trail? = null) {
        _uiState.update { state ->
            state.copy(
                isFullScreenMap = true,
                selectedTrail = focusTrail ?: state.selectedTrail
            )
        }
    }

    fun closeFullScreenMap() {
        _uiState.update { it.copy(isFullScreenMap = false) }
    }

    // Offline region operations
    fun downloadActiveRegionForOffline() {
        val location = _uiState.value.activeLocation ?: return
        downloadRegionForOffline(location.name, location.lat, location.lon)
    }

    fun downloadRegionForOffline(name: String, lat: Double, lon: Double) {
        if (_uiState.value.isDownloadingRegion) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isDownloadingRegion = true,
                    downloadingRegionName = name
                )
            }
            val result = trailRepository.downloadRegionForOffline(name, lat, lon)
            result.onSuccess { region ->
                _uiState.update {
                    it.copy(
                        isDownloadingRegion = false,
                        downloadingRegionName = null,
                        snackbarMessage = "Downloaded ${region.trailCount} trails in ${region.name} for offline use"
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isDownloadingRegion = false,
                        downloadingRegionName = null,
                        snackbarMessage = "Offline download failed: ${error.localizedMessage ?: "Network error"}"
                    )
                }
            }
        }
    }

    fun openOfflineRegion(region: OfflineRegion) {
        viewModelScope.launch {
            val place = LocationPlace(
                name = region.name,
                lat = region.centerLat,
                lon = region.centerLon,
                isCurrentGps = false
            )
            val trails = trailRepository.loadOfflineRegionTrails(
                regionId = region.id,
                referenceLat = region.centerLat,
                referenceLon = region.centerLon
            )
            _uiState.update { state ->
                state.copy(
                    currentDestination = AppDestination.EXPLORE,
                    activeLocation = place,
                    trails = trails,
                    filteredTrails = applyFiltersAndSort(
                        trails = trails,
                        query = state.searchQuery,
                        difficulty = state.selectedDifficulty,
                        sortOrder = state.sortOrder
                    ),
                    isLoadingTrails = false,
                    trailErrorMessage = null,
                    isFromOfflineCache = true,
                    activeOfflineRegionName = region.name,
                    isCurrentAreaSavedOffline = true,
                    selectedTrail = null
                )
            }
        }
    }

    fun deleteOfflineRegion(regionId: String) {
        viewModelScope.launch {
            trailRepository.deleteOfflineRegion(regionId)
            _uiState.update { it.copy(snackbarMessage = "Offline region removed") }
        }
    }

    fun updatePlaceSearchQuery(query: String) {
        _uiState.update { it.copy(placeSearchQuery = query, placeSearchError = null) }
    }

    fun searchPlaces() {
        val query = _uiState.value.placeSearchQuery.trim()
        if (query.length < 2) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSearchingPlaces = true, placeSearchError = null) }
            val res = trailRepository.searchPlaces(query)
            res.onSuccess { places ->
                _uiState.update {
                    it.copy(
                        isSearchingPlaces = false,
                        placeSearchResults = places,
                        placeSearchError = if (places.isEmpty()) "No matching locations found." else null
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isSearchingPlaces = false,
                        placeSearchError = err.localizedMessage ?: "Search failed"
                    )
                }
            }
        }
    }

    // Ride Recording & Health Connect / Google Fit Sync
    fun startRideForTrail(trail: Trail) {
        _uiState.update {
            it.copy(
                selectedTrail = null,
                currentDestination = AppDestination.RIDE
            )
        }
        if (_uiState.value.activeRide.status == RideRecordingStatus.IDLE) {
            startRide(trailName = trail.name)
        } else {
            _uiState.update {
                it.copy(snackbarMessage = "Finish or discard your current ride before starting a new one.")
            }
        }
    }

    fun startRide(trailName: String? = null) {
        if (!locationProvider.hasLocationPermission()) {
            _uiState.update {
                it.copy(snackbarMessage = "Location permission is required to record your GPS ride.")
            }
            return
        }
        lastAltitudeMeters = null
        val now = System.currentTimeMillis()
        _uiState.update {
            it.copy(
                activeRide = ActiveRideMetrics(
                    status = RideRecordingStatus.RECORDING,
                    targetTrailName = trailName,
                    startTimeMillis = now
                )
            )
        }
        launchRideLoops()
    }

    fun pauseRide() {
        rideTimerJob?.cancel()
        rideGpsJob?.cancel()
        _uiState.update { state ->
            state.copy(
                activeRide = state.activeRide.copy(
                    status = RideRecordingStatus.PAUSED,
                    currentSpeedMph = 0.0
                )
            )
        }
    }

    fun resumeRide() {
        if (_uiState.value.activeRide.status != RideRecordingStatus.PAUSED) return
        _uiState.update { state ->
            state.copy(
                activeRide = state.activeRide.copy(status = RideRecordingStatus.RECORDING)
            )
        }
        launchRideLoops()
    }

    fun discardRide() {
        rideTimerJob?.cancel()
        rideGpsJob?.cancel()
        lastAltitudeMeters = null
        _uiState.update {
            it.copy(
                activeRide = ActiveRideMetrics(),
                snackbarMessage = "Ride discarded"
            )
        }
    }

    fun finishAndSaveRide() {
        val current = _uiState.value.activeRide
        if (current.status == RideRecordingStatus.IDLE) return

        rideTimerJob?.cancel()
        rideGpsJob?.cancel()
        lastAltitudeMeters = null

        val endTime = System.currentTimeMillis()
        val durationSec = current.elapsedSeconds.coerceAtLeast(1L)
        val avgSpeedMph = if (durationSec > 0L) {
            current.distanceMiles / (durationSec / 3600.0)
        } else {
            0.0
        }
        val title = if (!current.targetTrailName.isNullOrBlank()) {
            "${current.targetTrailName} Ride"
        } else {
            "Mountain Bike Ride"
        }

        val ride = RecordedRide(
            id = UUID.randomUUID().toString(),
            title = title,
            trailName = current.targetTrailName,
            startTimeMillis = current.startTimeMillis.takeIf { it > 0L } ?: (endTime - durationSec * 1000L),
            endTimeMillis = endTime,
            durationSeconds = durationSec,
            distanceMiles = current.distanceMiles,
            avgSpeedMph = avgSpeedMph,
            maxSpeedMph = current.maxSpeedMph.coerceAtLeast(avgSpeedMph),
            elevationGainFeet = current.elevationGainFeet,
            routePoints = current.points,
            samples = current.samples,
            syncedToHealthConnect = false,
            healthConnectSessionId = null
        )

        _uiState.update { it.copy(activeRide = ActiveRideMetrics()) }

        viewModelScope.launch {
            rideRepository.saveRide(ride)
            val state = _uiState.value
            if (state.autoSyncToHealthConnect && state.healthConnectStatus.permissionsGranted) {
                syncRideToHealthConnect(ride)
            } else {
                _uiState.update {
                    it.copy(snackbarMessage = "Ride saved! Connect Health Connect to sync with Google Fit.")
                }
            }
        }
    }

    fun refreshHealthConnectStatus() {
        viewModelScope.launch {
            val status = healthConnectSyncManager.getStatus()
            _uiState.update { it.copy(healthConnectStatus = status) }
        }
    }

    fun onHealthConnectPermissionsResult(granted: Set<String>) {
        refreshHealthConnectStatus()
        if (granted.containsAll(HealthConnectSyncManager.CORE_PERMISSIONS)) {
            _uiState.update {
                it.copy(snackbarMessage = "Health Connect connected! Rides can now sync with Google Fit.")
            }
        } else {
            _uiState.update {
                it.copy(snackbarMessage = "Health Connect permissions were not granted.")
            }
        }
    }

    fun setAutoSyncToHealthConnect(enabled: Boolean) {
        _uiState.update { it.copy(autoSyncToHealthConnect = enabled) }
    }

    fun syncRideToHealthConnect(ride: RecordedRide) {
        viewModelScope.launch {
            _uiState.update { it.copy(syncingRideId = ride.id) }
            val result = healthConnectSyncManager.syncRide(ride)
            result.onSuccess { sessionId ->
                rideRepository.markRideSynced(ride.id, sessionId)
                _uiState.update {
                    it.copy(
                        syncingRideId = null,
                        snackbarMessage = "Synced \"${ride.title}\" to Health Connect (Google Fit)"
                    )
                }
            }.onFailure { err ->
                refreshHealthConnectStatus()
                _uiState.update {
                    it.copy(
                        syncingRideId = null,
                        snackbarMessage = "Sync failed: ${err.localizedMessage ?: "Health Connect error"}"
                    )
                }
            }
        }
    }

    fun deleteRide(rideId: String) {
        viewModelScope.launch {
            rideRepository.deleteRide(rideId)
            _uiState.update { it.copy(snackbarMessage = "Ride deleted") }
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    private fun launchRideLoops() {
        rideTimerJob?.cancel()
        rideGpsJob?.cancel()

        rideTimerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                _uiState.update { state ->
                    if (state.activeRide.status == RideRecordingStatus.RECORDING) {
                        state.copy(
                            activeRide = state.activeRide.copy(
                                elapsedSeconds = state.activeRide.elapsedSeconds + 1L
                            )
                        )
                    } else {
                        state
                    }
                }
            }
        }

        rideGpsJob = viewModelScope.launch {
            rideTracker.trackLocationUpdates()
                .catch { err ->
                    _uiState.update {
                        it.copy(snackbarMessage = "GPS tracking error: ${err.localizedMessage ?: "Unavailable"}")
                    }
                }
                .collect { location ->
                    processNewRideLocation(location)
                }
        }
    }

    private fun processNewRideLocation(location: Location) {
        if (location.hasAccuracy() && location.accuracy > 50f) return

        _uiState.update { state ->
            val current = state.activeRide
            if (current.status != RideRecordingStatus.RECORDING) return@update state

            val newPoint = GeoPoint(location.latitude, location.longitude)
            val prevPoint = current.points.lastOrNull()
            val segmentMiles = if (prevPoint != null) {
                GeoUtils.haversineMiles(prevPoint.lat, prevPoint.lon, newPoint.lat, newPoint.lon)
            } else {
                0.0
            }

            // Filter tiny stationary GPS jitter (< ~10 ft) after first point
            val shouldAppendPoint = prevPoint == null || segmentMiles >= 0.002
            val updatedDistance = if (prevPoint != null && segmentMiles >= 0.002) {
                current.distanceMiles + segmentMiles
            } else {
                current.distanceMiles
            }

            var updatedElevGainFeet = current.elevationGainFeet
            if (location.hasAltitude()) {
                val prevAlt = lastAltitudeMeters
                val alt = location.altitude
                if (prevAlt != null) {
                    val deltaMeters = alt - prevAlt
                    if (deltaMeters > 1.5) {
                        updatedElevGainFeet += deltaMeters * 3.28084
                        lastAltitudeMeters = alt
                    } else if (deltaMeters < -1.5) {
                        lastAltitudeMeters = alt
                    }
                } else {
                    lastAltitudeMeters = alt
                }
            }

            val speedMph = if (location.hasSpeed()) {
                (location.speed * 2.23694).coerceAtLeast(0.0)
            } else {
                current.currentSpeedMph
            }

            val sample = RideSample(
                lat = location.latitude,
                lon = location.longitude,
                altitudeMeters = if (location.hasAltitude()) location.altitude else null,
                speedMps = if (location.hasSpeed()) location.speed else null,
                timestampMillis = System.currentTimeMillis()
            )

            state.copy(
                activeRide = current.copy(
                    distanceMiles = updatedDistance,
                    currentSpeedMph = speedMph,
                    maxSpeedMph = maxOf(current.maxSpeedMph, speedMph),
                    elevationGainFeet = updatedElevGainFeet,
                    points = if (shouldAppendPoint) current.points + newPoint else current.points,
                    samples = current.samples + sample
                )
            )
        }
    }

    private fun applyFiltersAndSort(
        trails: List<Trail>,
        query: String,
        difficulty: TrailDifficulty?,
        sortOrder: TrailSortOrder
    ): List<Trail> {
        val q = query.trim().lowercase(Locale.US)
        val filtered = trails.filter { trail ->
            val matchesDiff = difficulty == null || trail.difficulty == difficulty
            val matchesQuery = q.isEmpty() ||
                trail.name.lowercase(Locale.US).contains(q) ||
                trail.surface.lowercase(Locale.US).contains(q) ||
                (trail.networkName?.lowercase(Locale.US)?.contains(q) == true) ||
                (trail.operatorName?.lowercase(Locale.US)?.contains(q) == true)
            matchesDiff && matchesQuery
        }

        return when (sortOrder) {
            TrailSortOrder.CLOSEST -> filtered.sortedBy { it.distanceFromUserMiles }
            TrailSortOrder.DIFFICULTY -> filtered.sortedWith(
                compareBy<Trail> { it.difficulty.rank }.thenBy { it.distanceFromUserMiles }
            )
            TrailSortOrder.LENGTH -> filtered.sortedByDescending { it.lengthMiles ?: -1.0 }
            TrailSortOrder.NAME -> filtered.sortedBy { it.name.lowercase(Locale.US) }
        }
    }

    private fun isLocationSavedOffline(
        location: LocationPlace?,
        regions: List<OfflineRegion>
    ): Boolean {
        if (location == null) return false
        return regions.any { region ->
            GeoUtils.haversineMiles(
                location.lat,
                location.lon,
                region.centerLat,
                region.centerLon
            ) <= 8.0
        }
    }

    override fun onCleared() {
        super.onCleared()
        rideTimerJob?.cancel()
        rideGpsJob?.cancel()
    }
}
