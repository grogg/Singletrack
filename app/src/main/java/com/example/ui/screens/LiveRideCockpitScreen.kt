package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.ActiveRideMetrics
import com.example.domain.model.BleConnectionStatus
import com.example.domain.model.BleSensorUiState
import com.example.domain.model.MeasurementUnit
import com.example.domain.model.RideRecordingStatus
import com.example.ui.components.FormatUtils
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PineDeep
import com.example.ui.theme.PineForest
import com.example.ui.theme.Terracotta
import com.example.ui.theme.TerracottaBright

@Composable
fun LiveRideCockpitScreen(
    activeRide: ActiveRideMetrics,
    measurementUnit: MeasurementUnit,
    bleSensorState: BleSensorUiState,
    hasLocationPermission: Boolean,
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    onStartRide: () -> Unit,
    onPauseRide: () -> Unit,
    onResumeRide: () -> Unit,
    onFinishRide: () -> Unit,
    onDiscardRide: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onClose)

    val liveHr = activeRide.currentHeartRateBpm ?: bleSensorState.liveHeartRateBpm
    val liveCadence = activeRide.currentCadenceRpm ?: bleSensorState.liveCadenceRpm
    val isBleSpeedActive = bleSensorState.connectionStatus == BleConnectionStatus.CONNECTED &&
        bleSensorState.hasWheelCapability

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("live_ride_cockpit_screen"),
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                shadowElevation = 8.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                        )
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 760.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        when (activeRide.status) {
                            RideRecordingStatus.IDLE -> {
                                Button(
                                    onClick = {
                                        if (hasLocationPermission) onStartRide() else onRequestLocationPermission()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(54.dp)
                                        .testTag("cockpit_start_ride_button")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.DirectionsBike, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (hasLocationPermission) "Start Ride" else "Grant Location to Record",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }

                            RideRecordingStatus.RECORDING -> {
                                FilledTonalButton(
                                    onClick = onPauseRide,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(54.dp)
                                        .testTag("cockpit_pause_ride_button")
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
                                        .height(54.dp)
                                        .testTag("cockpit_finish_ride_button")
                                ) {
                                    Icon(Icons.Filled.Stop, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Finish & Save")
                                }
                            }

                            RideRecordingStatus.PAUSED -> {
                                Button(
                                    onClick = onResumeRide,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(54.dp)
                                        .testTag("cockpit_resume_ride_button")
                                ) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Resume")
                                }
                                FilledTonalButton(
                                    onClick = onFinishRide,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(54.dp)
                                        .testTag("cockpit_finish_ride_button")
                                ) {
                                    Text("Save")
                                }
                                OutlinedButton(
                                    onClick = onDiscardRide,
                                    modifier = Modifier
                                        .height(54.dp)
                                        .testTag("cockpit_discard_ride_button")
                                ) {
                                    Text("Discard")
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Atmospheric Bike-Computer Hero Banner with Elapsed Time
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(PineDeep, PineForest)
                        )
                    )
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 760.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.testTag("cockpit_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Ride tab",
                                tint = Color.White
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = activeRide.targetTrailName ?: "Ride Computer",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = when (activeRide.status) {
                                    RideRecordingStatus.RECORDING -> Terracotta
                                    RideRecordingStatus.PAUSED -> Color.White.copy(alpha = 0.22f)
                                    RideRecordingStatus.IDLE -> Color.White.copy(alpha = 0.16f)
                                }
                            ) {
                                Text(
                                    text = when (activeRide.status) {
                                        RideRecordingStatus.IDLE -> "READY"
                                        RideRecordingStatus.RECORDING -> "RECORDING"
                                        RideRecordingStatus.PAUSED -> "PAUSED"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.testTag("cockpit_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Settings and Bluetooth sensors",
                                tint = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "ELAPSED TIME",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.72f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = FormatUtils.formatDuration(activeRide.elapsedSeconds),
                        style = TextStyle(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 48.sp,
                            lineHeight = 54.sp,
                            fontFeatureSettings = "tnum"
                        ),
                        color = Color.White,
                        modifier = Modifier.testTag("cockpit_elapsed_time")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Primary Telemetry Cards Grid
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Row 1: Speed & Distance Traveled
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CockpitMetricCard(
                        icon = Icons.Filled.Speed,
                        iconTint = MaterialTheme.colorScheme.primary,
                        label = if (isBleSpeedActive) "Speed (BLE)" else "Speed",
                        value = FormatUtils.formatSpeed(activeRide.currentSpeedMph, measurementUnit),
                        subtitle = "Max: ${FormatUtils.formatSpeed(activeRide.maxSpeedMph, measurementUnit)}",
                        modifier = Modifier
                            .weight(1f)
                            .testTag("cockpit_speed_card")
                    )

                    CockpitMetricCard(
                        icon = Icons.Filled.Route,
                        iconTint = MaterialTheme.colorScheme.primary,
                        label = "Distance Traveled",
                        value = FormatUtils.formatDistance(
                            activeRide.distanceMiles,
                            measurementUnit,
                            decimals = 2
                        ),
                        subtitle = "${activeRide.points.size} GPS pts",
                        modifier = Modifier
                            .weight(1f)
                            .testTag("cockpit_distance_card")
                    )
                }

                // Row 2: Heart Rate (HR) & Cadence
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val hrSubtitle = when {
                        activeRide.avgHeartRateBpm != null || activeRide.maxHeartRateBpm != null ->
                            "Avg ${activeRide.avgHeartRateBpm ?: "--"} • Max ${activeRide.maxHeartRateBpm ?: "--"}"
                        bleSensorState.hrConnectionStatus == BleConnectionStatus.CONNECTED ->
                            bleSensorState.pairedHrSensor?.name ?: "HR connected"
                        bleSensorState.pairedHrSensor != null ->
                            "HR disconnected • Tap to manage"
                        else -> "No HR paired • Tap to pair"
                    }
                    CockpitMetricCard(
                        icon = Icons.Filled.Favorite,
                        iconTint = TerracottaBright,
                        label = "Heart Rate",
                        value = liveHr?.let { "$it bpm" } ?: "-- bpm",
                        subtitle = hrSubtitle,
                        onClick = if (bleSensorState.pairedHrSensor == null ||
                            bleSensorState.hrConnectionStatus != BleConnectionStatus.CONNECTED
                        ) {
                            onOpenSettings
                        } else {
                            null
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("cockpit_hr_card")
                    )

                    val cadenceSubtitle = when {
                        activeRide.avgCadenceRpm != null || activeRide.maxCadenceRpm != null ->
                            "Avg ${activeRide.avgCadenceRpm ?: "--"} • Max ${activeRide.maxCadenceRpm ?: "--"}"
                        bleSensorState.connectionStatus == BleConnectionStatus.CONNECTED ->
                            bleSensorState.pairedSensor?.name ?: "CSC connected"
                        bleSensorState.pairedSensor != null ->
                            "Sensor disconnected • Tap"
                        else -> "No CSC paired • Tap to pair"
                    }
                    CockpitMetricCard(
                        icon = Icons.Filled.Autorenew,
                        iconTint = MaterialTheme.colorScheme.primary,
                        label = "Cadence",
                        value = liveCadence?.let { "$it rpm" } ?: "-- rpm",
                        subtitle = cadenceSubtitle,
                        onClick = if (bleSensorState.pairedSensor == null ||
                            bleSensorState.connectionStatus != BleConnectionStatus.CONNECTED
                        ) {
                            onOpenSettings
                        } else {
                            null
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("cockpit_cadence_card")
                    )
                }

                // Row 3: Elevation (Gain + Current Altitude)
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cockpit_elevation_card")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Terrain,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Elevation",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text(
                                    text = "Elevation Gain",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = FormatUtils.formatElevation(
                                        activeRide.elevationGainFeet,
                                        measurementUnit,
                                        prefix = "+"
                                    ),
                                    style = TextStyle(
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 26.sp,
                                        fontFeatureSettings = "tnum"
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            VerticalDivider(
                                modifier = Modifier
                                    .height(48.dp)
                                    .padding(horizontal = 12.dp)
                            )

                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text(
                                    text = "Current Altitude",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = activeRide.currentAltitudeFeet?.let {
                                        FormatUtils.formatElevation(it, measurementUnit)
                                    } ?: "--",
                                    style = TextStyle(
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 26.sp,
                                        fontFeatureSettings = "tnum"
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CockpitMetricCard(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val cardModifier = if (onClick != null) {
        modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    } else {
        modifier
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(18.dp),
        modifier = cardModifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = value,
                style = TextStyle(
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                    lineHeight = 30.sp,
                    fontFeatureSettings = "tnum"
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
