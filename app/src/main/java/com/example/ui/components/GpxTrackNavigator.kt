package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.GpxNavigationState
import com.example.domain.model.GpxRouteStatus
import com.example.domain.model.GpxTrack
import com.example.domain.model.MeasurementUnit
import kotlin.math.roundToInt

@Composable
fun GpxTrackNavigatorSection(
    gpxNavigation: GpxNavigationState,
    measurementUnit: MeasurementUnit,
    hasLocationPermission: Boolean,
    onSelectGpxFile: () -> Unit,
    onStartNavigation: () -> Unit,
    onStopNavigation: () -> Unit,
    onReverseTrack: () -> Unit,
    onClearTrack: () -> Unit,
    onOpenFullScreenMap: () -> Unit,
    onToggleFollowLocation: () -> Unit,
    onDisableFollowLocation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val track = gpxNavigation.track

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("gpx_track_navigator_section")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Route,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "GPX Track Navigator",
                        style = MaterialTheme.typography.titleLarge
                    )
                }

                if (track != null) {
                    IconButton(
                        onClick = onClearTrack,
                        modifier = Modifier.testTag("gpx_clear_track_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Clear loaded GPX track"
                        )
                    }
                }
            }

            if (track == null) {
                Text(
                    text = "Load any .gpx route file from your device to preview trail contours, waypoints, and follow live GPS turn-by-turn track guidance.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = onSelectGpxFile,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("gpx_select_file_button")
                ) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select GPX File")
                }
            } else {
                TrackHeaderAndStats(
                    track = track,
                    measurementUnit = measurementUnit
                )

                GpxNavigatorTopoMap(
                    track = track,
                    navigationState = gpxNavigation,
                    measurementUnit = measurementUnit,
                    onToggleFollowLocation = onToggleFollowLocation,
                    onDisableFollowLocation = onDisableFollowLocation,
                    isFullScreen = false,
                    onToggleFullScreen = onOpenFullScreenMap,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                )

                GpxLiveGuidancePanel(
                    navigationState = gpxNavigation,
                    measurementUnit = measurementUnit
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (gpxNavigation.isNavigating) {
                        Button(
                            onClick = onStopNavigation,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("gpx_toggle_navigation_button")
                        ) {
                            Icon(Icons.Filled.Stop, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Stop Nav")
                        }
                    } else {
                        Button(
                            onClick = onStartNavigation,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("gpx_toggle_navigation_button")
                        ) {
                            Icon(Icons.Filled.Navigation, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (hasLocationPermission) "Navigate Track" else "Grant GPS & Nav")
                        }
                    }

                    FilledTonalButton(
                        onClick = onReverseTrack,
                        modifier = Modifier.testTag("gpx_reverse_track_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SwapVert,
                            contentDescription = "Reverse track direction",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reverse")
                    }
                }

                OutlinedButton(
                    onClick = onSelectGpxFile,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("gpx_select_file_button")
                ) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Change GPX File")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrackHeaderAndStats(
    track: GpxTrack,
    measurementUnit: MeasurementUnit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = track.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (track.isReversed) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Text(
                        text = "REVERSED",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }

        Text(
            text = track.fileName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = FormatUtils.formatDistance(track.totalDistanceMiles, measurementUnit, decimals = 2),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "${FormatUtils.formatElevation(track.elevationGainFeet, measurementUnit, prefix = "+")} climb",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                text = "${track.points.size} pts",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (track.waypoints.isNotEmpty()) {
                Text(
                    text = "${track.waypoints.size} waypoints",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GpxLiveGuidancePanel(
    navigationState: GpxNavigationState,
    measurementUnit: MeasurementUnit
) {
    val containerColor = when (navigationState.routeStatus) {
        GpxRouteStatus.OFF_ROUTE -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f)
        GpxRouteStatus.FINISHED -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
        GpxRouteStatus.ON_TRACK -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("gpx_live_guidance_panel")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    val statusIcon = when (navigationState.routeStatus) {
                        GpxRouteStatus.OFF_ROUTE -> Icons.Filled.WarningAmber
                        GpxRouteStatus.FINISHED -> Icons.Filled.CheckCircle
                        else -> Icons.Filled.Navigation
                    }
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (navigationState.routeStatus == GpxRouteStatus.OFF_ROUTE) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                    Text(
                        text = navigationState.turnInstruction,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
                ) {
                    Text(
                        text = navigationState.routeStatus.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (navigationState.routeStatus == GpxRouteStatus.OFF_ROUTE) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            if (navigationState.isNavigating) {
                LinearProgressIndicator(
                    progress = { navigationState.progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                GuidanceCell(
                    label = "Completed",
                    value = FormatUtils.formatDistance(
                        navigationState.distanceCompletedMiles,
                        measurementUnit,
                        decimals = 2
                    )
                )
                GuidanceCell(
                    label = "Remaining",
                    value = FormatUtils.formatDistance(
                        navigationState.distanceRemainingMiles,
                        measurementUnit,
                        decimals = 2
                    )
                )
                val offRouteText = navigationState.offRouteFeet?.let { feet ->
                    val valRounded = if (measurementUnit == MeasurementUnit.METRIC) {
                        "${(feet * FormatUtils.FEET_TO_METERS).roundToInt()} m"
                    } else {
                        "${feet.roundToInt()} ft"
                    }
                    valRounded
                } ?: "--"
                GuidanceCell(
                    label = "Off Track",
                    value = offRouteText
                )
                GuidanceCell(
                    label = "Bearing",
                    value = navigationState.bearingCardinal ?: "--"
                )
            }
        }
    }
}

@Composable
private fun GuidanceCell(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}
