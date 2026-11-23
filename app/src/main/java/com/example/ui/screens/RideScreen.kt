package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.health.HealthConnectAvailability
import com.example.data.health.HealthConnectStatus
import com.example.domain.model.ActiveRideMetrics
import com.example.domain.model.BleConnectionStatus
import com.example.domain.model.BleSensorUiState
import com.example.domain.model.MeasurementUnit
import com.example.domain.model.RecordedRide
import com.example.domain.model.RideRecordingStatus
import com.example.ui.components.FormatUtils
import com.example.ui.components.TrailShapeCanvas

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RideScreen(
    activeRide: ActiveRideMetrics,
    recordedRides: List<RecordedRide>,
    measurementUnit: MeasurementUnit,
    bleSensorState: BleSensorUiState,
    healthConnectStatus: HealthConnectStatus,
    autoSyncEnabled: Boolean,
    syncingRideId: String?,
    hasLocationPermission: Boolean,
    onOpenSettings: () -> Unit,
    onOpenRecordingScreen: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    onConnectHealthConnect: () -> Unit,
    onInstallHealthConnect: () -> Unit,
    onOpenHealthConnectSettings: () -> Unit,
    onOpenGoogleFit: () -> Unit,
    onToggleAutoSync: (Boolean) -> Unit,
    onStartRide: () -> Unit,
    onPauseRide: () -> Unit,
    onResumeRide: () -> Unit,
    onFinishRide: () -> Unit,
    onDiscardRide: () -> Unit,
    onOpenRideDetail: (RecordedRide) -> Unit,
    onShareRideGpx: (RecordedRide) -> Unit,
    onSyncRide: (RecordedRide) -> Unit,
    onDeleteRide: (String) -> Unit,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
    bottomInset: Dp = 0.dp
) {
    val context = LocalContext.current
    val hasGoogleFitInstalled = remember(context) {
        context.packageManager.getLaunchIntentForPackage("com.google.android.apps.fitness") != null
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("ride_screen_list"),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 20.dp + topInset,
            bottom = 20.dp + bottomInset
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "header") {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ride Tracker & Fit Sync",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("ride_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings and Bluetooth sensors"
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Record live GPS MTB rides, connect Bluetooth cycling sensors, view routes, and export GPX tracks.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Health Connect / Google Fit Integration Card
        item(key = "health_connect_card") {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (healthConnectStatus.permissionsGranted) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (healthConnectStatus.permissionsGranted) {
                                Icons.Filled.CheckCircle
                            } else {
                                Icons.Filled.Favorite
                            },
                            contentDescription = null,
                            tint = if (healthConnectStatus.permissionsGranted) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.secondary
                            }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when {
                                    healthConnectStatus.permissionsGranted ->
                                        "Health Connect Active (Google Fit Sync)"
                                    healthConnectStatus.availability == HealthConnectAvailability.UPDATE_REQUIRED ->
                                        "Health Connect Update Required"
                                    healthConnectStatus.availability == HealthConnectAvailability.UNAVAILABLE ->
                                        "Health Connect Unavailable"
                                    else -> "Sync Rides with Google Fit"
                                },
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = when {
                                    healthConnectStatus.permissionsGranted ->
                                        "Finished rides write biking sessions, GPS routes, distance, and speed to Health Connect for Google Fit."
                                    healthConnectStatus.availability == HealthConnectAvailability.UPDATE_REQUIRED ->
                                        "Install or update Health Connect from Google Play to sync rides with Google Fit."
                                    healthConnectStatus.availability == HealthConnectAvailability.UNAVAILABLE ->
                                        "Rides are saved locally on-device and can be exported to GPX anytime."
                                    else ->
                                        "Grant Health Connect permissions so Google Fit automatically displays your recorded MTB rides."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    when {
                        healthConnectStatus.permissionsGranted -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Auto-sync finished rides",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Switch(
                                    checked = autoSyncEnabled,
                                    onCheckedChange = onToggleAutoSync,
                                    modifier = Modifier.testTag("auto_sync_switch")
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onOpenHealthConnectSettings,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Health Connect")
                                }
                                if (hasGoogleFitInstalled) {
                                    FilledTonalButton(
                                        onClick = onOpenGoogleFit,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Open Google Fit")
                                    }
                                }
                            }
                        }

                        healthConnectStatus.availability == HealthConnectAvailability.AVAILABLE -> {
                            Button(
                                onClick = onConnectHealthConnect,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("connect_health_connect_button")
                            ) {
                                Icon(Icons.Filled.Sync, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Connect Health Connect for Google Fit")
                            }
                        }

                        healthConnectStatus.availability == HealthConnectAvailability.UPDATE_REQUIRED -> {
                            Button(
                                onClick = onInstallHealthConnect,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Install / Update Health Connect")
                            }
                        }
                    }
                }
            }
        }

        // Live GPS Ride Recorder Card
        item(key = "live_recorder_card") {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = activeRide.targetTrailName?.let { "Riding: $it" } ?: "Live GPS Ride",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = when (activeRide.status) {
                                RideRecordingStatus.IDLE -> "READY"
                                RideRecordingStatus.RECORDING -> "REC"
                                RideRecordingStatus.PAUSED -> "PAUSED"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = when (activeRide.status) {
                                RideRecordingStatus.RECORDING -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    // Bluetooth Cycling & Heart Rate Sensors Banner
                    val cscConnected = bleSensorState.connectionStatus == BleConnectionStatus.CONNECTED
                    val hrConnected = bleSensorState.hrConnectionStatus == BleConnectionStatus.CONNECTED
                    val anyConnected = cscConnected || hrConnected
                    val anyPaired = bleSensorState.pairedSensor != null || bleSensorState.pairedHrSensor != null
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (anyConnected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (anyConnected) {
                                        Icons.Filled.BluetoothConnected
                                    } else {
                                        Icons.Filled.Bluetooth
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = if (anyConnected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                val sensorParts = buildList {
                                    bleSensorState.pairedSensor?.let { csc ->
                                        val suffix = when (bleSensorState.connectionStatus) {
                                            BleConnectionStatus.CONNECTED -> "Connected"
                                            BleConnectionStatus.CONNECTING -> "Connecting…"
                                            BleConnectionStatus.DISCONNECTED -> "Disconnected"
                                            BleConnectionStatus.ERROR -> "Retry"
                                        }
                                        add("${csc.name} ($suffix)")
                                    }
                                    bleSensorState.pairedHrSensor?.let { hr ->
                                        val suffix = when (bleSensorState.hrConnectionStatus) {
                                            BleConnectionStatus.CONNECTED -> "Connected"
                                            BleConnectionStatus.CONNECTING -> "Connecting…"
                                            BleConnectionStatus.DISCONNECTED -> "Disconnected"
                                            BleConnectionStatus.ERROR -> "Retry"
                                        }
                                        add("HR: ${hr.name} ($suffix)")
                                    }
                                }
                                val sensorLabel = if (sensorParts.isEmpty()) {
                                    "No BT speed/cadence or HR sensor paired"
                                } else {
                                    sensorParts.joinToString(" • ")
                                }
                                Text(
                                    text = sensorLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(
                                onClick = onOpenSettings,
                                modifier = Modifier.testTag("ride_sensor_settings_button")
                            ) {
                                Text(if (anyPaired) "Manage" else "Pair Sensors")
                            }
                        }
                    }

                    // Live Route Breadcrumb Canvas
                    TrailShapeCanvas(
                        points = activeRide.points,
                        emptyText = if (activeRide.status == RideRecordingStatus.IDLE) {
                            "Press Start Ride to trace your GPS route"
                        } else {
                            "Waiting for GPS movement…"
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                    )

                    // Big elapsed clock
                    Text(
                        text = FormatUtils.formatDuration(activeRide.elapsedSeconds),
                        style = MaterialTheme.typography.displaySmall,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )

                    // Live telemetry grid
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RideMetricCell(
                            label = "Distance",
                            value = FormatUtils.formatDistance(
                                activeRide.distanceMiles,
                                measurementUnit,
                                decimals = 2
                            )
                        )
                        RideMetricCell(
                            label = if (bleSensorState.connectionStatus == BleConnectionStatus.CONNECTED &&
                                bleSensorState.hasWheelCapability
                            ) {
                                "Speed (BLE)"
                            } else {
                                "Speed"
                            },
                            value = FormatUtils.formatSpeed(
                                activeRide.currentSpeedMph,
                                measurementUnit
                            )
                        )
                        RideMetricCell(
                            label = "Elev Gain",
                            value = FormatUtils.formatElevation(
                                activeRide.elevationGainFeet,
                                measurementUnit
                            )
                        )
                        if (activeRide.currentCadenceRpm != null ||
                            bleSensorState.hasCadenceCapability ||
                            bleSensorState.pairedSensor != null
                        ) {
                            val cadenceVal = activeRide.currentCadenceRpm
                                ?: bleSensorState.liveCadenceRpm
                            RideMetricCell(
                                label = "Cadence",
                                value = cadenceVal?.let { "$it rpm" } ?: "--"
                            )
                        }
                        if (activeRide.currentHeartRateBpm != null ||
                            bleSensorState.pairedHrSensor != null ||
                            bleSensorState.liveHeartRateBpm != null
                        ) {
                            val hrVal = activeRide.currentHeartRateBpm
                                ?: bleSensorState.liveHeartRateBpm
                            RideMetricCell(
                                label = "Heart Rate",
                                value = hrVal?.let { "$it bpm" } ?: "--"
                            )
                        }
                    }

                    // Controls
                    when (activeRide.status) {
                        RideRecordingStatus.IDLE -> {
                            Button(
                                onClick = {
                                    if (hasLocationPermission) onStartRide() else onRequestLocationPermission()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("start_ride_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.DirectionsBike, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (hasLocationPermission) "Start Ride" else "Grant Location to Record"
                                )
                            }
                        }

                        RideRecordingStatus.RECORDING -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = onPauseRide,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("pause_ride_button")
                                ) {
                                    Icon(Icons.Filled.Pause, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Pause")
                                }
                                Button(
                                    onClick = onFinishRide,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondary
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("finish_ride_button")
                                ) {
                                    Icon(Icons.Filled.Stop, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Finish & Save")
                                }
                            }
                        }

                        RideRecordingStatus.PAUSED -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onResumeRide,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("resume_ride_button")
                                ) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Resume")
                                }
                                FilledTonalButton(
                                    onClick = onFinishRide,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("finish_ride_button")
                                ) {
                                    Text("Save")
                                }
                                OutlinedButton(
                                    onClick = onDiscardRide,
                                    modifier = Modifier.testTag("discard_ride_button")
                                ) {
                                    Text("Discard")
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = onOpenRecordingScreen,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_recording_screen_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.OpenInFull,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Full-Screen Recording View")
                    }
                }
            }
        }

        item(key = "history_header") {
            Text(
                text = "Recorded Rides (${recordedRides.size})",
                style = MaterialTheme.typography.titleLarge
            )
        }

        if (recordedRides.isEmpty()) {
            item(key = "empty_rides") {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No rides recorded yet. Hit Start Ride above—or tap Record Ride on any trail—to log distance, route, cadence, and elevation.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(items = recordedRides, key = { it.id }) { ride ->
                RecordedRideCard(
                    ride = ride,
                    measurementUnit = measurementUnit,
                    isSyncing = syncingRideId == ride.id,
                    canSyncHealthConnect = healthConnectStatus.permissionsGranted,
                    onOpenDetail = { onOpenRideDetail(ride) },
                    onShareGpx = { onShareRideGpx(ride) },
                    onSync = {
                        if (healthConnectStatus.permissionsGranted) {
                            onSyncRide(ride)
                        } else {
                            onConnectHealthConnect()
                        }
                    },
                    onDelete = { onDeleteRide(ride.id) }
                )
            }
        }
    }
}

@Composable
private fun RideMetricCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecordedRideCard(
    ride: RecordedRide,
    measurementUnit: MeasurementUnit,
    isSyncing: Boolean,
    canSyncHealthConnect: Boolean,
    onOpenDetail: () -> Unit,
    onShareGpx: () -> Unit,
    onSync: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDetail)
            .testTag("ride_card_${ride.id}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = ride.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = FormatUtils.formatTimestamp(ride.startTimeMillis),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onShareGpx,
                        modifier = Modifier.testTag("share_gpx_card_${ride.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "Share ride as GPX file"
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "Delete ride"
                        )
                    }
                }
            }

            val hasCscSensor = !ride.sensorName.isNullOrBlank()
            val hasHrSensor = !ride.hrSensorName.isNullOrBlank()
            if (hasCscSensor || hasHrSensor) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ride.sensorName?.takeIf { it.isNotBlank() }?.let { sensor ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Bluetooth,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = "Sensor: $sensor",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    ride.hrSensorName?.takeIf { it.isNotBlank() }?.let { hrSensor ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Favorite,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "HR: $hrSensor",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            if (ride.routePoints.size >= 2) {
                TrailShapeCanvas(
                    points = ride.routePoints,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RideMetricCell("Duration", FormatUtils.formatDuration(ride.durationSeconds))
                RideMetricCell(
                    "Distance",
                    FormatUtils.formatDistance(ride.distanceMiles, measurementUnit, decimals = 2)
                )
                RideMetricCell(
                    "Avg Speed",
                    FormatUtils.formatSpeed(ride.avgSpeedMph, measurementUnit)
                )
                RideMetricCell(
                    "Elev",
                    FormatUtils.formatElevation(ride.elevationGainFeet, measurementUnit)
                )
                if (ride.avgCadenceRpm != null) {
                    RideMetricCell("Cadence", "${ride.avgCadenceRpm} rpm")
                }
                if (ride.avgHeartRateBpm != null) {
                    RideMetricCell("Avg HR", "${ride.avgHeartRateBpm} bpm")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onOpenDetail,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("view_ride_route_${ride.id}")
                ) {
                    Icon(Icons.Filled.Map, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("View Route & GPX")
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }

            if (ride.syncedToHealthConnect) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Synced to Health Connect (Google Fit)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                FilledTonalButton(
                    onClick = onSync,
                    enabled = !isSyncing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sync_ride_${ride.id}")
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Filled.Sync, contentDescription = null)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (canSyncHealthConnect) {
                            "Sync to Google Fit"
                        } else {
                            "Connect Health Connect & Sync"
                        }
                    )
                }
            }

        }
    }
}
