package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

@Composable
internal fun SettingsAboutSection() {
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
