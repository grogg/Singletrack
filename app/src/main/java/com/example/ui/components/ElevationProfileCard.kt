package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.ElevationPoint
import com.example.domain.model.MeasurementUnit
import com.example.domain.model.PhysicalEffortRating
import com.example.domain.model.Trail
import com.example.ui.theme.DifficultyEasyColor
import com.example.ui.theme.DifficultyExpertColor
import com.example.ui.theme.DifficultyIntermediateColor
import com.example.ui.theme.Terracotta
import com.example.ui.theme.TerracottaBright

@Composable
fun ElevationProfileCard(
    trail: Trail,
    isLoadingElevation: Boolean,
    modifier: Modifier = Modifier,
    measurementUnit: MeasurementUnit = MeasurementUnit.IMPERIAL
) {
    val profile = trail.elevationProfile
    val gainFeet = profile?.elevationGainFeet ?: trail.elevationGainFeet

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("elevation_profile_card"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header with icon, title, and Physical Effort badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Elevation & Effort",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                if (profile != null) {
                    PhysicalEffortBadge(effort = profile.effortRating)
                }
            }

            when {
                isLoadingElevation && profile == null -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.5.dp
                        )
                        Text(
                            text = if (gainFeet != null) {
                                "Mapped climb ${FormatUtils.formatElevation(gainFeet, measurementUnit, prefix = "+")} • Loading elevation curve…"
                            } else {
                                "Calculating elevation profile along trail…"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                profile != null -> {
                    // Key climbing & gradient metrics
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ElevationMetricItem(
                            label = "Climb",
                            value = FormatUtils.formatElevation(
                                profile.elevationGainFeet,
                                measurementUnit,
                                prefix = "+"
                            ),
                            valueColor = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("elevation_gain_value")
                        )
                        ElevationMetricItem(
                            label = "Descent",
                            value = FormatUtils.formatElevation(
                                profile.elevationLossFeet,
                                measurementUnit,
                                prefix = "-"
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        ElevationMetricItem(
                            label = "Peak",
                            value = FormatUtils.formatElevation(profile.maxElevationFeet, measurementUnit),
                            modifier = Modifier.weight(1f)
                        )
                        ElevationMetricItem(
                            label = "Max Grade",
                            value = "${profile.maxGradePercent}%",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Terrain elevation curve
                    ElevationChartCanvas(
                        points = profile.points,
                        minElevFeet = profile.minElevationFeet,
                        maxElevFeet = profile.maxElevationFeet,
                        measurementUnit = measurementUnit
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val lowVal = FormatUtils.elevationValue(profile.minElevationFeet, measurementUnit)
                        val highLabel = FormatUtils.formatElevation(profile.maxElevationFeet, measurementUnit)
                        Text(
                            text = "Range: $lowVal–$highLabel",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = profile.sourceLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                gainFeet != null -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Total Elevation Gain",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = FormatUtils.formatElevation(gainFeet, measurementUnit, prefix = "+"),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.testTag("elevation_gain_value")
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "OSM Tagged Ascent",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Terrain,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = if (trail.isOfflineCached && trail.geometry.size < 2) {
                                "Elevation profile unavailable offline for summary-only cached trails."
                            } else {
                                "Elevation profile unavailable right now (requires network DEM lookup or tagged OSM ascent)."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PhysicalEffortBadge(effort: PhysicalEffortRating) {
    val isDark = isSystemInDarkTheme()
    val accent = when (effort) {
        PhysicalEffortRating.GENTLE -> DifficultyEasyColor
        PhysicalEffortRating.MODERATE -> DifficultyIntermediateColor
        PhysicalEffortRating.STRENUOUS -> if (isDark) TerracottaBright else Terracotta
        PhysicalEffortRating.GRUELING -> DifficultyExpertColor
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = accent.copy(alpha = 0.14f),
        modifier = Modifier.testTag("elevation_effort_badge")
    ) {
        Text(
            text = "${effort.label} Effort",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = accent,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun ElevationMetricItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = valueColor
        )
    }
}

@Immutable
private data class ElevationChartStats(
    val totalMiles: Double,
    val minVal: Double,
    val span: Double,
    val peakIndex: Int
)

@Composable
private fun ElevationChartCanvas(
    points: List<ElevationPoint>,
    minElevFeet: Int,
    maxElevFeet: Int,
    measurementUnit: MeasurementUnit
) {
    val isDark = isSystemInDarkTheme()
    val density = LocalDensity.current
    val lineColor = if (isDark) TerracottaBright else Terracotta
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val peakColor = MaterialTheme.colorScheme.primary
    val chartStats = remember(points) {
        val totalMiles = (points.lastOrNull()?.distanceMiles ?: 0.0).coerceAtLeast(0.05)
        var minVal = Double.POSITIVE_INFINITY
        var maxVal = Double.NEGATIVE_INFINITY
        var peakIndex = -1
        for (i in points.indices) {
            val elev = points[i].elevationFeet
            if (elev < minVal) minVal = elev
            if (elev > maxVal) {
                maxVal = elev
                peakIndex = i
            }
        }
        ElevationChartStats(
            totalMiles = totalMiles,
            minVal = minVal,
            span = (maxVal - minVal).coerceAtLeast(25.0),
            peakIndex = peakIndex
        )
    }
    val totalMiles = chartStats.totalMiles
    val minVal = chartStats.minVal
    val span = chartStats.span
    val peakIndex = chartStats.peakIndex
    val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f) }
    val strokePath = remember { Path() }
    val fillPath = remember { Path() }
    val lineStroke = remember(density) {
        Stroke(
            width = with(density) { 2.6.dp.toPx() },
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("elevation_chart_canvas")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(118.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (points.size < 2) return@Canvas

                val padTop = 10.dp.toPx()
                val padBottom = 10.dp.toPx()
                val padH = 6.dp.toPx()
                val chartWidth = (size.width - padH * 2f).coerceAtLeast(1f)
                val chartHeight = (size.height - padTop - padBottom).coerceAtLeast(1f)
                val gridStroke = 1.dp.toPx()

                for (step in 0..2) {
                    val y = padTop + chartHeight * (step / 2f)
                    drawLine(
                        color = gridColor,
                        start = Offset(padH, y),
                        end = Offset(size.width - padH, y),
                        strokeWidth = gridStroke,
                        pathEffect = dashEffect
                    )
                }

                fun xFor(pt: ElevationPoint): Float {
                    val xFrac = (pt.distanceMiles / totalMiles).toFloat().coerceIn(0f, 1f)
                    return padH + xFrac * chartWidth
                }

                fun yFor(pt: ElevationPoint): Float {
                    val yFrac = ((pt.elevationFeet - minVal) / span).toFloat().coerceIn(0f, 1f)
                    return padTop + chartHeight * (1f - yFrac)
                }

                strokePath.reset()
                var prevX = xFor(points[0])
                var prevY = yFor(points[0])
                val firstX = prevX
                strokePath.moveTo(prevX, prevY)
                for (i in 1 until points.size) {
                    val currX = xFor(points[i])
                    val currY = yFor(points[i])
                    val midX = (prevX + currX) / 2f
                    strokePath.cubicTo(midX, prevY, midX, currY, currX, currY)
                    prevX = currX
                    prevY = currY
                }

                fillPath.reset()
                fillPath.addPath(strokePath)
                fillPath.lineTo(prevX, padTop + chartHeight)
                fillPath.lineTo(firstX, padTop + chartHeight)
                fillPath.close()

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            lineColor.copy(alpha = 0.34f),
                            lineColor.copy(alpha = 0.03f)
                        ),
                        startY = padTop,
                        endY = padTop + chartHeight
                    )
                )

                drawPath(
                    path = strokePath,
                    color = lineColor,
                    style = lineStroke
                )

                if (peakIndex in points.indices) {
                    val peakPt = points[peakIndex]
                    val peakOffset = Offset(xFor(peakPt), yFor(peakPt))
                    drawCircle(
                        color = Color.White,
                        radius = 5.dp.toPx(),
                        center = peakOffset
                    )
                    drawCircle(
                        color = peakColor,
                        radius = 3.2.dp.toPx(),
                        center = peakOffset
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = FormatUtils.formatDistance(0.0, measurementUnit),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Low ${FormatUtils.formatElevation(minElevFeet, measurementUnit)} • Peak ${FormatUtils.formatElevation(maxElevFeet, measurementUnit)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = FormatUtils.formatDistance(totalMiles, measurementUnit),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
