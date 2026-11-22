package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.domain.model.BleConnectionStatus
import com.example.domain.model.BleSensorUiState
import com.example.domain.model.DiscoveredBleSensor
import com.example.domain.model.MeasurementUnit
import com.example.domain.model.OfflineRegion
import com.example.domain.model.WheelCircumferencePreset

@Composable
fun UnitSettingsDialog(
    currentUnit: MeasurementUnit,
    onSelectUnit: (MeasurementUnit) -> Unit,
    loadOfflineByDefault: Boolean = false,
    offlineRegions: List<OfflineRegion> = emptyList(),
    defaultOfflineRegionId: String? = null,
    onToggleLoadOfflineByDefault: (Boolean) -> Unit = {},
    onSelectDefaultOfflineRegion: (OfflineRegion) -> Unit = {},
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
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
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
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Section 1: Measurement Units
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

                    // Section 1B: Offline Maps Startup Preference
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

                    // Section 2: Bluetooth Cycling Speed & Cadence Sensors
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (bleSensorState.connectionStatus == BleConnectionStatus.CONNECTED) {
                                    Icons.Filled.BluetoothConnected
                                } else {
                                    Icons.Filled.Bluetooth
                                },
                                contentDescription = null,
                                tint = if (bleSensorState.connectionStatus == BleConnectionStatus.CONNECTED) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.secondary
                                },
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Bluetooth Speed & Cadence",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        Text(
                            text = "Pair a Bluetooth LE Cycling Speed & Cadence (CSC) hub/crank sensor. Live wheel speed, distance, and cadence override GPS estimates during recordings when active.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        bleSensorState.pairedSensor?.let { paired ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("ble_paired_sensor_card")
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = paired.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = paired.address,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        val statusLabel = when (bleSensorState.connectionStatus) {
                                            BleConnectionStatus.CONNECTED -> "Connected"
                                            BleConnectionStatus.CONNECTING -> "Connecting…"
                                            BleConnectionStatus.DISCONNECTED -> "Disconnected"
                                            BleConnectionStatus.ERROR -> "Error"
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = if (bleSensorState.connectionStatus == BleConnectionStatus.CONNECTED) {
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant
                                            }
                                        ) {
                                            Text(
                                                text = statusLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (bleSensorState.connectionStatus == BleConnectionStatus.CONNECTED) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    if (bleSensorState.connectionStatus == BleConnectionStatus.CONNECTED) {
                                        val speedPreview = bleSensorState.liveSpeedMph?.let {
                                            FormatUtils.formatSpeed(it, currentUnit)
                                        } ?: "--"
                                        val cadencePreview = bleSensorState.liveCadenceRpm?.let {
                                            "$it rpm"
                                        } ?: "-- rpm"
                                        Text(
                                            text = "Live sensor: Speed $speedPreview • Cadence $cadencePreview",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (bleSensorState.connectionStatus != BleConnectionStatus.CONNECTED &&
                                            bleSensorState.connectionStatus != BleConnectionStatus.CONNECTING
                                        ) {
                                            Button(
                                                onClick = onConnectPairedBle,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("ble_connect_button")
                                            ) {
                                                Text("Connect")
                                            }
                                        }
                                        OutlinedButton(
                                            onClick = onForgetBleSensor,
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("ble_forget_button")
                                        ) {
                                            Text("Forget")
                                        }
                                    }
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Wheel Size (for speed sensor)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    WheelCircumferencePreset.entries.forEach { preset ->
                                        val isSelected = paired.wheelCircumferenceMm == preset.circumferenceMm
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { onSelectWheelCircumference(preset.circumferenceMm) },
                                            label = { Text(preset.shortLabel) },
                                            modifier = Modifier.testTag("wheel_preset_${preset.circumferenceMm}")
                                        )
                                    }
                                }
                            }
                        }

                        when {
                            !bleSensorState.isBluetoothSupported -> {
                                Text(
                                    text = "Bluetooth Low Energy hardware is unavailable on this device.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            !bleSensorState.isBluetoothEnabled -> {
                                Text(
                                    text = "Bluetooth is turned off. Enable Bluetooth in Android settings to scan for sensors.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }

                        if (bleSensorState.isBluetoothSupported) {
                            if (bleSensorState.isScanning) {
                                OutlinedButton(
                                    onClick = onStopBleScan,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("ble_scan_button")
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Stop Scanning")
                                }
                            } else {
                                Button(
                                    onClick = onRequestBleScan,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("ble_scan_button")
                                ) {
                                    Icon(Icons.Filled.BluetoothSearching, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        if (bleSensorState.pairedSensor != null) {
                                            "Scan for Different Sensor"
                                        } else {
                                            "Scan for Speed / Cadence Sensors"
                                        }
                                    )
                                }
                            }
                        }

                        bleSensorState.statusMessage?.takeIf { it.isNotBlank() }?.let { msg ->
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (bleSensorState.discoveredSensors.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Discovered Sensors (${bleSensorState.discoveredSensors.size})",
                                    style = MaterialTheme.typography.labelLarge
                                )
                                bleSensorState.discoveredSensors.forEach { sensor ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = sensor.name,
                                                    style = MaterialTheme.typography.titleSmall
                                                )
                                                Text(
                                                    text = "${sensor.address} • ${sensor.rssi} dBm",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Button(
                                                onClick = { onPairBleSensor(sensor) },
                                                modifier = Modifier.testTag("ble_pair_${sensor.address}")
                                            ) {
                                                Text("Pair")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    // Section 3: Bluetooth Heart Rate Monitor (0x180D)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Favorite,
                                contentDescription = null,
                                tint = if (bleSensorState.hrConnectionStatus == BleConnectionStatus.CONNECTED) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.secondary
                                },
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Bluetooth Heart Rate Monitor",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        Text(
                            text = "Pair a Bluetooth LE heart rate chest strap or armband (0x180D) to record live BPM, average/max heart rate, and per-sample GPX & Health Connect telemetry.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        bleSensorState.pairedHrSensor?.let { pairedHr ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("ble_paired_hr_card")
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = pairedHr.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = pairedHr.address,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        val hrStatusLabel = when (bleSensorState.hrConnectionStatus) {
                                            BleConnectionStatus.CONNECTED -> "Connected"
                                            BleConnectionStatus.CONNECTING -> "Connecting…"
                                            BleConnectionStatus.DISCONNECTED -> "Disconnected"
                                            BleConnectionStatus.ERROR -> "Error"
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = if (bleSensorState.hrConnectionStatus == BleConnectionStatus.CONNECTED) {
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant
                                            }
                                        ) {
                                            Text(
                                                text = hrStatusLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (bleSensorState.hrConnectionStatus == BleConnectionStatus.CONNECTED) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    if (bleSensorState.hrConnectionStatus == BleConnectionStatus.CONNECTED) {
                                        val hrPreview = bleSensorState.liveHeartRateBpm?.let { "$it bpm" } ?: "-- bpm"
                                        Text(
                                            text = "Live Heart Rate: $hrPreview",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (bleSensorState.hrConnectionStatus != BleConnectionStatus.CONNECTED &&
                                            bleSensorState.hrConnectionStatus != BleConnectionStatus.CONNECTING
                                        ) {
                                            Button(
                                                onClick = onConnectPairedBleHr,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("ble_hr_connect_button")
                                            ) {
                                                Text("Connect")
                                            }
                                        }
                                        OutlinedButton(
                                            onClick = onForgetBleHrSensor,
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("ble_hr_forget_button")
                                        ) {
                                            Text("Forget")
                                        }
                                    }
                                }
                            }
                        }

                        if (bleSensorState.isBluetoothSupported) {
                            if (bleSensorState.isScanningHr) {
                                OutlinedButton(
                                    onClick = onStopBleHrScan,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("ble_hr_scan_button")
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Stop Scanning")
                                }
                            } else {
                                Button(
                                    onClick = onRequestBleHrScan,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("ble_hr_scan_button")
                                ) {
                                    Icon(Icons.Filled.Favorite, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        if (bleSensorState.pairedHrSensor != null) {
                                            "Scan for Different HR Monitor"
                                        } else {
                                            "Scan for Heart Rate Sensors"
                                        }
                                    )
                                }
                            }
                        }

                        bleSensorState.hrStatusMessage?.takeIf { it.isNotBlank() }?.let { msg ->
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (bleSensorState.discoveredHrSensors.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Discovered HR Sensors (${bleSensorState.discoveredHrSensors.size})",
                                    style = MaterialTheme.typography.labelLarge
                                )
                                bleSensorState.discoveredHrSensors.forEach { sensor ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = sensor.name,
                                                    style = MaterialTheme.typography.titleSmall
                                                )
                                                Text(
                                                    text = "${sensor.address} • ${sensor.rssi} dBm",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Button(
                                                onClick = { onPairBleHrSensor(sensor) },
                                                modifier = Modifier.testTag("ble_hr_pair_${sensor.address}")
                                            ) {
                                                Text("Pair")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    // Section 4: About & Data Privacy
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.testTag("settings_about_section")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "About",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Singletrack MTB does not have a developer backend, user accounts, advertising SDKs, crash-reporting SDKs, or analytics/tracking libraries, and it does not collect or sell personal data.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(12.dp)
                            )
                        }

                        Text(
                            text = "Here is a complete breakdown of where data is stored and when any data leaves the app:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        AboutGroupCard(
                            title = "1. Stored Locally on Your Device",
                            items = listOf(
                                labeledLine(
                                    "Ride Recordings & Biometrics: ",
                                    "GPS tracks, distance, speed, elevation gain, cycling cadence, and heart rate (BPM) data are stored locally in the on-device database."
                                ),
                                labeledLine(
                                    "Bluetooth Sensors & Settings: ",
                                    "Paired speed/cadence and heart rate sensor names/addresses and unit preferences (imperial/metric) are saved locally in app preferences."
                                ),
                                labeledLine(
                                    "Offline Trail Regions: ",
                                    "Downloaded trail lists and geometry are cached locally on your device."
                                )
                            )
                        )

                        AboutGroupCard(
                            title = "2. Network Requests to Public Map & Trail Services (Read-Only)",
                            intro = "When online features are used, the app sends only the coordinates, map tile indices, or search text needed to fetch trail and map data:",
                            items = listOf(
                                labeledLine(
                                    "Trail Discovery (OpenStreetMap Overpass API): ",
                                    "Sends the active search center coordinates (current GPS or selected city/region) to find nearby MTB trails."
                                ),
                                labeledLine(
                                    "Location Search & Area Labels (OpenStreetMap Nominatim): ",
                                    "Sends your typed place query or coordinates to resolve city/trailhead names."
                                ),
                                labeledLine(
                                    "Trail Elevation Profiles (Open-Meteo & OpenTopoData): ",
                                    "Sends sampled coordinates of a selected trail to retrieve elevation points."
                                ),
                                labeledLine(
                                    "Topographic Map Tiles (OpenTopoMap & ArcGIS World Topo): ",
                                    "Requests standard map tile images (z/x/y) for the viewed map area."
                                )
                            ),
                            footnote = "(As with any standard HTTPS request, these public services receive your device IP address and the requested coordinates/tile region when queried.)"
                        )

                        AboutGroupCard(
                            title = "3. Optional User-Initiated Sharing & Device Sync",
                            items = listOf(
                                labeledLine(
                                    "Health Connect / Google Fit: ",
                                    "If you explicitly grant Health Connect permissions and sync a ride (or keep Auto-sync enabled), ride sessions, routes, distance, speed, and heart rate are written to Android Health Connect on your device so apps you connect (such as Google Fit) can read them."
                                ),
                                labeledLine(
                                    "GPX Export, Trail Share & Directions: ",
                                    "Data is only passed to another app when you tap Share GPX, Save .GPX, Share, or Directions (opening the Android share sheet, file picker, or map app)."
                                ),
                                labeledLine(
                                    "Android Auto Backup: ",
                                    "System backup is enabled in the manifest (allowBackup=\"true\"), meaning Android OS may back up local app data to your personal Google account if device backup is turned on."
                                )
                            )
                        )
                    }
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

private fun labeledLine(label: String, detail: String): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
        append(label)
    }
    append(detail)
}

@Composable
private fun AboutGroupCard(
    title: String,
    intro: String? = null,
    items: List<AnnotatedString>,
    footnote: String? = null
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (intro != null) {
                Text(
                    text = intro,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items.forEach { itemText ->
                Text(
                    text = itemText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (footnote != null) {
                Text(
                    text = footnote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
