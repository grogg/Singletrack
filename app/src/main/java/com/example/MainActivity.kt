package com.example

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.health.HealthConnectSyncManager
import com.example.domain.model.RideRecordingStatus
import com.example.ui.components.FormatUtils
import com.example.ui.components.LocationPickerSheet
import com.example.ui.components.TrailRadarMap
import com.example.ui.components.UnitSettingsDialog
import com.example.ui.screens.ExploreScreen
import com.example.ui.screens.OfflineRegionsScreen
import com.example.ui.screens.RideScreen
import com.example.ui.screens.TrailDetailScreen
import com.example.ui.theme.SingletrackTheme
import com.example.ui.viewmodel.AppDestination
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

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.onLocationPermissionResult(granted)
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

    val requestLocationPermission: () -> Unit = {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTabletOrWide = maxWidth >= 600.dp
        val isExpanded = maxWidth >= 840.dp
        val showCompactDetail = !isExpanded &&
            state.currentDestination == AppDestination.EXPLORE &&
            state.selectedTrail != null

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!isTabletOrWide && !showCompactDetail) {
                    NavigationBar {
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
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isTabletOrWide) {
                    NavigationRail {
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
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    when {
                        showCompactDetail && state.selectedTrail != null -> {
                            TrailDetailScreen(
                                trail = state.selectedTrail!!,
                                isLoadingGeometry = state.isLoadingTrailGeometry,
                                isLoadingElevation = state.isLoadingElevation,
                                measurementUnit = state.measurementUnit,
                                showBackNavigation = true,
                                onBack = viewModel::closeTrailDetail,
                                onOpenSettings = { showSettingsDialog = true },
                                onRecordRideOnTrail = viewModel::startRideForTrail,
                                onExpandOnTopoMap = { trail -> viewModel.openFullScreenMap(focusTrail = trail) }
                            )
                        }

                        state.currentDestination == AppDestination.EXPLORE -> {
                            if (isExpanded && state.selectedTrail != null) {
                                Row(modifier = Modifier.fillMaxSize()) {
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
                                        modifier = Modifier.weight(1f)
                                    )
                                    VerticalDivider()
                                    TrailDetailScreen(
                                        trail = state.selectedTrail!!,
                                        isLoadingGeometry = state.isLoadingTrailGeometry,
                                        isLoadingElevation = state.isLoadingElevation,
                                        measurementUnit = state.measurementUnit,
                                        showBackNavigation = false,
                                        onBack = viewModel::closeTrailDetail,
                                        onOpenSettings = { showSettingsDialog = true },
                                        onRecordRideOnTrail = viewModel::startRideForTrail,
                                        onExpandOnTopoMap = { trail -> viewModel.openFullScreenMap(focusTrail = trail) },
                                        modifier = Modifier.weight(1f)
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
                                    modifier = Modifier.widthIn(max = 760.dp)
                                )
                            }
                        }

                        state.currentDestination == AppDestination.OFFLINE -> {
                            OfflineRegionsScreen(
                                regions = state.offlineRegions,
                                activeLocation = state.activeLocation,
                                measurementUnit = state.measurementUnit,
                                isDownloadingRegion = state.isDownloadingRegion,
                                downloadingRegionName = state.downloadingRegionName,
                                onOpenSettings = { showSettingsDialog = true },
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
                                modifier = Modifier.widthIn(max = 760.dp)
                            )
                        }

                        state.currentDestination == AppDestination.RIDE -> {
                            RideScreen(
                                activeRide = state.activeRide,
                                recordedRides = state.recordedRides,
                                measurementUnit = state.measurementUnit,
                                healthConnectStatus = state.healthConnectStatus,
                                autoSyncEnabled = state.autoSyncToHealthConnect,
                                syncingRideId = state.syncingRideId,
                                hasLocationPermission = state.hasLocationPermission,
                                onOpenSettings = { showSettingsDialog = true },
                                onRequestLocationPermission = requestLocationPermission,
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
                                onStartRide = { viewModel.startRide() },
                                onPauseRide = viewModel::pauseRide,
                                onResumeRide = viewModel::resumeRide,
                                onFinishRide = viewModel::finishAndSaveRide,
                                onDiscardRide = viewModel::discardRide,
                                onSyncRide = viewModel::syncRideToHealthConnect,
                                onDeleteRide = viewModel::deleteRide,
                                modifier = Modifier.widthIn(max = 760.dp)
                            )
                        }
                    }
                }
            }
        }

        if (state.isFullScreenMap && state.activeLocation != null) {
            val mapTrails = remember(state.filteredTrails, state.selectedTrail) {
                val sel = state.selectedTrail
                if (sel != null && state.filteredTrails.none { it.osmKey == sel.osmKey }) {
                    state.filteredTrails + sel
                } else {
                    state.filteredTrails
                }
            }
            BackHandler { viewModel.closeFullScreenMap() }
            TrailRadarMap(
                trails = mapTrails,
                origin = state.activeLocation!!,
                onOpenTrailDetail = { trail ->
                    viewModel.closeFullScreenMap()
                    viewModel.openTrailDetail(trail)
                },
                isFullScreen = true,
                onToggleFullScreen = viewModel::closeFullScreenMap,
                initialSelectedTrail = state.selectedTrail,
                measurementUnit = state.measurementUnit,
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
            onDismiss = { showSettingsDialog = false }
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
