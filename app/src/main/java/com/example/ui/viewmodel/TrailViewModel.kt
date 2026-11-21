package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.location.Location
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.bluetooth.BleCyclingSensorManager
import com.example.data.bluetooth.CscSensorReading
import com.example.data.health.HealthConnectSyncManager
import com.example.data.local.SingletrackDatabase
import com.example.data.location.DeviceLocationResult
import com.example.data.location.LocationProvider
import com.example.data.location.RideTracker
import com.example.data.repository.GeoUtils
import com.example.data.repository.RideRepository
import com.example.data.repository.TrailRepository
import com.example.domain.model.ActiveRideMetrics
import com.example.domain.model.DiscoveredBleSensor
import com.example.domain.model.GeoPoint
import com.example.domain.model.LocationPlace
import com.example.domain.model.MeasurementUnit
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
    private val prefs = application.getSharedPreferences("singletrack_prefs", Context.MODE_PRIVATE)
    val healthConnectSyncManager = HealthConnectSyncManager(application)
    val bleSensorManager = BleCyclingSensorManager(application)

    private val _uiState = MutableStateFlow(
        MainUiState(
            measurementUnit = loadSavedUnit(),
            hasLocationPermission = locationProvider.hasLocationPermission(),
            bleSensorState = bleSensorManager.sensorState.value
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var rideTimerJob: Job? = null
    private var rideGpsJob: Job? = null
    private var detailLoadJob: Job? = null
    private var lastAltitudeMeters: Double? = null
    private var rideCadenceSum: Long = 0L
    private var rideCadenceCount: Int = 0
    private var usedBleSensorInRide: Boolean = false

    init {
        observeLocalData()
        observeBleSensors()
        refreshHealthConnectStatus()
        if (locationProvider.hasLocationPermission()) {
            locateAndScanCurrentPosition()
        }
        if (bleSensorManager.sensorState.value.pairedSensor != null && bleSensorManager.hasRequiredPermissions()) {
            bleSensorManager.connectPairedSensor()
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
                _uiState.update { state ->
                    val updatedSelected = state.selectedRide?.let { sel ->
                        rides.firstOrNull { it.id == sel.id }
                    }
                    state.copy(
                        recordedRides = rides,
                        selectedRide = updatedSelected,
                        isFullScreenRideMap = if (updatedSelected == null) false else state.isFullScreenRideMap
                    )
                }
            }
        }
    }

    private fun observeBleSensors() {
        viewModelScope.launch {
            bleSensorManager.sensorState.collect { sensorUi ->
                _uiState.update { it.copy(bleSensorState = sensorUi) }
            }
        }
        viewModelScope.launch {
            bleSensorManager.readings.collect { reading ->
                processBleSensorReading(reading)
            }
        }
    }

    fun selectDestination(destination: AppDestination) {
        _uiState.update { it.copy(currentDestination = destination) }
        if (destination == AppDestination.RIDE) {
            refreshHealthConnectStatus()
            bleSensorManager.refreshCapabilities()
        }
    }

    fun refreshBleCapabilities() {
        bleSensorManager.refreshCapabilities()
    }

    fun onBluetoothPermissionsResult(granted: Boolean, scanAfterGrant: Boolean = false) {
        bleSensorManager.refreshCapabilities()
        if (granted) {
            if (scanAfterGrant) {
                bleSensorManager.startScan()
            } else if (bleSensorManager.sensorState.value.pairedSensor != null) {
                bleSensorManager.connectPairedSensor()
            }
        } else {
            _uiState.update {
                it.copy(snackbarMessage = "Bluetooth permission is needed to scan or connect cycling sensors.")
            }
        }
    }

    fun startBleSensorScan() {
        bleSensorManager.startScan()
    }

    fun stopBleSensorScan() {
        bleSensorManager.stopScan()
    }

    fun pairBleSensor(sensor: DiscoveredBleSensor) {
        bleSensorManager.pairAndConnect(sensor)
    }

    fun connectPairedBleSensor() {
        bleSensorManager.connectPairedSensor()
    }

    fun forgetBleSensor() {
        bleSensorManager.forgetPairedSensor()
        _uiState.update { it.copy(snackbarMessage = "Bluetooth cycling sensor unpaired") }
    }

    fun setWheelCircumferenceMm(circumferenceMm: Int) {
        bleSensorManager.updateWheelCircumference(circumferenceMm)
    }

    fun openRideDetail(ride: RecordedRide) {
        _uiState.update {
            it.copy(
                selectedRide = ride,
                isFullScreenRideMap = false
            )
        }
    }

    fun closeRideDetail() {
        _uiState.update {
            it.copy(
                selectedRide = null,
                isFullScreenRideMap = false
            )
        }
    }

    fun openFullScreenRideMap() {
        _uiState.update { it.copy(isFullScreenRideMap = true) }
    }

    fun closeFullScreenRideMap() {
        _uiState.update { it.copy(isFullScreenRideMap = false) }
    }

    fun notifyMessage(message: String) {
        _uiState.update { it.copy(snackbarMessage = message) }
    }

    fun setMeasurementUnit(unit: MeasurementUnit) {
        prefs.edit { putString(KEY_MEASUREMENT_UNIT, unit.name) }
        _uiState.update { it.copy(measurementUnit = unit) }
    }

    fun toggleMeasurementUnit() {
        val next = if (_uiState.value.measurementUnit == MeasurementUnit.IMPERIAL) {
            MeasurementUnit.METRIC
        } else {
            MeasurementUnit.IMPERIAL
        }
        setMeasurementUnit(next)
    }

    private fun loadSavedUnit(): MeasurementUnit {
        val raw = prefs.getString(KEY_MEASUREMENT_UNIT, null)
        return MeasurementUnit.entries.firstOrNull { it.name == raw } ?: MeasurementUnit.IMPERIAL
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
        detailLoadJob?.cancel()
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingTrails = true,
                    trailErrorMessage = null,
                    selectedTrail = null,
                    isLoadingTrailGeometry = false,
                    isLoadingElevation = false
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
        detailLoadJob?.cancel()
        val needsGeometry = trail.geometry.size < 2 && trail.routeSegments.none { it.size >= 2 }
        val needsElevation = trail.elevationProfile == null

        _uiState.update {
            it.copy(
                selectedTrail = trail,
                isLoadingTrailGeometry = needsGeometry,
                isLoadingElevation = needsElevation
            )
        }

        if (!needsGeometry && !needsElevation) return

        detailLoadJob = viewModelScope.launch {
            try {
                var targetTrail = trail
                if (needsGeometry) {
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
                            targetTrail = enriched
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

                val activeSelected = _uiState.value.selectedTrail
                if (activeSelected?.osmKey == trail.osmKey) {
                    targetTrail = activeSelected
                }

                if (targetTrail.elevationProfile == null) {
                    val profile = trailRepository.fetchTrailElevation(targetTrail)
                    if (profile != null) {
                        _uiState.update { state ->
                            val currentSelected = state.selectedTrail
                            val updatedGain = profile.elevationGainFeet
                            val enrichFn: (Trail) -> Trail = { item ->
                                if (item.osmKey == trail.osmKey) {
                                    item.copy(
                                        elevationProfile = profile,
                                        elevationGainFeet = updatedGain
                                    )
                                } else {
                                    item
                                }
                            }
                            if (currentSelected?.osmKey == trail.osmKey) {
                                state.copy(
                                    selectedTrail = enrichFn(currentSelected),
                                    isLoadingElevation = false,
                                    trails = state.trails.map(enrichFn),
                                    filteredTrails = state.filteredTrails.map(enrichFn)
                                )
                            } else {
                                state.copy(
                                    trails = state.trails.map(enrichFn),
                                    filteredTrails = state.filteredTrails.map(enrichFn)
                                )
                            }
                        }
                    }
                }
            } finally {
                _uiState.update { state ->
                    if (state.selectedTrail?.osmKey == trail.osmKey) {
                        state.copy(
                            isLoadingTrailGeometry = false,
                            isLoadingElevation = false
                        )
                    } else {
                        state
                    }
                }
            }
        }
    }

    fun closeTrailDetail() {
        detailLoadJob?.cancel()
        _uiState.update {
            it.copy(
                selectedTrail = null,
                isLoadingTrailGeometry = false,
                isLoadingElevation = false
            )
        }
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
                    selectedTrail = null,
                    isLoadingTrailGeometry = false,
                    isLoadingElevation = false
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
        rideCadenceSum = 0L
        rideCadenceCount = 0
        usedBleSensorInRide = false
        val paired = bleSensorManager.sensorState.value.pairedSensor
        if (paired != null && bleSensorManager.hasRequiredPermissions()) {
            bleSensorManager.connectPairedSensor()
        }
        val now = System.currentTimeMillis()
        _uiState.update {
            it.copy(
                activeRide = ActiveRideMetrics(
                    status = RideRecordingStatus.RECORDING,
                    targetTrailName = trailName,
                    startTimeMillis = now,
                    sensorSourceLabel = paired?.name
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
                    currentSpeedMph = 0.0,
                    currentCadenceRpm = null
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
        rideCadenceSum = 0L
        rideCadenceCount = 0
        usedBleSensorInRide = false
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
        rideCadenceSum = 0L
        rideCadenceCount = 0
        val recordedWithBleSensor = usedBleSensorInRide
        usedBleSensorInRide = false

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
            healthConnectSessionId = null,
            avgCadenceRpm = current.avgCadenceRpm,
            maxCadenceRpm = current.maxCadenceRpm,
            sensorName = if (recordedWithBleSensor) current.sensorSourceLabel else null
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

    private fun processBleSensorReading(reading: CscSensorReading) {
        _uiState.update { state ->
            val current = state.activeRide
            if (current.status != RideRecordingStatus.RECORDING) return@update state
            usedBleSensorInRide = true

            var nextDistance = current.distanceMiles
            var nextSpeedMph = current.currentSpeedMph
            var nextMaxSpeedMph = current.maxSpeedMph

            if (reading.wheelDataPresent && reading.speedMps != null) {
                nextDistance += reading.incrementalDistanceMeters / 1609.344
                nextSpeedMph = reading.speedMps * 2.23694
                if (nextSpeedMph > nextMaxSpeedMph) {
                    nextMaxSpeedMph = nextSpeedMph
                }
            }

            var nextCadence = current.currentCadenceRpm
            var nextAvgCadence = current.avgCadenceRpm
            var nextMaxCadence = current.maxCadenceRpm

            if (reading.crankDataPresent && reading.cadenceRpm != null) {
                nextCadence = reading.cadenceRpm
                if (reading.cadenceRpm > 0) {
                    rideCadenceSum += reading.cadenceRpm
                    rideCadenceCount += 1
                    nextAvgCadence = (rideCadenceSum / rideCadenceCount).toInt()
                    nextMaxCadence = maxOf(current.maxCadenceRpm ?: 0, reading.cadenceRpm)
                }
            }

            state.copy(
                activeRide = current.copy(
                    distanceMiles = nextDistance,
                    currentSpeedMph = nextSpeedMph,
                    maxSpeedMph = nextMaxSpeedMph,
                    currentCadenceRpm = nextCadence,
                    avgCadenceRpm = nextAvgCadence,
                    maxCadenceRpm = nextMaxCadence,
                    sensorSourceLabel = reading.sensorName
                )
            )
        }
    }

    private fun processNewRideLocation(location: Location) {
        if (location.hasAccuracy() && location.accuracy > 50f) return

        val now = System.currentTimeMillis()
        val usingWheelSensor = bleSensorManager.hasFreshWheelReading(now)
        val freshCadence = bleSensorManager.getLatestFreshCadence(now)
        val freshWheelSpeedMps = if (usingWheelSensor) {
            bleSensorManager.sensorState.value.liveSpeedMph?.let { (it / 2.23694).toFloat() }
        } else {
            null
        }

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
            val updatedDistance = if (!usingWheelSensor && prevPoint != null && segmentMiles >= 0.002) {
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

            val speedMph = when {
                usingWheelSensor -> state.bleSensorState.liveSpeedMph ?: current.currentSpeedMph
                location.hasSpeed() -> (location.speed * 2.23694).coerceAtLeast(0.0)
                else -> current.currentSpeedMph
            }

            val sampleSpeedMps = freshWheelSpeedMps ?: if (location.hasSpeed()) location.speed else null

            val sample = RideSample(
                lat = location.latitude,
                lon = location.longitude,
                altitudeMeters = if (location.hasAltitude()) location.altitude else null,
                speedMps = sampleSpeedMps,
                timestampMillis = now,
                cadenceRpm = freshCadence
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
        detailLoadJob?.cancel()
        bleSensorManager.release()
    }

    companion object {
        private const val KEY_MEASUREMENT_UNIT = "measurement_unit"
    }
}
