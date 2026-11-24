package com.example.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.BleConnectionStatus
import com.example.domain.model.BleSensorUiState
import com.example.domain.model.DiscoveredBleSensor
import com.example.domain.model.MeasurementUnit
import com.example.domain.model.WheelCircumferencePreset

@Composable
internal fun BluetoothSpeedCadenceSection(
    currentUnit: MeasurementUnit,
    bleSensorState: BleSensorUiState,
    onRequestBleScan: () -> Unit,
    onStopBleScan: () -> Unit,
    onPairBleSensor: (DiscoveredBleSensor) -> Unit,
    onConnectPairedBle: () -> Unit,
    onForgetBleSensor: () -> Unit,
    onSelectWheelCircumference: (Int) -> Unit
) {
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
                        BleStatusBadge(bleSensorState.connectionStatus)
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
                    Icon(Icons.AutoMirrored.Filled.BluetoothSearching, contentDescription = null)
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
            DiscoveredSensorsList(
                header = "Discovered Sensors (${bleSensorState.discoveredSensors.size})",
                sensors = bleSensorState.discoveredSensors,
                tagPrefix = "ble_pair_",
                onPair = onPairBleSensor
            )
        }
    }
}

@Composable
internal fun BluetoothHeartRateSection(
    bleSensorState: BleSensorUiState,
    onRequestBleHrScan: () -> Unit,
    onStopBleHrScan: () -> Unit,
    onPairBleHrSensor: (DiscoveredBleSensor) -> Unit,
    onConnectPairedBleHr: () -> Unit,
    onForgetBleHrSensor: () -> Unit
) {
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
                        BleStatusBadge(bleSensorState.hrConnectionStatus)
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
            DiscoveredSensorsList(
                header = "Discovered HR Sensors (${bleSensorState.discoveredHrSensors.size})",
                sensors = bleSensorState.discoveredHrSensors,
                tagPrefix = "ble_hr_pair_",
                onPair = onPairBleHrSensor
            )
        }
    }
}

@Composable
private fun BleStatusBadge(status: BleConnectionStatus) {
    val label = when (status) {
        BleConnectionStatus.CONNECTED -> "Connected"
        BleConnectionStatus.CONNECTING -> "Connecting…"
        BleConnectionStatus.DISCONNECTED -> "Disconnected"
        BleConnectionStatus.ERROR -> "Error"
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = if (status == BleConnectionStatus.CONNECTED) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (status == BleConnectionStatus.CONNECTED) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun DiscoveredSensorsList(
    header: String,
    sensors: List<DiscoveredBleSensor>,
    tagPrefix: String,
    onPair: (DiscoveredBleSensor) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = header,
            style = MaterialTheme.typography.labelLarge
        )
        sensors.forEach { sensor ->
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
                        onClick = { onPair(sensor) },
                        modifier = Modifier.testTag("$tagPrefix${sensor.address}")
                    ) {
                        Text("Pair")
                    }
                }
            }
        }
    }
}
