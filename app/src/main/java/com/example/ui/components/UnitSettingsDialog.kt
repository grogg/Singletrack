package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.health.HealthConnectAvailability
import com.example.data.health.HealthConnectStatus
import com.example.domain.model.BleSensorUiState
import com.example.domain.model.DiscoveredBleSensor
import com.example.domain.model.MeasurementUnit
import com.example.domain.model.OfflineRegion

@Composable
fun UnitSettingsDialog(
    currentUnit: MeasurementUnit,
    onSelectUnit: (MeasurementUnit) -> Unit,
    loadOfflineByDefault: Boolean = false,
    offlineRegions: List<OfflineRegion> = emptyList(),
    defaultOfflineRegionId: String? = null,
    onToggleLoadOfflineByDefault: (Boolean) -> Unit = {},
    onSelectDefaultOfflineRegion: (OfflineRegion) -> Unit = {},
    openRecordingScreenOnStart: Boolean = false,
    onToggleOpenRecordingScreenOnStart: (Boolean) -> Unit = {},
    voiceCuesEnabled: Boolean = true,
    onToggleVoiceCues: (Boolean) -> Unit = {},
    gpxFullVoiceNavigationEnabled: Boolean = false,
    onToggleGpxFullVoiceNavigation: (Boolean) -> Unit = {},
    bleSensorState: BleSensorUiState = BleSensorUiState(),
    onRequestBleScan: () -> Unit = {},
    onStopBleScan: () -> Unit = {},
    onPairBleSensor: (DiscoveredBleSensor) -> Unit = {},
    onConnectPairedBle: () -> Unit = {},
    onForgetBleSensor: () -> Unit = {},
    onSelectWheelCircumference: (Int) -> Unit = {},
    onRequestBleHrScan: () -> Unit = {},
    onStopBleHrScan: () -> Unit = {},
    onPairBleHrSensor: (DiscoveredBleSensor) -> Unit = {},
    onConnectPairedBleHr: () -> Unit = {},
    onForgetBleHrSensor: () -> Unit = {},
    healthConnectStatus: HealthConnectStatus = HealthConnectStatus(
        availability = HealthConnectAvailability.UNAVAILABLE,
        permissionsGranted = false
    ),
    autoSyncEnabled: Boolean = false,
    onConnectHealthConnect: () -> Unit = {},
    onInstallHealthConnect: () -> Unit = {},
    onOpenHealthConnectSettings: () -> Unit = {},
    onOpenGoogleFit: () -> Unit = {},
    onToggleAutoSync: (Boolean) -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val hasGoogleFitInstalled = remember(context) {
        context.packageManager.getLaunchIntentForPackage("com.google.android.apps.fitness") != null
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .safeDrawingPadding()
                .testTag("unit_settings_dialog")
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Settings & Sensors",
                        style = MaterialTheme.typography.headlineSmall
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(weight = 1f, fill = false)
                        .heightIn(max = 600.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Measurement Units
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Measurement Units",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Choose how trail distances, ride speeds, and elevation profiles are displayed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        MeasurementUnit.entries.forEach { unit ->
                            val selected = unit == currentUnit
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { onSelectUnit(unit) }
                                    .testTag("unit_option_${unit.name.lowercase()}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    RadioButton(
                                        selected = selected,
                                        onClick = { onSelectUnit(unit) },
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = unit.label,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = unit.summary,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    // Offline Maps Startup Preference
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CloudOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Offline Maps on Startup",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        val activeDefaultRegion = offlineRegions.firstOrNull {
                            it.id == defaultOfflineRegionId
                        } ?: offlineRegions.firstOrNull()

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onToggleLoadOfflineByDefault(!loadOfflineByDefault) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Load downloaded offline maps by default",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = when {
                                            loadOfflineByDefault && activeDefaultRegion != null ->
                                                "Opens ${activeDefaultRegion.name} automatically when the app starts."
                                            loadOfflineByDefault ->
                                                "Download an offline region to load it automatically on launch."
                                            else ->
                                                "Uses online GPS trail scan when the app opens."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = loadOfflineByDefault,
                                    onCheckedChange = onToggleLoadOfflineByDefault,
                                    modifier = Modifier.testTag("settings_offline_default_switch")
                                )
                            }
                        }

                        if (loadOfflineByDefault && offlineRegions.size > 1) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Default Startup Region",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    offlineRegions.forEach { region ->
                                        val isDefault = region.id == activeDefaultRegion?.id
                                        FilterChip(
                                            selected = isDefault,
                                            onClick = { onSelectDefaultOfflineRegion(region) },
                                            label = { Text(region.name) },
                                            modifier = Modifier.testTag("settings_default_region_${region.id}")
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    // Full-Screen Ride Recording Display
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.OpenInFull,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Ride Recording Screen",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onToggleOpenRecordingScreenOnStart(!openRecordingScreenOnStart) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Open recording screen on ride start",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Automatically displays full-screen elapsed time, distance, speed, heart rate, cadence, and elevation when a ride begins.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = openRecordingScreenOnStart,
                                    onCheckedChange = onToggleOpenRecordingScreenOnStart,
                                    modifier = Modifier.testTag("settings_cockpit_auto_open_switch")
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    // Spoken Audio Cues
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.testTag("settings_spoken_audio_cues_section")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Spoken Audio Cues",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onToggleVoiceCues(!voiceCuesEnabled) }
                                .testTag("settings_voice_cues_card")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Voice alerts during rides & GPX nav",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Speaks GPX off-route alerts and each completed mile or kilometer during live GPS rides.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = voiceCuesEnabled,
                                    onCheckedChange = onToggleVoiceCues,
                                    modifier = Modifier.testTag("settings_voice_cues_switch")
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onToggleGpxFullVoiceNavigation(!gpxFullVoiceNavigationEnabled) }
                                .testTag("settings_gpx_full_voice_nav_card")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Full voice navigation (GPX)",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Speaks turn-by-turn directions, switchback warnings, back-on-track cues, and route completion during GPX track navigation.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = gpxFullVoiceNavigationEnabled,
                                    onCheckedChange = onToggleGpxFullVoiceNavigation,
                                    modifier = Modifier.testTag("settings_gpx_full_voice_nav_switch")
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    BluetoothSpeedCadenceSection(
                        currentUnit = currentUnit,
                        bleSensorState = bleSensorState,
                        onRequestBleScan = onRequestBleScan,
                        onStopBleScan = onStopBleScan,
                        onPairBleSensor = onPairBleSensor,
                        onConnectPairedBle = onConnectPairedBle,
                        onForgetBleSensor = onForgetBleSensor,
                        onSelectWheelCircumference = onSelectWheelCircumference
                    )

                    HorizontalDivider()

                    BluetoothHeartRateSection(
                        bleSensorState = bleSensorState,
                        onRequestBleHrScan = onRequestBleHrScan,
                        onStopBleHrScan = onStopBleHrScan,
                        onPairBleHrSensor = onPairBleHrSensor,
                        onConnectPairedBleHr = onConnectPairedBleHr,
                        onForgetBleHrSensor = onForgetBleHrSensor
                    )

                    HorizontalDivider()

                    GoogleFitSyncSection(
                        healthConnectStatus = healthConnectStatus,
                        autoSyncEnabled = autoSyncEnabled,
                        hasGoogleFitInstalled = hasGoogleFitInstalled,
                        onConnectHealthConnect = onConnectHealthConnect,
                        onInstallHealthConnect = onInstallHealthConnect,
                        onOpenHealthConnectSettings = onOpenHealthConnectSettings,
                        onOpenGoogleFit = onOpenGoogleFit,
                        onToggleAutoSync = onToggleAutoSync
                    )

                    HorizontalDivider()

                    SettingsAboutSection()
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("unit_settings_done_button")
                    ) {
                        Text("Done")
                    }
                }
            }
        }
    }
}
