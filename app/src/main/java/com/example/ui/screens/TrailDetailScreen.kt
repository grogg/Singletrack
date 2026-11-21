package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.Trail
import com.example.ui.components.DifficultyBadge
import com.example.ui.components.TrailShapeCanvas
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrailDetailScreen(
    trail: Trail,
    isLoadingGeometry: Boolean,
    showBackNavigation: Boolean,
    onBack: () -> Unit,
    onRecordRideOnTrail: (Trail) -> Unit,
    onExpandOnTopoMap: ((Trail) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    if (showBackNavigation) {
        BackHandler(onBack = onBack)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        if (showBackNavigation) {
            TopAppBar(
                title = { Text("Trail Briefing") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DifficultyBadge(difficulty = trail.difficulty)
                trail.rawDifficultyTag?.let { raw ->
                    Text(
                        text = raw.uppercase(Locale.US),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (trail.isOfflineCached) {
                    Icon(
                        imageVector = Icons.Filled.OfflinePin,
                        contentDescription = "Offline cached",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Text(
                text = trail.name,
                style = MaterialTheme.typography.headlineMedium
            )

            Text(
                text = trail.difficulty.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Trail Polyline Geometry Map Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
            ) {
                TrailShapeCanvas(
                    points = trail.geometry,
                    segments = trail.routeSegments,
                    emptyText = if (isLoadingGeometry) {
                        "Loading trail geometry…"
                    } else {
                        "Route polyline unavailable for this segment"
                    },
                    modifier = Modifier.fillMaxSize()
                )
                if (isLoadingGeometry) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(14.dp)
                            .size(22.dp),
                        strokeWidth = 2.5.dp
                    )
                }
                if (onExpandOnTopoMap != null) {
                    FilledIconButton(
                        onClick = { onExpandOnTopoMap(trail) },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.94f)
                        ),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                            .size(48.dp)
                            .testTag("detail_expand_map_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Fullscreen,
                            contentDescription = "Expand trail route on full-screen topographic map"
                        )
                    }
                }
            }

            // 2x2 Telemetry Stats Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    label = "Distance Away",
                    value = String.format(
                        Locale.US,
                        "%.1f mi %s",
                        trail.distanceFromUserMiles,
                        trail.bearingCardinal
                    ),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "Trail Length",
                    value = trail.lengthMiles?.let {
                        String.format(Locale.US, "%.2f mi", it)
                    } ?: "Not tagged",
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    label = "Surface",
                    value = trail.surface,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "Direction / Flow",
                    value = when (trail.oneway?.lowercase(Locale.US)) {
                        "yes", "true", "1" -> "One-Way"
                        "no" -> "Two-Way"
                        else -> "Two-Way / Open"
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            // Primary Ride Action
            Button(
                onClick = { onRecordRideOnTrail(trail) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("record_ride_on_trail_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.DirectionsBike, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Record Ride on This Trail")
            }

            // Directions + Share row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        val label = Uri.encode(trail.name)
                        val uri = Uri.parse(
                            "geo:${trail.centerLat},${trail.centerLon}?q=${trail.centerLat},${trail.centerLon}($label)"
                        )
                        val intent = Intent(Intent.ACTION_VIEW, uri)
                        if (intent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(intent)
                        } else {
                            val webUri = Uri.parse(
                                "https://www.openstreetmap.org/?mlat=${trail.centerLat}&mlon=${trail.centerLon}#map=15/${trail.centerLat}/${trail.centerLon}"
                            )
                            context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("directions_button")
                ) {
                    Icon(Icons.Filled.Directions, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Directions")
                }

                OutlinedButton(
                    onClick = {
                        val shareText = buildString {
                            append("${trail.name} (${trail.difficulty.label})\n")
                            append("Surface: ${trail.surface}\n")
                            trail.lengthMiles?.let {
                                append(String.format(Locale.US, "Length: %.2f mi\n", it))
                            }
                            append(
                                String.format(
                                    Locale.US,
                                    "Trailhead: https://maps.google.com/?q=%.5f,%.5f",
                                    trail.centerLat,
                                    trail.centerLon
                                )
                            )
                        }
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share MTB Trail"))
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("share_trail_button")
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share")
                }
            }

            // Additional OSM Trail Details
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Trailhead & Mapping Info",
                        style = MaterialTheme.typography.titleSmall
                    )
                    InfoRow("Coordinates", String.format(Locale.US, "%.5f°, %.5f°", trail.centerLat, trail.centerLon))
                    trail.networkName?.takeIf { it.isNotBlank() }?.let {
                        InfoRow("Trail Network", it)
                    }
                    trail.operatorName?.takeIf { it.isNotBlank() }?.let {
                        InfoRow("Managed By", it)
                    }
                    trail.uphillScale?.takeIf { it.isNotBlank() }?.let {
                        InfoRow("Uphill Scale", "STS Uphill $it")
                    }
                    trail.description?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium
        )
    }
}
