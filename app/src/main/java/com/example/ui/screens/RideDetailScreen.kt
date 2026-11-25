package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.MeasurementUnit
import com.example.domain.model.RecordedRide
import com.example.ui.components.FormatUtils
import com.example.ui.components.RecordedRideTopoMap
import androidx.compose.ui.text.style.TextOverflow
import com.example.ui.theme.Terracotta
import com.example.ui.theme.TerracottaBright
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RideDetailScreen(
    ride: RecordedRide,
    measurementUnit: MeasurementUnit,
    isSyncing: Boolean,
    canSyncHealthConnect: Boolean,
    showBackNavigation: Boolean,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onExpandFullScreenMap: () -> Unit,
    onShareGpx: (RecordedRide) -> Unit,
    onSaveGpxFile: (RecordedRide) -> Unit,
    onSyncHealthConnect: (RecordedRide) -> Unit,
    onDeleteRide: (String) -> Unit,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
    bottomInset: Dp = 0.dp
) {
    if (showBackNavigation) {
        BackHandler(onBack = onBack)
    }

    val elevationSeriesStats = remember(ride.samples) {
        val altitudeMeters = ride.samples.mapNotNull { it.altitudeMeters }
        if (altitudeMeters.size >= 2) {
            var minFeet = Double.POSITIVE_INFINITY
            var maxFeet = Double.NEGATIVE_INFINITY
            val series = List(altitudeMeters.size) { index ->
                val ft = altitudeMeters[index] * 3.28084
                if (ft < minFeet) minFeet = ft
                if (ft > maxFeet) maxFeet = ft
                ft
            }
            Triple(series, minFeet.roundToInt(), maxFeet.roundToInt())
        } else {
            null
        }
    }
    val speedSamplesMph = remember(ride.samples) {
        ride.samples.mapNotNull { it.speedMps?.let { mps -> mps * 2.23694 } }
    }
    val hrSeriesStats = remember(ride.samples, ride.maxHeartRateBpm, ride.avgHeartRateBpm) {
        val hrValues = ride.samples.mapNotNull { it.heartRateBpm?.toDouble() }
        if (hrValues.size >= 2) {
            val minBpm = hrValues.min().roundToInt()
            val maxBpm = ride.maxHeartRateBpm ?: hrValues.max().roundToInt()
            val avgBpm = ride.avgHeartRateBpm ?: hrValues.average().roundToInt()
            Triple(hrValues, minBpm, maxBpm to avgBpm)
        } else {
            null
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("ride_detail_screen")
    ) {
        if (showBackNavigation) {
            TopAppBar(
                title = { Text("Ride Recording") },
                windowInsets = WindowInsets(top = topInset),
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("ride_detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onShareGpx(ride) },
                        modifier = Modifier.testTag("ride_detail_top_share_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "Share ride as GPX file"
                        )
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("ride_detail_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings"
                        )
                    }
                }
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = if (showBackNavigation) 12.dp else 12.dp + topInset,
                    bottom = 24.dp + bottomInset
                ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Title, timestamp, trail & sensor pills
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = ride.title,
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = FormatUtils.formatTimestamp(ride.startTimeMillis),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ride.trailName?.takeIf { it.isNotBlank() }?.let { trail ->
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Route,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = trail,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    ride.sensorName?.takeIf { it.isNotBlank() }?.let { sensor ->
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Bluetooth,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = sensor,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    ride.hrSensorName?.takeIf { it.isNotBlank() }?.let { hrSensor ->
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Favorite,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                                Text(
                                    text = hrSensor,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Recorded Route Topographic Map
            RecordedRideTopoMap(
                ride = ride,
                measurementUnit = measurementUnit,
                isFullScreen = false,
                onToggleFullScreen = onExpandFullScreenMap,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            )

            // Telemetry Stats Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RideDetailStatCard(
                    label = "Duration",
                    value = FormatUtils.formatDuration(ride.durationSeconds),
                    modifier = Modifier.weight(1f)
                )
                RideDetailStatCard(
                    label = "Distance",
                    value = FormatUtils.formatDistance(ride.distanceMiles, measurementUnit, decimals = 2),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RideDetailStatCard(
                    label = "Avg Speed",
                    value = FormatUtils.formatSpeed(ride.avgSpeedMph, measurementUnit),
                    modifier = Modifier.weight(1f)
                )
                RideDetailStatCard(
                    label = "Max Speed",
                    value = FormatUtils.formatSpeed(ride.maxSpeedMph, measurementUnit),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RideDetailStatCard(
                    label = "Elevation Gain",
                    value = FormatUtils.formatElevation(ride.elevationGainFeet, measurementUnit, prefix = "+"),
                    modifier = Modifier.weight(1f)
                )
                if (ride.avgHeartRateBpm != null && ride.avgCadenceRpm == null) {
                    val peakSuffix = ride.maxHeartRateBpm?.let { " (max $it)" } ?: ""
                    RideDetailStatCard(
                        label = "Avg Heart Rate",
                        value = "${ride.avgHeartRateBpm} bpm$peakSuffix",
                        modifier = Modifier.weight(1f)
                    )
                } else if (ride.avgCadenceRpm != null) {
                    val peakSuffix = ride.maxCadenceRpm?.let { " (max $it)" } ?: ""
                    RideDetailStatCard(
                        label = "Avg Cadence",
                        value = "${ride.avgCadenceRpm} rpm$peakSuffix",
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    RideDetailStatCard(
                        label = "GPS Trackpoints",
                        value = "${maxOf(ride.samples.size, ride.routePoints.size)} pts",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (ride.avgHeartRateBpm != null && ride.avgCadenceRpm != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val hrPeakSuffix = ride.maxHeartRateBpm?.let { " (max $it)" } ?: ""
                    RideDetailStatCard(
                        label = "Avg Heart Rate",
                        value = "${ride.avgHeartRateBpm} bpm$hrPeakSuffix",
                        modifier = Modifier.weight(1f)
                    )
                    RideDetailStatCard(
                        label = "GPS Trackpoints",
                        value = "${maxOf(ride.samples.size, ride.routePoints.size)} pts",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Recorded Elevation or Speed Profile Curve
            if (elevationSeriesStats != null) {
                val (elevSeries, minFt, maxFt) = elevationSeriesStats
                RideTelemetryChartCard(
                    title = "Recorded Elevation Curve",
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    values = elevSeries,
                    leftLabel = FormatUtils.formatDistance(0.0, measurementUnit),
                    centerLabel = "Low ${FormatUtils.formatElevation(minFt, measurementUnit)} • Peak ${FormatUtils.formatElevation(maxFt, measurementUnit)}",
                    rightLabel = FormatUtils.formatDistance(ride.distanceMiles, measurementUnit)
                )
            } else if (speedSamplesMph.size >= 2) {
                RideTelemetryChartCard(
                    title = "Recorded Speed Profile",
                    icon = Icons.Filled.Speed,
                    values = speedSamplesMph,
                    leftLabel = FormatUtils.formatDistance(0.0, measurementUnit),
                    centerLabel = "Avg ${FormatUtils.formatSpeed(ride.avgSpeedMph, measurementUnit)} • Peak ${FormatUtils.formatSpeed(ride.maxSpeedMph, measurementUnit)}",
                    rightLabel = FormatUtils.formatDistance(ride.distanceMiles, measurementUnit)
                )
            }

            if (hrSeriesStats != null) {
                val (hrSamples, minBpm, maxAndAvgBpm) = hrSeriesStats
                val (maxBpm, avgBpm) = maxAndAvgBpm
                RideTelemetryChartCard(
                    title = "Recorded Heart Rate",
                    icon = Icons.Filled.Favorite,
                    values = hrSamples,
                    leftLabel = FormatUtils.formatDistance(0.0, measurementUnit),
                    centerLabel = "Min $minBpm bpm • Avg $avgBpm bpm • Peak $maxBpm bpm",
                    rightLabel = FormatUtils.formatDistance(ride.distanceMiles, measurementUnit)
                )
            }

            // GPX Export & Share Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Export & Share GPX Track",
                        style = MaterialTheme.typography.titleMedium
                    )
                    val extraFields = listOfNotNull(
                        "cadence".takeIf { ride.avgCadenceRpm != null },
                        "heart rate".takeIf { ride.avgHeartRateBpm != null }
                    )
                    val gpxDescSuffix = if (extraFields.isNotEmpty()) {
                        ", and ${extraFields.joinToString(" & ")} data."
                    } else {
                        "."
                    }
                    Text(
                        text = "Export standard GPX 1.1 XML with GPS coordinates, elevation, timestamps$gpxDescSuffix",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onShareGpx(ride) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("share_gpx_button")
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share GPX")
                        }
                        FilledTonalButton(
                            onClick = { onSaveGpxFile(ride) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("save_gpx_button")
                        ) {
                            Icon(Icons.Filled.FileDownload, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save .GPX")
                        }
                    }
                }
            }

            // Health Connect Sync Status / Action
            if (ride.syncedToHealthConnect) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Synced to Health Connect (Google Fit)",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            } else {
                FilledTonalButton(
                    onClick = { onSyncHealthConnect(ride) },
                    enabled = !isSyncing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("detail_sync_ride_button")
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
                        if (canSyncHealthConnect) "Sync to Google Fit" else "Connect Health Connect & Sync"
                    )
                }
            }

            // Delete Ride
            OutlinedButton(
                onClick = {
                    onDeleteRide(ride.id)
                    onBack()
                },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("delete_ride_detail_button")
            ) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Delete Ride Recording")
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun RideDetailStatCard(
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
private fun RideTelemetryChartCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    values: List<Double>,
    leftLabel: String,
    centerLabel: String,
    rightLabel: String
) {
    val isDark = isSystemInDarkTheme()
    val lineColor = if (isDark) TerracottaBright else Terracotta
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            val density = LocalDensity.current
            val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f) }
            val strokePath = remember { Path() }
            val fillPath = remember { Path() }
            val lineStroke = remember(density) {
                Stroke(
                    width = with(density) { 2.4.dp.toPx() },
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            }

            val chartData = remember(values) {
                val maxSamples = 180
                val sampled = if (values.size <= maxSamples) {
                    values
                } else {
                    val lastIdx = values.lastIndex
                    List(maxSamples) { i ->
                        val srcIndex = ((i.toLong() * lastIdx) / (maxSamples - 1)).toInt()
                        values[srcIndex]
                    }
                }
                var minV = Double.POSITIVE_INFINITY
                var maxV = Double.NEGATIVE_INFINITY
                for (v in sampled) {
                    if (v < minV) minV = v
                    if (v > maxV) maxV = v
                }
                val spanV = (maxV - minV).coerceAtLeast(10.0)
                Triple(sampled, minV, spanV)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(104.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val (chartValues, minVal, span) = chartData
                    if (chartValues.size < 2) return@Canvas
                    val padTop = 8.dp.toPx()
                    val padBottom = 8.dp.toPx()
                    val padH = 4.dp.toPx()
                    val chartW = (size.width - padH * 2f).coerceAtLeast(1f)
                    val chartH = (size.height - padTop - padBottom).coerceAtLeast(1f)

                    for (step in 0..2) {
                        val y = padTop + chartH * (step / 2f)
                        drawLine(
                            color = gridColor,
                            start = Offset(padH, y),
                            end = Offset(size.width - padH, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = dashEffect
                        )
                    }

                    val lastIndex = (chartValues.size - 1).coerceAtLeast(1)
                    val firstX = padH
                    val firstY = padTop + chartH * (1f - ((chartValues[0] - minVal) / span).toFloat().coerceIn(0f, 1f))
                    var prevX = firstX
                    var prevY = firstY

                    strokePath.reset()
                    strokePath.moveTo(firstX, firstY)
                    for (i in 1 until chartValues.size) {
                        val xFrac = i.toFloat() / lastIndex
                        val yFrac = ((chartValues[i] - minVal) / span).toFloat().coerceIn(0f, 1f)
                        val currX = padH + xFrac * chartW
                        val currY = padTop + chartH * (1f - yFrac)
                        val midX = (prevX + currX) / 2f
                        strokePath.cubicTo(midX, prevY, midX, currY, currX, currY)
                        prevX = currX
                        prevY = currY
                    }

                    fillPath.reset()
                    fillPath.addPath(strokePath)
                    fillPath.lineTo(prevX, padTop + chartH)
                    fillPath.lineTo(firstX, padTop + chartH)
                    fillPath.close()

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                lineColor.copy(alpha = 0.30f),
                                lineColor.copy(alpha = 0.02f)
                            ),
                            startY = padTop,
                            endY = padTop + chartH
                        )
                    )

                    drawPath(
                        path = strokePath,
                        color = lineColor,
                        style = lineStroke
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = leftLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = centerLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = rightLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
