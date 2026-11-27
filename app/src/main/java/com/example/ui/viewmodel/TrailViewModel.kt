package com.example.ui.viewmodel

import android.app.Application
import android.location.Location
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.audio.AndroidVoiceCueSpeaker
import com.example.data.audio.VoiceCueAlertEngine
import com.example.data.audio.VoiceCueSpeaker
import com.example.data.bluetooth.BleCyclingSensorManager
import com.example.data.bluetooth.CscSensorReading
import com.example.data.bluetooth.HrSensorReading
import com.example.data.export.GpxTrackParser
import com.example.data.health.HealthConnectSyncManager
import com.example.data.local.AppPreferences
import com.example.data.local.GpxTrackStorage
import com.example.data.local.SingletrackDatabase
import com.example.data.location.DeviceLocationResult
import com.example.data.location.LocationProvider
import com.example.data.location.RideTracker
import com.example.data.repository.GeoUtils
import com.example.data.repository.GpxNavigationEngine
import com.example.data.repository.RideRepository
import com.example.data.repository.TrailRepository
import com.example.domain.model.ActiveRideMetrics
import com.example.domain.model.DiscoveredBleSensor
import com.example.domain.model.GeoPoint
import com.example.domain.model.GpxNavigationState
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.util.UUID

enum class BlePermissionAction {
    CONNECT_ALL,
    SCAN_CSC,
    SCAN_HR,
    CONNECT_CSC,
    CONNECT_HR
}

class TrailViewModel(application: Application) : AndroidViewModel(application) {
    private val database = SingletrackDatabase.getInstance(application)
    private val trailRepository = TrailRepository(database.offlineTrailDao())
    private val rideRepository = RideRepository(database.offlineTrailDao())
    private val locationProvider = LocationProvider(application)
    private val rideTracker = RideTracker(application)
    private val gpxTrackStorage = GpxTrackStorage(application)
    private val voiceCueSpeaker: VoiceCueSpeaker = AndroidVoiceCueSpeaker(application)
    private val voiceCueAlertEngine = VoiceCueAlertEngine()
    private val appPreferences = AppPreferences(application)
    val healthConnectSyncManager = HealthConnectSyncManager(application)
    val bleSensorManager = BleCyclingSensorManager(application)

    private val _uiState = MutableStateFlow(
        MainUiState(
            measurementUnit = appPreferences.measurementUnit,
            loadOfflineMapsByDefault = appPreferences.loadOfflineMapsByDefault,
            defaultOfflineRegionId = appPreferences.defaultOfflineRegionId,
            openRecordingScreenOnStart = appPreferences.openRecordingScreenOnStart,
            voiceCuesEnabled = appPreferences.voiceCuesEnabled,
            gpxFullVoiceNavigationEnabled = appPreferences.gpxFullVoiceNavigationEnabled,
            autoSyncToHealthConnect = appPreferences.autoSyncToHealthConnect,
            hasLocationPermission = locationProvider.hasLocationPermission(),
            bleSensorState = bleSensorManager.sensorState.value
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var rideTimerJob: Job? = null
    private var rideGpsJob: Job? = null
    private var gpxNavGpsJob: Job? = null
    private var gpxInitFixJob: Job? = null
    private var trailScanJob: Job? = null
    private var detailLoadJob: Job? = null
    private var placeSearchJob: Job? = null
    private var locationLabelJob: Job? = null
    private val activeRidePoints = ArrayList<GeoPoint>()
    private val activeRideSamples = ArrayList<RideSample>()
    private var lastAltitudeMeters: Double? = null
    private var rideCadenceSum: Long = 0L
    private var rideCadenceCount: Int = 0
    private var usedBleSensorInRide: Boolean = false
    private var rideHrSum: Long = 0L
    private var rideHrCount: Int = 0
    private var usedHrSensorInRide: Boolean = false

    init {
        observeLocalData()
        observeBleSensors()
        refreshHealthConnectStatus()
        initializeStartupTrails()
        viewModelScope.launch {
            val savedTrack = withContext(Dispatchers.IO) { gpxTrackStorage.loadActiveTrack() }
            if (savedTrack != null) {
                _uiState.update { state ->
                    if (state.gpxNavigation.track == null) {
                        state.copy(gpxNavigation = GpxNavigationEngine.idleState(savedTrack))
                    } else {
                        state
                    }
                }
            }
        }
        viewModelScope.launch {
            yield()
            if (bleSensorManager.hasRequiredPermissions()) {
                if (bleSensorManager.sensorState.value.pairedSensor != null) {
                    bleSensorManager.connectPairedSensor()
                }
                if (bleSensorManager.sensorState.value.pairedHrSensor != null) {
                    bleSensorManager.connectPairedHrSensor()
                }
            }
        }
    }

    private fun initializeStartupTrails() {
        viewModelScope.launch {
            yield()
            if (_uiState.value.loadOfflineMapsByDefault) {
                val regions = trailRepository.getOfflineRegionsOnce()
                val preferredId = _uiState.value.defaultOfflineRegionId
                val targetRegion = regions.firstOrNull { it.id == preferredId } ?: regions.firstOrNull()
                if (targetRegion != null) {
                    openOfflineRegion(targetRegion)
                    return@launch
                }
            }
            if (locationProvider.hasLocationPermission()) {
                locateAndScanCurrentPosition()
            }
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
        viewModelScope.launch {
            bleSensorManager.hrReadings.collect { reading ->
                processBleHrReading(reading)
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

    fun onBluetoothPermissionsResult(
        granted: Boolean,
        action: BlePermissionAction = BlePermissionAction.CONNECT_ALL
    ) {
        bleSensorManager.refreshCapabilities()
        if (granted) {
            when (action) {
                BlePermissionAction.SCAN_CSC -> bleSensorManager.startScan()
                BlePermissionAction.SCAN_HR -> bleSensorManager.startHrScan()
                BlePermissionAction.CONNECT_CSC -> {
                    if (bleSensorManager.sensorState.value.pairedSensor != null) {
                        bleSensorManager.connectPairedSensor()
                    }
                }
                BlePermissionAction.CONNECT_HR -> {
                    if (bleSensorManager.sensorState.value.pairedHrSensor != null) {
                        bleSensorManager.connectPairedHrSensor()
                    }
                }
                BlePermissionAction.CONNECT_ALL -> {
                    if (bleSensorManager.sensorState.value.pairedSensor != null) {
                        bleSensorManager.connectPairedSensor()
                    }
                    if (bleSensorManager.sensorState.value.pairedHrSensor != null) {
                        bleSensorManager.connectPairedHrSensor()
                    }
                }
            }
        } else {
            _uiState.update {
                it.copy(snackbarMessage = "Bluetooth permission is needed to scan or connect cycling and heart rate sensors.")
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

    fun startBleHrScan() {
        bleSensorManager.startHrScan()
    }

    fun stopBleHrScan() {
        bleSensorManager.stopHrScan()
    }

    fun pairBleHrSensor(sensor: DiscoveredBleSensor) {
        bleSensorManager.pairAndConnectHr(sensor)
    }

    fun connectPairedBleHrSensor() {
        bleSensorManager.connectPairedHrSensor()
    }

    fun forgetBleHrSensor() {
        bleSensorManager.forgetPairedHrSensor()
        _uiState.update { it.copy(snackbarMessage = "Bluetooth heart rate monitor unpaired") }
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

    fun openRecordingScreen() {
        _uiState.update { it.copy(isRecordingScreenOpen = true) }
    }

    fun closeRecordingScreen() {
        _uiState.update { it.copy(isRecordingScreenOpen = false) }
    }

    // GPX Track Navigator operations
    fun selectGpxFile(uri: Uri) {
        viewModelScope.launch {
            val parsed = withContext(Dispatchers.IO) {
                GpxTrackParser.parseFromUri(getApplication(), uri)
            }
            parsed.onSuccess { track ->
                gpxInitFixJob?.cancel()
                gpxNavGpsJob?.cancel()
                voiceCueAlertEngine.resetGpxState()
                withContext(Dispatchers.IO) {
                    gpxTrackStorage.saveActiveTrack(track)
                }
                _uiState.update {
                    it.copy(
                        gpxNavigation = GpxNavigationEngine.idleState(track),
                        snackbarMessage = "Loaded GPX route \"${track.name}\" (${track.points.size} pts)"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(snackbarMessage = "Could not load GPX file: ${err.localizedMessage ?: "Invalid GPX"}")
                }
            }
        }
    }

    fun startGpxNavigation() {
        val currentNav = _uiState.value.gpxNavigation
        val track = currentNav.track ?: return
        if (!locationProvider.hasLocationPermission()) {
            _uiState.update {
                it.copy(snackbarMessage = "Location permission is required to navigate GPX tracks.")
            }
            return
        }

        voiceCueAlertEngine.resetGpxState()
        val initial = currentNav.userLocation?.let { loc ->
            GpxNavigationEngine.updateNavigationProgress(
                previousState = currentNav.copy(isNavigating = true, followUserLocation = true),
                track = track,
                userLat = loc.lat,
                userLon = loc.lon,
                userHeadingDegrees = currentNav.userHeadingDegrees
            )
        } ?: currentNav.copy(
            isNavigating = true,
            followUserLocation = true,
            turnInstruction = "Acquiring GPS signal for ${track.name}…"
        )

        _uiState.update { it.copy(gpxNavigation = initial) }
        maybeSpeakOffRouteAlert(currentNav, initial)
        if (_uiState.value.activeRide.status == RideRecordingStatus.IDLE) {
            startRide(trailName = track.name)
        } else {
            syncGpxNavigationGpsLoop()
        }

        if (initial.userLocation == null) {
            gpxInitFixJob?.cancel()
            gpxInitFixJob = viewModelScope.launch {
                val fix = locationProvider.getCurrentLocation(resolveLabel = false)
                if (fix is DeviceLocationResult.Success) {
                    var transition: Pair<GpxNavigationState, GpxNavigationState>? = null
                    _uiState.update { state ->
                        val nav = state.gpxNavigation
                        val activeTrack = nav.track ?: return@update state
                        if (!nav.isNavigating) return@update state
                        val next = GpxNavigationEngine.updateNavigationProgress(
                            previousState = nav,
                            track = activeTrack,
                            userLat = fix.lat,
                            userLon = fix.lon,
                            userHeadingDegrees = nav.userHeadingDegrees
                        )
                        transition = nav to next
                        state.copy(gpxNavigation = next)
                    }
                    transition?.let { (prev, next) ->
                        maybeSpeakOffRouteAlert(prev, next)
                    }
                }
            }
        }
    }

    fun stopGpxNavigation() {
        val wasNavigating = _uiState.value.gpxNavigation.isNavigating
        gpxInitFixJob?.cancel()
        gpxNavGpsJob?.cancel()
        gpxNavGpsJob = null
        voiceCueAlertEngine.resetGpxState()
        _uiState.update { state ->
            state.copy(
                gpxNavigation = GpxNavigationEngine.idleState(state.gpxNavigation.track)
            )
        }
        if (wasNavigating && _uiState.value.activeRide.status != RideRecordingStatus.IDLE) {
            finishAndSaveRide()
        } else if (_uiState.value.activeRide.status != RideRecordingStatus.RECORDING) {
            voiceCueSpeaker.stop()
        }
    }

    fun reverseGpxTrack() {
        val currentNav = _uiState.value.gpxNavigation
        val track = currentNav.track ?: return
        val reversed = GpxTrackParser.reverseTrack(track)
        voiceCueAlertEngine.resetGpxState()
        viewModelScope.launch(Dispatchers.IO) {
            gpxTrackStorage.saveActiveTrack(reversed)
        }
        var transition: Pair<GpxNavigationState, GpxNavigationState>? = null
        _uiState.update { state ->
            val nav = state.gpxNavigation
            val updated = if (nav.isNavigating && nav.userLocation != null) {
                GpxNavigationEngine.updateNavigationProgress(
                    previousState = nav,
                    track = reversed,
                    userLat = nav.userLocation.lat,
                    userLon = nav.userLocation.lon,
                    userHeadingDegrees = nav.userHeadingDegrees
                )
            } else {
                GpxNavigationEngine.idleState(reversed).copy(isFullScreenMap = nav.isFullScreenMap)
            }
            transition = nav to updated
            state.copy(
                gpxNavigation = updated,
                snackbarMessage = if (reversed.isReversed) "GPX track direction reversed" else "GPX track direction restored"
            )
        }
        transition?.let { (prev, next) ->
            maybeSpeakOffRouteAlert(prev, next)
        }
    }

    fun clearGpxTrack() {
        val wasNavigating = _uiState.value.gpxNavigation.isNavigating
        gpxInitFixJob?.cancel()
        gpxNavGpsJob?.cancel()
        gpxNavGpsJob = null
        voiceCueAlertEngine.resetGpxState()
        viewModelScope.launch(Dispatchers.IO) {
            gpxTrackStorage.clearActiveTrack()
        }
        _uiState.update { it.copy(gpxNavigation = GpxNavigationState()) }
        if (wasNavigating && _uiState.value.activeRide.status != RideRecordingStatus.IDLE) {
            finishAndSaveRide()
        } else if (_uiState.value.activeRide.status != RideRecordingStatus.RECORDING) {
            voiceCueSpeaker.stop()
        }
    }

    fun openGpxFullScreenMap() {
        _uiState.update { state ->
            if (state.gpxNavigation.track == null) state
            else state.copy(gpxNavigation = state.gpxNavigation.copy(isFullScreenMap = true))
        }
    }

    fun closeGpxFullScreenMap() {
        _uiState.update { state ->
            state.copy(gpxNavigation = state.gpxNavigation.copy(isFullScreenMap = false))
        }
    }

    fun toggleGpxFollowLocation() {
        _uiState.update { state ->
            state.copy(
                gpxNavigation = state.gpxNavigation.copy(
                    followUserLocation = !state.gpxNavigation.followUserLocation
                )
            )
        }
    }

    fun disableGpxFollowLocation() {
        _uiState.update { state ->
            if (!state.gpxNavigation.followUserLocation) state
            else state.copy(gpxNavigation = state.gpxNavigation.copy(followUserLocation = false))
        }
    }

    private fun syncGpxNavigationGpsLoop() {
        val state = _uiState.value
        val shouldRunDedicatedGps = state.gpxNavigation.isNavigating &&
            state.gpxNavigation.track != null &&
            state.activeRide.status != RideRecordingStatus.RECORDING &&
            locationProvider.hasLocationPermission()

        if (!shouldRunDedicatedGps) {
            gpxNavGpsJob?.cancel()
            gpxNavGpsJob = null
            return
        }
        if (gpxNavGpsJob?.isActive == true) return

        gpxNavGpsJob = viewModelScope.launch {
            rideTracker.trackLocationUpdates()
                .catch { err ->
                    _uiState.update {
                        it.copy(snackbarMessage = "GPX navigation GPS error: ${err.localizedMessage ?: "Unavailable"}")
                    }
                }
                .collect { location ->
                    if (location.hasAccuracy() && location.accuracy > 50f) return@collect
                    updateGpxLocation(location)
                }
        }
    }

    private fun updateGpxLocation(location: Location) {
        var transition: Pair<GpxNavigationState, GpxNavigationState>? = null
        _uiState.update { state ->
            val nav = state.gpxNavigation
            val track = nav.track ?: return@update state
            if (!nav.isNavigating) return@update state
            val heading = if (location.hasBearing()) location.bearing else nav.userHeadingDegrees
            val next = GpxNavigationEngine.updateNavigationProgress(
                previousState = nav,
                track = track,
                userLat = location.latitude,
                userLon = location.longitude,
                userHeadingDegrees = heading
            )
            transition = nav to next
            state.copy(gpxNavigation = next)
        }
        transition?.let { (prev, next) ->
            maybeSpeakOffRouteAlert(prev, next)
        }
    }

    fun setVoiceCuesEnabled(enabled: Boolean) {
        appPreferences.voiceCuesEnabled = enabled
        val state = _uiState.value
        if (!enabled && !(state.gpxFullVoiceNavigationEnabled && state.gpxNavigation.isNavigating)) {
            voiceCueSpeaker.stop()
        }
        _uiState.update { it.copy(voiceCuesEnabled = enabled) }
    }

    fun setGpxFullVoiceNavigationEnabled(enabled: Boolean) {
        appPreferences.gpxFullVoiceNavigationEnabled = enabled
        val state = _uiState.value
        if (!enabled && !state.voiceCuesEnabled) {
            voiceCueSpeaker.stop()
        }
        _uiState.update { it.copy(gpxFullVoiceNavigationEnabled = enabled) }
    }

    fun setOpenRecordingScreenOnStart(enabled: Boolean) {
        appPreferences.openRecordingScreenOnStart = enabled
        _uiState.update { it.copy(openRecordingScreenOnStart = enabled) }
    }

    fun notifyMessage(message: String) {
        _uiState.update { it.copy(snackbarMessage = message) }
    }

    fun setMeasurementUnit(unit: MeasurementUnit) {
        appPreferences.measurementUnit = unit
        val active = _uiState.value.activeRide
        if (active.status != RideRecordingStatus.IDLE) {
            voiceCueAlertEngine.syncMilestoneBaseline(active.distanceMiles, unit)
        }
        _uiState.update { it.copy(measurementUnit = unit) }
    }

    fun setLoadOfflineMapsByDefault(enabled: Boolean) {
        appPreferences.loadOfflineMapsByDefault = enabled
        _uiState.update { state ->
            val fallbackId = if (enabled && state.defaultOfflineRegionId == null) {
                state.offlineRegions.firstOrNull()?.id
            } else {
                state.defaultOfflineRegionId
            }
            if (fallbackId != state.defaultOfflineRegionId && fallbackId != null) {
                appPreferences.defaultOfflineRegionId = fallbackId
            }
            state.copy(
                loadOfflineMapsByDefault = enabled,
                defaultOfflineRegionId = fallbackId
            )
        }
    }

    fun setDefaultOfflineRegion(region: OfflineRegion) {
        appPreferences.defaultOfflineRegionId = region.id
        _uiState.update {
            it.copy(
                defaultOfflineRegionId = region.id,
                snackbarMessage = "${region.name} set as default offline map on launch"
            )
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

    fun locateAndScanCurrentPosition(forceRefresh: Boolean = false) {
        locationLabelJob?.cancel()
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLocating = true,
                    locationUnavailableReason = null,
                    trailErrorMessage = null
                )
            }

            when (val result = locationProvider.getCurrentLocation(resolveLabel = false)) {
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
                    scanTrailsAt(place.lat, place.lon, forceRefresh = forceRefresh)
                    locationLabelJob = viewModelScope.launch {
                        val resolvedLabel = locationProvider.reverseGeocodeLabel(place.lat, place.lon)
                        if (resolvedLabel.isNotBlank() && resolvedLabel != place.name) {
                            _uiState.update { state ->
                                val current = state.activeLocation
                                if (current != null &&
                                    current.isCurrentGps &&
                                    kotlin.math.abs(current.lat - place.lat) < 1e-5 &&
                                    kotlin.math.abs(current.lon - place.lon) < 1e-5
                                ) {
                                    state.copy(activeLocation = current.copy(name = resolvedLabel))
                                } else {
                                    state
                                }
                            }
                        }
                    }
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
        locationLabelJob?.cancel()
        _uiState.update {
            it.copy(
                activeLocation = place,
                locationUnavailableReason = null,
                isCurrentAreaSavedOffline = isLocationSavedOffline(place, it.offlineRegions),
                searchQuery = "",
                selectedDifficulty = null,
                currentDestination = AppDestination.EXPLORE
            )
        }
        scanTrailsAt(place.lat, place.lon)
    }

    fun refreshActiveLocationTrails() {
        val active = _uiState.value.activeLocation
        if (active != null) {
            if (active.isCurrentGps && locationProvider.hasLocationPermission()) {
                locateAndScanCurrentPosition(forceRefresh = true)
            } else {
                scanTrailsAt(active.lat, active.lon, forceRefresh = true)
            }
        } else if (locationProvider.hasLocationPermission()) {
            locateAndScanCurrentPosition(forceRefresh = true)
        }
    }

    private fun scanTrailsAt(lat: Double, lon: Double, forceRefresh: Boolean = false) {
        trailScanJob?.cancel()
        detailLoadJob?.cancel()
        trailScanJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingTrails = true,
                    trailErrorMessage = null,
                    selectedTrail = null,
                    isLoadingTrailGeometry = false,
                    isLoadingElevation = false
                )
            }

            val result = trailRepository.fetchTrailsNear(lat, lon, forceRefresh = forceRefresh)
            result.onSuccess { scan ->
                _uiState.update { state ->
                    val filtered = applyTrailFiltersAndSort(
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
                filteredTrails = applyTrailFiltersAndSort(
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
                filteredTrails = applyTrailFiltersAndSort(
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
                filteredTrails = applyTrailFiltersAndSort(
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
        trailScanJob?.cancel()
        detailLoadJob?.cancel()
        trailScanJob = viewModelScope.launch {
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
            val currentState = _uiState.value
            val sortedTrails = withContext(Dispatchers.Default) {
                applyTrailFiltersAndSort(
                    trails = trails,
                    query = currentState.searchQuery,
                    difficulty = currentState.selectedDifficulty,
                    sortOrder = currentState.sortOrder
                )
            }
            _uiState.update { state ->
                val filtered = if (
                    state.searchQuery == currentState.searchQuery &&
                    state.selectedDifficulty == currentState.selectedDifficulty &&
                    state.sortOrder == currentState.sortOrder
                ) {
                    sortedTrails
                } else {
                    applyTrailFiltersAndSort(
                        trails = trails,
                        query = state.searchQuery,
                        difficulty = state.selectedDifficulty,
                        sortOrder = state.sortOrder
                    )
                }
                state.copy(
                    currentDestination = AppDestination.EXPLORE,
                    activeLocation = place,
                    trails = trails,
                    filteredTrails = filtered,
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
            var updatedDefaultId: String? = null
            var defaultChanged = false
            _uiState.update { state ->
                if (state.defaultOfflineRegionId == regionId) {
                    val nextDefault = state.offlineRegions.firstOrNull { it.id != regionId }?.id
                    updatedDefaultId = nextDefault
                    defaultChanged = true
                    state.copy(
                        defaultOfflineRegionId = nextDefault,
                        snackbarMessage = "Offline region removed"
                    )
                } else {
                    state.copy(snackbarMessage = "Offline region removed")
                }
            }
            if (defaultChanged) {
                appPreferences.defaultOfflineRegionId = updatedDefaultId
            }
        }
    }

    fun updatePlaceSearchQuery(query: String) {
        placeSearchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _uiState.update {
                it.copy(
                    placeSearchQuery = query,
                    placeSearchResults = emptyList(),
                    isSearchingPlaces = false,
                    placeSearchError = null
                )
            }
            return
        }

        _uiState.update { it.copy(placeSearchQuery = query, placeSearchError = null) }
        if (trimmed.length >= 3) {
            placeSearchJob = viewModelScope.launch {
                delay(300L)
                executePlaceSearch(trimmed)
            }
        }
    }

    fun searchPlaces() {
        val query = _uiState.value.placeSearchQuery.trim()
        if (query.length < 2) return
        placeSearchJob?.cancel()
        placeSearchJob = viewModelScope.launch {
            executePlaceSearch(query)
        }
    }

    private suspend fun executePlaceSearch(query: String) {
        _uiState.update { it.copy(isSearchingPlaces = true, placeSearchError = null) }
        val res = trailRepository.searchPlaces(query)
        res.onSuccess { places ->
            if (_uiState.value.placeSearchQuery.trim().equals(query, ignoreCase = true)) {
                _uiState.update {
                    it.copy(
                        isSearchingPlaces = false,
                        placeSearchResults = places,
                        placeSearchError = if (places.isEmpty()) "No matching locations found." else null
                    )
                }
            } else {
                _uiState.update { it.copy(isSearchingPlaces = false) }
            }
        }.onFailure { err ->
            if (_uiState.value.placeSearchQuery.trim().equals(query, ignoreCase = true)) {
                _uiState.update {
                    it.copy(
                        isSearchingPlaces = false,
                        placeSearchError = err.localizedMessage ?: "Search failed"
                    )
                }
            } else {
                _uiState.update { it.copy(isSearchingPlaces = false) }
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
        activeRidePoints.clear()
        activeRideSamples.clear()
        lastAltitudeMeters = null
        rideCadenceSum = 0L
        rideCadenceCount = 0
        usedBleSensorInRide = false
        rideHrSum = 0L
        rideHrCount = 0
        usedHrSensorInRide = false
        voiceCueAlertEngine.resetRideMilestones()
        val paired = bleSensorManager.sensorState.value.pairedSensor
        val pairedHr = bleSensorManager.sensorState.value.pairedHrSensor
        if (bleSensorManager.hasRequiredPermissions()) {
            if (paired != null) {
                bleSensorManager.connectPairedSensor()
            }
            if (pairedHr != null) {
                bleSensorManager.connectPairedHrSensor()
            }
        }
        val now = System.currentTimeMillis()
        _uiState.update { state ->
            state.copy(
                isRecordingScreenOpen = if (state.openRecordingScreenOnStart) true else state.isRecordingScreenOpen,
                activeRide = ActiveRideMetrics(
                    status = RideRecordingStatus.RECORDING,
                    targetTrailName = trailName,
                    startTimeMillis = now,
                    sensorSourceLabel = paired?.name,
                    hrSensorSourceLabel = pairedHr?.name
                )
            )
        }
        launchRideLoops()
        syncGpxNavigationGpsLoop()
    }

    fun pauseRide() {
        rideTimerJob?.cancel()
        rideGpsJob?.cancel()
        _uiState.update { state ->
            state.copy(
                activeRide = state.activeRide.copy(
                    status = RideRecordingStatus.PAUSED,
                    currentSpeedMph = 0.0,
                    currentCadenceRpm = null,
                    currentHeartRateBpm = null
                )
            )
        }
        syncGpxNavigationGpsLoop()
    }

    fun resumeRide() {
        if (_uiState.value.activeRide.status != RideRecordingStatus.PAUSED) return
        _uiState.update { state ->
            state.copy(
                activeRide = state.activeRide.copy(status = RideRecordingStatus.RECORDING)
            )
        }
        launchRideLoops()
        syncGpxNavigationGpsLoop()
    }

    fun discardRide() {
        rideTimerJob?.cancel()
        rideGpsJob?.cancel()
        activeRidePoints.clear()
        activeRideSamples.clear()
        lastAltitudeMeters = null
        rideCadenceSum = 0L
        rideCadenceCount = 0
        usedBleSensorInRide = false
        rideHrSum = 0L
        rideHrCount = 0
        usedHrSensorInRide = false
        voiceCueAlertEngine.resetRideMilestones()
        if (!_uiState.value.gpxNavigation.isNavigating) {
            voiceCueSpeaker.stop()
        }
        _uiState.update {
            it.copy(
                activeRide = ActiveRideMetrics(),
                isRecordingScreenOpen = false,
                snackbarMessage = "Ride discarded"
            )
        }
        syncGpxNavigationGpsLoop()
    }

    fun finishAndSaveRide() {
        val current = _uiState.value.activeRide
        if (current.status == RideRecordingStatus.IDLE) return

        rideTimerJob?.cancel()
        rideGpsJob?.cancel()
        val capturedPoints = if (activeRidePoints.isNotEmpty()) activeRidePoints.toList() else current.points
        val capturedSamples = if (activeRideSamples.isNotEmpty()) activeRideSamples.toList() else current.samples
        activeRidePoints.clear()
        activeRideSamples.clear()
        lastAltitudeMeters = null
        rideCadenceSum = 0L
        rideCadenceCount = 0
        val recordedWithBleSensor = usedBleSensorInRide
        usedBleSensorInRide = false
        rideHrSum = 0L
        rideHrCount = 0
        val recordedWithHrSensor = usedHrSensorInRide
        usedHrSensorInRide = false
        voiceCueAlertEngine.resetRideMilestones()
        if (!_uiState.value.gpxNavigation.isNavigating) {
            voiceCueSpeaker.stop()
        }

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
            routePoints = capturedPoints,
            samples = capturedSamples,
            syncedToHealthConnect = false,
            healthConnectSessionId = null,
            avgCadenceRpm = current.avgCadenceRpm,
            maxCadenceRpm = current.maxCadenceRpm,
            sensorName = if (recordedWithBleSensor) current.sensorSourceLabel else null,
            avgHeartRateBpm = current.avgHeartRateBpm,
            maxHeartRateBpm = current.maxHeartRateBpm,
            hrSensorName = if (recordedWithHrSensor) current.hrSensorSourceLabel else null
        )

        _uiState.update {
            it.copy(
                activeRide = ActiveRideMetrics(),
                isRecordingScreenOpen = false
            )
        }
        syncGpxNavigationGpsLoop()

        viewModelScope.launch {
            rideRepository.saveRide(ride)
            val state = _uiState.value
            val willAutoHealthConnect = state.autoSyncToHealthConnect && state.healthConnectStatus.permissionsGranted

            if (willAutoHealthConnect) {
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
        appPreferences.autoSyncToHealthConnect = enabled
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
        if (_uiState.value.activeRide.status != RideRecordingStatus.RECORDING) return
        usedBleSensorInRide = true

        val updatedAvgCadence: Int? = if (reading.crankDataPresent && reading.cadenceRpm != null && reading.cadenceRpm > 0) {
            rideCadenceSum += reading.cadenceRpm
            rideCadenceCount += 1
            (rideCadenceSum / rideCadenceCount).toInt()
        } else {
            null
        }

        var updatedRideSnapshot: ActiveRideMetrics? = null
        _uiState.update { state ->
            val current = state.activeRide
            if (current.status != RideRecordingStatus.RECORDING) return@update state

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
                    nextAvgCadence = updatedAvgCadence ?: nextAvgCadence
                    nextMaxCadence = maxOf(current.maxCadenceRpm ?: 0, reading.cadenceRpm)
                }
            }

            val updatedRide = current.copy(
                distanceMiles = nextDistance,
                currentSpeedMph = nextSpeedMph,
                maxSpeedMph = nextMaxSpeedMph,
                currentCadenceRpm = nextCadence,
                avgCadenceRpm = nextAvgCadence,
                maxCadenceRpm = nextMaxCadence,
                sensorSourceLabel = reading.sensorName
            )
            updatedRideSnapshot = updatedRide
            state.copy(activeRide = updatedRide)
        }
        updatedRideSnapshot?.let { maybeSpeakDistanceMilestone(it) }
    }

    private fun processBleHrReading(reading: HrSensorReading) {
        if (_uiState.value.activeRide.status != RideRecordingStatus.RECORDING) return
        usedHrSensorInRide = true

        rideHrSum += reading.heartRateBpm
        rideHrCount += 1
        val nextAvgHr = (rideHrSum / rideHrCount).toInt()

        _uiState.update { state ->
            val current = state.activeRide
            if (current.status != RideRecordingStatus.RECORDING) return@update state

            val nextMaxHr = maxOf(current.maxHeartRateBpm ?: 0, reading.heartRateBpm)

            state.copy(
                activeRide = current.copy(
                    currentHeartRateBpm = reading.heartRateBpm,
                    avgHeartRateBpm = nextAvgHr,
                    maxHeartRateBpm = nextMaxHr,
                    hrSensorSourceLabel = reading.sensorName
                )
            )
        }
    }

    private fun processNewRideLocation(location: Location) {
        if (location.hasAccuracy() && location.accuracy > 50f) return

        val now = System.currentTimeMillis()
        val usingWheelSensor = bleSensorManager.hasFreshWheelReading(now)
        val freshCadence = bleSensorManager.getLatestFreshCadence(now)
        val freshHeartRate = bleSensorManager.getLatestFreshHeartRate(now)
        val freshWheelSpeedMps = if (usingWheelSensor) {
            bleSensorManager.sensorState.value.liveSpeedMph?.let { (it / 2.23694).toFloat() }
        } else {
            null
        }

        var gpxTransition: Pair<GpxNavigationState, GpxNavigationState>? = null
        var updatedRideSnapshot: ActiveRideMetrics? = null

        _uiState.update { state ->
            val current = state.activeRide
            if (current.status != RideRecordingStatus.RECORDING) return@update state

            val newPoint = GeoPoint(location.latitude, location.longitude)
            val prevPoint = activeRidePoints.lastOrNull() ?: current.points.lastOrNull()
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
                cadenceRpm = freshCadence,
                heartRateBpm = freshHeartRate
            )

            val currentAltFeet = if (location.hasAltitude()) {
                location.altitude * 3.28084
            } else {
                current.currentAltitudeFeet
            }

            val prevGpxNav = state.gpxNavigation
            val nextGpxNav = if (prevGpxNav.isNavigating && prevGpxNav.track != null) {
                val heading = if (location.hasBearing()) location.bearing else prevGpxNav.userHeadingDegrees
                GpxNavigationEngine.updateNavigationProgress(
                    previousState = prevGpxNav,
                    track = prevGpxNav.track,
                    userLat = location.latitude,
                    userLon = location.longitude,
                    userHeadingDegrees = heading
                ).also { updated ->
                    gpxTransition = prevGpxNav to updated
                }
            } else {
                prevGpxNav
            }

            activeRideSamples.add(sample)
            val updatedPoints = if (shouldAppendPoint) {
                activeRidePoints.add(newPoint)
                activeRidePoints.toList()
            } else {
                current.points
            }

            val updatedRide = current.copy(
                distanceMiles = updatedDistance,
                currentSpeedMph = speedMph,
                maxSpeedMph = maxOf(current.maxSpeedMph, speedMph),
                elevationGainFeet = updatedElevGainFeet,
                currentAltitudeFeet = currentAltFeet,
                points = updatedPoints
            )
            updatedRideSnapshot = updatedRide

            state.copy(
                gpxNavigation = nextGpxNav,
                activeRide = updatedRide
            )
        }

        updatedRideSnapshot?.let { maybeSpeakDistanceMilestone(it) }
        gpxTransition?.let { (prev, next) -> maybeSpeakOffRouteAlert(prev, next) }
    }

    private fun maybeSpeakOffRouteAlert(
        previousState: GpxNavigationState,
        newState: GpxNavigationState
    ) {
        val currentState = _uiState.value
        val cue = voiceCueAlertEngine.evaluateGpxNavigationCue(
            offRouteAlertsEnabled = currentState.voiceCuesEnabled,
            fullVoiceNavEnabled = currentState.gpxFullVoiceNavigationEnabled,
            previousState = previousState,
            newState = newState,
            unit = currentState.measurementUnit
        )
        if (cue != null) {
            voiceCueSpeaker.speak(cue.text, interrupt = cue.interrupt)
        }
    }

    private fun maybeSpeakDistanceMilestone(rideMetrics: ActiveRideMetrics) {
        val currentState = _uiState.value
        val cue = voiceCueAlertEngine.evaluateDistanceMilestoneCue(
            isEnabled = currentState.voiceCuesEnabled,
            rideStatus = rideMetrics.status,
            distanceMiles = rideMetrics.distanceMiles,
            elapsedSeconds = rideMetrics.elapsedSeconds,
            unit = currentState.measurementUnit
        )
        if (cue != null) {
            voiceCueSpeaker.speak(cue, interrupt = false)
        }
    }

    override fun onCleared() {
        super.onCleared()
        rideTimerJob?.cancel()
        rideGpsJob?.cancel()
        gpxInitFixJob?.cancel()
        gpxNavGpsJob?.cancel()
        trailScanJob?.cancel()
        detailLoadJob?.cancel()
        voiceCueSpeaker.release()
        bleSensorManager.release()
    }
}
