package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.health.HealthConnectAvailability
import com.example.data.health.HealthConnectStatus

@Composable
internal fun GoogleFitSyncSection(
    healthConnectStatus: HealthConnectStatus,
    autoSyncEnabled: Boolean,
    hasGoogleFitInstalled: Boolean,
    onConnectHealthConnect: () -> Unit,
    onInstallHealthConnect: () -> Unit,
    onOpenHealthConnectSettings: () -> Unit,
    onOpenGoogleFit: () -> Unit,
    onToggleAutoSync: (Boolean) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.testTag("settings_google_fit_section")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (healthConnectStatus.permissionsGranted) {
                    Icons.Filled.CheckCircle
                } else {
                    Icons.Filled.Sync
                },
                contentDescription = null,
                tint = if (healthConnectStatus.permissionsGranted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.secondary
                },
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = when {
                    healthConnectStatus.permissionsGranted ->
                        "Google Fit Sync (Health Connect Active)"
                    healthConnectStatus.availability == HealthConnectAvailability.UPDATE_REQUIRED ->
                        "Google Fit Sync (Update Required)"
                    healthConnectStatus.availability == HealthConnectAvailability.UNAVAILABLE ->
                        "Google Fit Sync (Unavailable)"
                    else -> "Google Fit Sync"
                },
                style = MaterialTheme.typography.titleMedium
            )
        }

        Text(
            text = when {
                healthConnectStatus.permissionsGranted ->
                    "Finished rides write biking sessions, GPS routes, distance, speed, and heart rate to Health Connect for Google Fit."
                healthConnectStatus.availability == HealthConnectAvailability.UPDATE_REQUIRED ->
                    "Install or update Health Connect from Google Play to sync rides with Google Fit."
                healthConnectStatus.availability == HealthConnectAvailability.UNAVAILABLE ->
                    "Health Connect is unavailable on this device. Rides are saved locally on-device and can be exported to GPX anytime."
                else ->
                    "Grant Health Connect permissions so Google Fit automatically displays your recorded MTB rides."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        when {
            healthConnectStatus.permissionsGranted -> {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onToggleAutoSync(!autoSyncEnabled) }
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
                                text = "Auto-sync finished rides",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Automatically send completed rides to Health Connect.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoSyncEnabled,
                            onCheckedChange = onToggleAutoSync,
                            modifier = Modifier.testTag("auto_sync_switch")
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenHealthConnectSettings,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("health_connect_settings_button")
                    ) {
                        Text("Health Connect")
                    }
                    if (hasGoogleFitInstalled) {
                        FilledTonalButton(
                            onClick = onOpenGoogleFit,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("open_google_fit_button")
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("install_health_connect_button")
                ) {
                    Text("Install / Update Health Connect")
                }
            }
        }
    }
}
