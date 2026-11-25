package com.example

import android.Manifest
import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.outlined.DirectionsBike
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.bluetooth.BleCyclingSensorManager
import com.example.data.export.GpxExporter
import com.example.data.health.HealthConnectSyncManager
import com.example.domain.model.RecordedRide
import com.example.domain.model.RideRecordingStatus
import com.example.ui.viewmodel.MainUiState
import com.example.ui.components.FormatUtils
import com.example.ui.components.GpxNavigatorTopoMap
import com.example.ui.components.LocationPickerSheet
import com.example.ui.components.RecordedRideTopoMap
import com.example.ui.components.TrailRadarMap
import com.example.ui.components.UnitSettingsDialog
import com.example.ui.screens.ExploreScreen
import com.example.ui.screens.LiveRideCockpitScreen
import com.example.ui.screens.OfflineRegionsScreen
import com.example.ui.screens.RideDetailScreen
import com.example.ui.screens.RideScreen
import com.example.ui.screens.TrailDetailScreen
import com.example.ui.theme.SingletrackTheme
import com.example.ui.viewmodel.AppDestination
import com.example.ui.viewmodel.BlePermissionAction
import com.example.ui.viewmodel.TrailViewModel

private enum class PickerPurpose {
    NONE,
    EXPLORE_LOCATION,
    OFFLINE_DOWNLOAD
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SingletrackTheme {
                SingletrackApp()
            }
        }
    }
}

@Composable
fun SingletrackApp(
    viewModel: TrailViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var pickerPurpose by remember { mutableStateOf(PickerPurpose.NONE) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var pendingBlePermissionAction by remember { mutableStateOf(BlePermissionAction.CONNECT_ALL) }
    var pendingGpxExportRide by remember { mutableStateOf<RecordedRide?>(null) }
    var pendingStartGpxNavAfterPermission by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.onLocationPermissionResult(granted)
        if (granted && pendingStartGpxNavAfterPermission) {
            pendingStartGpxNavAfterPermission = false
            viewModel.startGpxNavigation()
        } else {
            pendingStartGpxNavAfterPermission = false
        }
    }

    val openGpxDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.selectGpxFile(uri)
        }
    }

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = BleCyclingSensorManager.requiredPermissions().all { perm ->
            permissions[perm] == true
        }
        val action = pendingBlePermissionAction
        pendingBlePermissionAction = BlePermissionAction.CONNECT_ALL
        viewModel.onBluetoothPermissionsResult(granted, action = action)
    }

    val saveGpxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/gpx+xml")
    ) { uri ->
        val targetRide = pendingGpxExportRide
        pendingGpxExportRide = null
        if (uri != null && targetRide != null) {
            runCatching {
                GpxExporter.writeGpxToUri(context, uri, targetRide)
            }.onSuccess {
                viewModel.notifyMessage("Saved ${GpxExporter.buildSuggestedFileName(targetRide)}")
            }.onFailure { err ->
                viewModel.notifyMessage("Failed to save GPX: ${err.localizedMessage ?: "Write error"}")
            }
        }
    }

    val healthConnectPermissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { grantedPermissions ->
        viewModel.onHealthConnectPermissionsResult(grantedPermissions)
    }

    LaunchedEffect(state.snackbarMessage) {
        val message = state.snackbarMessage
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearSnackbar()
        }
    }

    LaunchedEffect(showSettingsDialog) {
        if (showSettingsDialog) {
            viewModel.refreshBleCapabilities()
            viewModel.refreshHealthConnectStatus()
        }
    }

    val requestLocationPermission: () -> Unit = {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    val shareRideGpx: (RecordedRide) -> Unit = { ride ->
        runCatching {
            val intent = GpxExporter.buildShareGpxIntent(context, ride)
            context.startActivity(intent)
        }.onFailure { err ->
            viewModel.notifyMessage("Could not share GPX: ${err.localizedMessage ?: "No compatible app"}")
        }
    }

    val saveRideGpxFile: (RecordedRide) -> Unit = { ride ->
        pendingGpxExportRide = ride
        saveGpxLauncher.launch(GpxExporter.buildSuggestedFileName(ride))
    }

    val requestBleScan: () -> Unit = {
        if (viewModel.bleSensorManager.hasRequiredPermissions()) {
            viewModel.startBleSensorScan()
        } else {
            pendingBlePermissionAction = BlePermissionAction.SCAN_CSC
            bluetoothPermissionLauncher.launch(BleCyclingSensorManager.requiredPermissions())
        }
    }

    val connectPairedBle: () -> Unit = {
        if (viewModel.bleSensorManager.hasRequiredPermissions()) {
            viewModel.connectPairedBleSensor()
        } else {
            pendingBlePermissionAction = BlePermissionAction.CONNECT_CSC
            bluetoothPermissionLauncher.launch(BleCyclingSensorManager.requiredPermissions())
        }
    }

    val requestBleHrScan: () -> Unit = {
        if (viewModel.bleSensorManager.hasRequiredPermissions()) {
            viewModel.startBleHrScan()
        } else {
            pendingBlePermissionAction = BlePermissionAction.SCAN_HR
            bluetoothPermissionLauncher.launch(BleCyclingSensorManager.requiredPermissions())
        }
    }

    val connectPairedBleHr: () -> Unit = {
        if (viewModel.bleSensorManager.hasRequiredPermissions()) {
            viewModel.connectPairedBleHrSensor()
        } else {
            pendingBlePermissionAction = BlePermissionAction.CONNECT_HR
            bluetoothPermissionLauncher.launch(BleCyclingSensorManager.requiredPermissions())
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTabletOrWide = maxWidth >= 600.dp
        val isExpanded = maxWidth >= 840.dp
        val showCompactTrailDetail = !isExpanded &&
            state.currentDestination == AppDestination.EXPLORE &&
            state.selectedTrail != null
        val showCompactRideDetail = !isExpanded &&
            state.currentDestination == AppDestination.RIDE &&
            state.selectedRide != null

        val isDark = isSystemInDarkTheme()
        val view = LocalView.current
        val selectedTrail = state.selectedTrail
        val selectedRide = state.selectedRide
        val activeLocation = state.activeLocation
        val gpxTrack = state.gpxNavigation.track
        val hasDarkTopHeader = state.isRecordingScreenOpen || (
            !state.isFullScreenMap &&
                !state.isFullScreenRideMap &&
                !state.gpxNavigation.isFullScreenMap &&
                !showCompactTrailDetail &&
                !showCompactRideDetail &&
                state.currentDestination == AppDestination.EXPLORE &&
                !(isExpanded && selectedTrail != null)
            )

        SideEffect {
            if (!view.isInEditMode) {
                val window = (view.context as? Activity)?.window
                if (window != null) {
                    val controller = WindowCompat.getInsetsController(window, view)
                    controller.isAppearanceLightStatusBars = !isDark && !hasDarkTopHeader
                    controller.isAppearanceLightNavigationBars = !isDark
                }
            }
        }

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!isTabletOrWide && !showCompactTrailDetail && !showCompactRideDetail) {
                    NavigationBar(
                        windowInsets = WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                        )
                    ) {
                        AppDestination.entries.forEach { dest ->
                            val selected = state.currentDestination == dest
                            NavigationBarItem(
                                selected = selected,
                                onClick = { viewModel.selectDestination(dest) },
                                icon = {
                                    DestinationIcon(
                                        destination = dest,
                                        selected = selected,
                                        offlineCount = state.offlineRegions.size,
                                        isRecording = state.activeRide.status != RideRecordingStatus.IDLE
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag("nav_${dest.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            val layoutDirection = LocalLayoutDirection.current
            val topInset = innerPadding.calculateTopPadding()
            val bottomInset = innerPadding.calculateBottomPadding()
            val startInset = innerPadding.calculateStartPadding(layoutDirection)
            val endInset = innerPadding.calculateEndPadding(layoutDirection)

            Row(
                modifier = Modifier.fillMaxSize()
            ) {
                if (isTabletOrWide) {
                    NavigationRail(
                        windowInsets = WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Vertical + WindowInsetsSides.Start
                        )
                    ) {
                        AppDestination.entries.forEach { dest ->
                            val selected = state.currentDestination == dest
                            NavigationRailItem(
                                selected = selected,
                                onClick = { viewModel.selectDestination(dest) },
                                icon = {
                                    DestinationIcon(
                                        destination = dest,
                                        selected = selected,
                                        offlineCount = state.offlineRegions.size,
                                        isRecording = state.activeRide.status != RideRecordingStatus.IDLE
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag("rail_${dest.name.lowercase()}")
                            )
                        }
                    }
                    VerticalDivider()
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(
                            start = if (isTabletOrWide) 0.dp else startInset,
                            end = endInset
                        )
                        .consumeWindowInsets(innerPadding),
                    contentAlignment = Alignment.TopCenter
                ) {
                    val launchSelectGpxFile: () -> Unit = {
                        openGpxDocumentLauncher.launch(
                            arrayOf(
                                "application/gpx+xml",
                                "application/xml",
                                "text/xml",
                                "application/octet-stream",
                                "*/*"
                            )
                        )
                    }
                    val startOrRequestGpxNav: () -> Unit = {
                        if (state.hasLocationPermission) {
                            viewModel.startGpxNavigation()
                        } else {
                            pendingStartGpxNavAfterPermission = true
                            requestLocationPermission()
                        }
                    }

                    when {
                        showCompactTrailDetail && selectedTrail != null -> {
                            TrailDetailScreen(
                                trail = selectedTrail,
                                isLoadingGeometry = state.isLoadingTrailGeometry,
                                isLoadingElevation = state.isLoadingElevation,
                                measurementUnit = state.measurementUnit,
                                showBackNavigation = true,
                                onBack = viewModel::closeTrailDetail,
                                onOpenSettings = { showSettingsDialog = true },
                                onRecordRideOnTrail = viewModel::startRideForTrail,
                                onExpandOnTopoMap = { trail -> viewModel.openFullScreenMap(focusTrail = trail) },
                                topInset = topInset,
                                bottomInset = bottomInset
                            )
                        }

                        showCompactRideDetail && selectedRide != null -> {
                            RideDetailPane(
                                ride = selectedRide,
                                state = state,
                                viewModel = viewModel,
                                showBack = true,
                                onOpenSettings = { showSettingsDialog = true },
                                onShareRideGpx = shareRideGpx,
                                onSaveRideGpxFile = saveRideGpxFile,
                                onRequestHealthConnect = {
                                    healthConnectPermissionLauncher.launch(
                                        HealthConnectSyncManager.REQUIRED_PERMISSIONS
                                    )
                                },
                                paneTopInset = topInset,
                                bottomInset = bottomInset
                            )
                        }

                        state.currentDestination == AppDestination.EXPLORE -> {
                            if (isExpanded && selectedTrail != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(top = topInset)
                                ) {
                                    ExploreScreen(
                                        state = state,
                                        onRequestLocationPermission = requestLocationPermission,
                                        onOpenLocationPicker = {
                                            pickerPurpose = PickerPurpose.EXPLORE_LOCATION
                                        },
                                        onOpenSettings = { showSettingsDialog = true },
                                        onRefreshTrails = viewModel::refreshActiveLocationTrails,
                                        onDownloadRegion = viewModel::downloadActiveRegionForOffline,
                                        onSearchQueryChange = viewModel::updateSearchQuery,
                                        onSelectDifficulty = viewModel::selectDifficultyFilter,
                                        onSelectSortOrder = viewModel::selectSortOrder,
                                        onSelectViewMode = viewModel::setViewMode,
                                        onOpenTrailDetail = viewModel::openTrailDetail,
                                        onExpandFullScreenMap = { viewModel.openFullScreenMap() },
                                        modifier = Modifier.weight(1f),
                                        topInset = 0.dp,
                                        bottomInset = bottomInset
                                    )
                                    VerticalDivider()
                                    TrailDetailScreen(
                                        trail = selectedTrail,
                                        isLoadingGeometry = state.isLoadingTrailGeometry,
                                        isLoadingElevation = state.isLoadingElevation,
                                        measurementUnit = state.measurementUnit,
                                        showBackNavigation = false,
                                        onBack = viewModel::closeTrailDetail,
                                        onOpenSettings = { showSettingsDialog = true },
                                        onRecordRideOnTrail = viewModel::startRideForTrail,
                                        onExpandOnTopoMap = { trail -> viewModel.openFullScreenMap(focusTrail = trail) },
                                        modifier = Modifier.weight(1f),
                                        topInset = 0.dp,
                                        bottomInset = bottomInset
                                    )
                                }
                            } else {
                                ExploreScreen(
                                    state = state,
                                    onRequestLocationPermission = requestLocationPermission,
                                    onOpenLocationPicker = {
                                        pickerPurpose = PickerPurpose.EXPLORE_LOCATION
                                    },
                                    onOpenSettings = { showSettingsDialog = true },
                                    onRefreshTrails = viewModel::refreshActiveLocationTrails,
                                    onDownloadRegion = viewModel::downloadActiveRegionForOffline,
                                    onSearchQueryChange = viewModel::updateSearchQuery,
                                    onSelectDifficulty = viewModel::selectDifficultyFilter,
                                    onSelectSortOrder = viewModel::selectSortOrder,
                                    onSelectViewMode = viewModel::setViewMode,
                                    onOpenTrailDetail = viewModel::openTrailDetail,
                                    onExpandFullScreenMap = { viewModel.openFullScreenMap() },
                                    modifier = Modifier.widthIn(max = 760.dp),
                                    topInset = topInset,
                                    bottomInset = bottomInset
                                )
                            }
                        }

                        state.currentDestination == AppDestination.OFFLINE -> {
                            OfflineRegionsScreen(
                                regions = state.offlineRegions,
                                activeLocation = state.activeLocation,
                                measurementUnit = state.measurementUnit,
                                loadOfflineByDefault = state.loadOfflineMapsByDefault,
                                defaultOfflineRegionId = state.defaultOfflineRegionId,
                                isDownloadingRegion = state.isDownloadingRegion,
                                downloadingRegionName = state.downloadingRegionName,
                                onOpenSettings = { showSettingsDialog = true },
                                onToggleLoadOfflineByDefault = viewModel::setLoadOfflineMapsByDefault,
                                onSetDefaultOfflineRegion = viewModel::setDefaultOfflineRegion,
                                onDownloadCurrentArea = viewModel::downloadActiveRegionForOffline,
                                onOpenRegionPicker = {
                                    pickerPurpose = PickerPurpose.OFFLINE_DOWNLOAD
                                },
                                onOpenOfflineRegion = viewModel::openOfflineRegion,
                                onUpdateRegion = { region ->
                                    viewModel.downloadRegionForOffline(
                                        name = region.name,
                                        lat = region.centerLat,
                                        lon = region.centerLon
                                    )
                                },
                                onDeleteRegion = viewModel::deleteOfflineRegion,
                                modifier = Modifier.widthIn(max = 760.dp),
                                topInset = topInset,
                                bottomInset = bottomInset
                            )
                        }

                        state.currentDestination == AppDestination.RIDE -> {
                            val openSettings = { showSettingsDialog = true }
                            val launchHcPermissions = {
                                healthConnectPermissionLauncher.launch(
                                    HealthConnectSyncManager.REQUIRED_PERMISSIONS
                                )
                            }
                            if (isExpanded && selectedRide != null) {
                                Row(modifier = Modifier.fillMaxSize()) {
                                    RideMainPane(
                                        state = state,
                                        viewModel = viewModel,
                                        onOpenSettings = openSettings,
                                        onRequestLocationPermission = requestLocationPermission,
                                        onConnectHealthConnect = launchHcPermissions,
                                        onShareRideGpx = shareRideGpx,
                                        onSelectGpxFile = launchSelectGpxFile,
                                        onStartGpxNavigation = startOrRequestGpxNav,
                                        topInset = topInset,
                                        bottomInset = bottomInset,
                                        paneModifier = Modifier.weight(1f)
                                    )
                                    VerticalDivider()
                                    RideDetailPane(
                                        ride = selectedRide,
                                        state = state,
                                        viewModel = viewModel,
                                        showBack = false,
                                        onOpenSettings = openSettings,
                                        onShareRideGpx = shareRideGpx,
                                        onSaveRideGpxFile = saveRideGpxFile,
                                        onRequestHealthConnect = launchHcPermissions,
                                        paneTopInset = topInset,
                                        bottomInset = bottomInset,
                                        paneModifier = Modifier.weight(1f)
                                    )
                                }
                            } else {
                                RideMainPane(
                                    state = state,
                                    viewModel = viewModel,
                                    onOpenSettings = openSettings,
                                    onRequestLocationPermission = requestLocationPermission,
                                    onConnectHealthConnect = launchHcPermissions,
                                    onShareRideGpx = shareRideGpx,
                                    onSelectGpxFile = launchSelectGpxFile,
                                    onStartGpxNavigation = startOrRequestGpxNav,
                                    topInset = topInset,
                                    bottomInset = bottomInset,
                                    paneModifier = Modifier.widthIn(max = 760.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (state.isRecordingScreenOpen) {
            LiveRideCockpitScreen(
                activeRide = state.activeRide,
                measurementUnit = state.measurementUnit,
                bleSensorState = state.bleSensorState,
                hasLocationPermission = state.hasLocationPermission,
                onClose = viewModel::closeRecordingScreen,
                onOpenSettings = { showSettingsDialog = true },
                onRequestLocationPermission = requestLocationPermission,
                onStartRide = { viewModel.startRide() },
                onPauseRide = viewModel::pauseRide,
                onResumeRide = viewModel::resumeRide,
                onFinishRide = viewModel::finishAndSaveRide,
                onDiscardRide = viewModel::discardRide,
                gpxNavigation = state.gpxNavigation,
                voiceCuesEnabled = state.voiceCuesEnabled,
                onToggleVoiceCues = viewModel::setVoiceCuesEnabled,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (state.isFullScreenMap && activeLocation != null) {
            val mapTrails = remember(state.filteredTrails, selectedTrail) {
                if (selectedTrail != null && state.filteredTrails.none { it.osmKey == selectedTrail.osmKey }) {
                    state.filteredTrails + selectedTrail
                } else {
                    state.filteredTrails
                }
            }
            BackHandler { viewModel.closeFullScreenMap() }
            TrailRadarMap(
                trails = mapTrails,
                origin = activeLocation,
                onOpenTrailDetail = { trail ->
                    viewModel.closeFullScreenMap()
                    viewModel.openTrailDetail(trail)
                },
                isFullScreen = true,
                onToggleFullScreen = viewModel::closeFullScreenMap,
                initialSelectedTrail = selectedTrail,
                measurementUnit = state.measurementUnit,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (state.isFullScreenRideMap && selectedRide != null) {
            BackHandler { viewModel.closeFullScreenRideMap() }
            RecordedRideTopoMap(
                ride = selectedRide,
                measurementUnit = state.measurementUnit,
                isFullScreen = true,
                onToggleFullScreen = viewModel::closeFullScreenRideMap,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (state.gpxNavigation.isFullScreenMap && gpxTrack != null) {
            BackHandler { viewModel.closeGpxFullScreenMap() }
            GpxNavigatorTopoMap(
                track = gpxTrack,
                navigationState = state.gpxNavigation,
                measurementUnit = state.measurementUnit,
                onToggleFollowLocation = viewModel::toggleGpxFollowLocation,
                onDisableFollowLocation = viewModel::disableGpxFollowLocation,
                isFullScreen = true,
                onToggleFullScreen = viewModel::closeGpxFullScreenMap,
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    if (pickerPurpose != PickerPurpose.NONE) {
        LocationPickerSheet(
            title = if (pickerPurpose == PickerPurpose.OFFLINE_DOWNLOAD) {
                "Download ${FormatUtils.formatRadiusShort(unit = state.measurementUnit)} Region"
            } else {
                "Choose Riding Area"
            },
            query = state.placeSearchQuery,
            results = state.placeSearchResults,
            isSearching = state.isSearchingPlaces,
            searchError = state.placeSearchError,
            onQueryChange = viewModel::updatePlaceSearchQuery,
            onSearch = viewModel::searchPlaces,
            onUseCurrentGps = {
                if (state.hasLocationPermission) {
                    viewModel.locateAndScanCurrentPosition()
                } else {
                    requestLocationPermission()
                }
            },
            onSelectPlace = { place ->
                if (pickerPurpose == PickerPurpose.OFFLINE_DOWNLOAD) {
                    viewModel.downloadRegionForOffline(place.name, place.lat, place.lon)
                } else {
                    viewModel.selectCustomLocation(place)
                }
            },
            onDismiss = { pickerPurpose = PickerPurpose.NONE },
            measurementUnit = state.measurementUnit
        )
    }

    if (showSettingsDialog) {
        UnitSettingsDialog(
            currentUnit = state.measurementUnit,
            onSelectUnit = viewModel::setMeasurementUnit,
            loadOfflineByDefault = state.loadOfflineMapsByDefault,
            offlineRegions = state.offlineRegions,
            defaultOfflineRegionId = state.defaultOfflineRegionId,
            onToggleLoadOfflineByDefault = viewModel::setLoadOfflineMapsByDefault,
            onSelectDefaultOfflineRegion = viewModel::setDefaultOfflineRegion,
            openRecordingScreenOnStart = state.openRecordingScreenOnStart,
            onToggleOpenRecordingScreenOnStart = viewModel::setOpenRecordingScreenOnStart,
            voiceCuesEnabled = state.voiceCuesEnabled,
            onToggleVoiceCues = viewModel::setVoiceCuesEnabled,
            gpxFullVoiceNavigationEnabled = state.gpxFullVoiceNavigationEnabled,
            onToggleGpxFullVoiceNavigation = viewModel::setGpxFullVoiceNavigationEnabled,
            bleSensorState = state.bleSensorState,
            onRequestBleScan = requestBleScan,
            onStopBleScan = viewModel::stopBleSensorScan,
            onPairBleSensor = viewModel::pairBleSensor,
            onConnectPairedBle = connectPairedBle,
            onForgetBleSensor = viewModel::forgetBleSensor,
            onSelectWheelCircumference = viewModel::setWheelCircumferenceMm,
            onRequestBleHrScan = requestBleHrScan,
            onStopBleHrScan = viewModel::stopBleHrScan,
            onPairBleHrSensor = viewModel::pairBleHrSensor,
            onConnectPairedBleHr = connectPairedBleHr,
            onForgetBleHrSensor = viewModel::forgetBleHrSensor,
            healthConnectStatus = state.healthConnectStatus,
            autoSyncEnabled = state.autoSyncToHealthConnect,
            onConnectHealthConnect = {
                healthConnectPermissionLauncher.launch(
                    HealthConnectSyncManager.REQUIRED_PERMISSIONS
                )
            },
            onInstallHealthConnect = {
                runCatching {
                    context.startActivity(
                        viewModel.healthConnectSyncManager.buildInstallHealthConnectIntent()
                    )
                }
            },
            onOpenHealthConnectSettings = {
                runCatching {
                    context.startActivity(
                        viewModel.healthConnectSyncManager.buildOpenHealthConnectSettingsIntent()
                    )
                }
            },
            onOpenGoogleFit = {
                viewModel.healthConnectSyncManager.buildOpenGoogleFitIntent()?.let {
                    context.startActivity(it)
                }
            },
            onToggleAutoSync = viewModel::setAutoSyncToHealthConnect,
            onDismiss = {
                viewModel.stopBleSensorScan()
                viewModel.stopBleHrScan()
                showSettingsDialog = false
            }
        )
    }
}

@Composable
private fun DestinationIcon(
    destination: AppDestination,
    selected: Boolean,
    offlineCount: Int,
    isRecording: Boolean
) {
    when (destination) {
        AppDestination.EXPLORE -> {
            Icon(
                imageVector = if (selected) Icons.Filled.Explore else Icons.Outlined.Explore,
                contentDescription = destination.label
            )
        }
        AppDestination.OFFLINE -> {
            BadgedBox(
                badge = {
                    if (offlineCount > 0) {
                        Badge { Text("$offlineCount") }
                    }
                }
            ) {
                Icon(
                    imageVector = if (selected) Icons.Filled.CloudOff else Icons.Outlined.CloudOff,
                    contentDescription = destination.label
                )
            }
        }
        AppDestination.RIDE -> {
            BadgedBox(
                badge = {
                    if (isRecording) {
                        Badge(containerColor = MaterialTheme.colorScheme.secondary)
                    }
                }
            ) {
                Icon(
                    imageVector = if (selected) {
                        Icons.AutoMirrored.Filled.DirectionsBike
                    } else {
                        Icons.AutoMirrored.Outlined.DirectionsBike
                    },
                    contentDescription = destination.label
                )
            }
        }
    }
}

@Composable
private fun RideMainPane(
    state: MainUiState,
    viewModel: TrailViewModel,
    onOpenSettings: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    onConnectHealthConnect: () -> Unit,
    onShareRideGpx: (RecordedRide) -> Unit,
    onSelectGpxFile: () -> Unit,
    onStartGpxNavigation: () -> Unit,
    topInset: Dp,
    bottomInset: Dp,
    paneModifier: Modifier = Modifier
) {
    RideScreen(
        activeRide = state.activeRide,
        recordedRides = state.recordedRides,
        measurementUnit = state.measurementUnit,
        bleSensorState = state.bleSensorState,
        healthConnectStatus = state.healthConnectStatus,
        syncingRideId = state.syncingRideId,
        hasLocationPermission = state.hasLocationPermission,
        onOpenSettings = onOpenSettings,
        onOpenRecordingScreen = viewModel::openRecordingScreen,
        onRequestLocationPermission = onRequestLocationPermission,
        onConnectHealthConnect = onConnectHealthConnect,
        onStartRide = { viewModel.startRide() },
        onPauseRide = viewModel::pauseRide,
        onResumeRide = viewModel::resumeRide,
        onFinishRide = viewModel::finishAndSaveRide,
        onDiscardRide = viewModel::discardRide,
        onOpenRideDetail = viewModel::openRideDetail,
        onShareRideGpx = onShareRideGpx,
        onSyncRide = viewModel::syncRideToHealthConnect,
        onDeleteRide = viewModel::deleteRide,
        gpxNavigation = state.gpxNavigation,
        onSelectGpxFile = onSelectGpxFile,
        onStartGpxNavigation = onStartGpxNavigation,
        onStopGpxNavigation = viewModel::stopGpxNavigation,
        onReverseGpxTrack = viewModel::reverseGpxTrack,
        onClearGpxTrack = viewModel::clearGpxTrack,
        onOpenGpxFullScreenMap = viewModel::openGpxFullScreenMap,
        onToggleGpxFollowLocation = viewModel::toggleGpxFollowLocation,
        onDisableGpxFollowLocation = viewModel::disableGpxFollowLocation,
        voiceCuesEnabled = state.voiceCuesEnabled,
        onToggleVoiceCues = viewModel::setVoiceCuesEnabled,
        modifier = paneModifier,
        topInset = topInset,
        bottomInset = bottomInset
    )
}

@Composable
private fun RideDetailPane(
    ride: RecordedRide,
    state: MainUiState,
    viewModel: TrailViewModel,
    showBack: Boolean,
    onOpenSettings: () -> Unit,
    onShareRideGpx: (RecordedRide) -> Unit,
    onSaveRideGpxFile: (RecordedRide) -> Unit,
    onRequestHealthConnect: () -> Unit,
    paneTopInset: Dp,
    bottomInset: Dp,
    paneModifier: Modifier = Modifier
) {
    RideDetailScreen(
        ride = ride,
        measurementUnit = state.measurementUnit,
        isSyncing = state.syncingRideId == ride.id,
        canSyncHealthConnect = state.healthConnectStatus.permissionsGranted,
        showBackNavigation = showBack,
        onBack = viewModel::closeRideDetail,
        onOpenSettings = onOpenSettings,
        onExpandFullScreenMap = viewModel::openFullScreenRideMap,
        onShareGpx = onShareRideGpx,
        onSaveGpxFile = onSaveRideGpxFile,
        onSyncHealthConnect = { target ->
            if (state.healthConnectStatus.permissionsGranted) {
                viewModel.syncRideToHealthConnect(target)
            } else {
                onRequestHealthConnect()
            }
        },
        onDeleteRide = viewModel::deleteRide,
        modifier = paneModifier,
        topInset = paneTopInset,
        bottomInset = bottomInset
    )
}
