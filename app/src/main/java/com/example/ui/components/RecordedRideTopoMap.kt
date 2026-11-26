package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.data.remote.TopoTileLoader
import com.example.data.repository.MapCameraState
import com.example.data.repository.WebMercatorUtils
import com.example.domain.model.GeoPoint
import com.example.domain.model.MeasurementUnit
import com.example.domain.model.RecordedRide
import com.example.ui.theme.DifficultyEasyColor
import com.example.ui.theme.Terracotta
import com.example.ui.theme.TerracottaBright
import com.example.ui.theme.TopoMapFallbackDark
import com.example.ui.theme.TopoMapFallbackLight

@Composable
fun RecordedRideTopoMap(
    ride: RecordedRide,
    measurementUnit: MeasurementUnit,
    modifier: Modifier = Modifier,
    isFullScreen: Boolean = false,
    onToggleFullScreen: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val tileLoader = remember(context) { TopoTileLoader(context) }
    val textMeasurer = rememberTextMeasurer()

    val routePoints = remember(ride.id, ride.routePoints, ride.samples) {
        when {
            ride.routePoints.isNotEmpty() -> ride.routePoints
            ride.samples.isNotEmpty() -> ride.samples.map { GeoPoint(it.lat, it.lon) }
            else -> emptyList()
        }
    }

    val initialBounds = remember(routePoints) {
        WebMercatorUtils.computeBoundingCamera(routePoints)
    }

    var centerLat by remember(ride.id, initialBounds) { mutableDoubleStateOf(initialBounds.centerLat) }
    var centerLon by remember(ride.id, initialBounds) { mutableDoubleStateOf(initialBounds.centerLon) }
    var zoom by remember(ride.id, initialBounds) { mutableFloatStateOf(initialBounds.zoom) }
    var rotationDegrees by remember(ride.id, initialBounds) { mutableFloatStateOf(0f) }

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val baseTilePx = with(density) { 200.dp.toPx() }
    val isDark = isSystemInDarkTheme()
    val fallbackBg = if (isDark) TopoMapFallbackDark else TopoMapFallbackLight
    val contourColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
    val routeAccent = if (isDark) TerracottaBright else Terracotta
    val endpointLabelStyle = MaterialTheme.typography.labelSmall

    val tileOverlayState = rememberTopoTileOverlayState(
        centerLat = centerLat,
        centerLon = centerLon,
        zoom = zoom,
        canvasSize = canvasSize,
        baseTilePx = baseTilePx,
        tileLoader = tileLoader,
        rotationDegrees = rotationDegrees,
        enabled = routePoints.isNotEmpty()
    )

    val reusableRoutePath = remember { Path() }
    val haloStroke = remember { Stroke(width = 16f, cap = StrokeCap.Round, join = StrokeJoin.Round) }
    val casingStroke = remember { Stroke(width = 10f, cap = StrokeCap.Round, join = StrokeJoin.Round) }
    val coreStroke = remember { Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round) }
    val startBadgeLayout = remember(endpointLabelStyle) {
        textMeasurer.measure(text = "START", style = endpointLabelStyle)
    }
    val finishBadgeLayout = remember(endpointLabelStyle) {
        textMeasurer.measure(text = "FINISH", style = endpointLabelStyle)
    }

    val containerShape = if (isFullScreen) RoundedCornerShape(0.dp) else RoundedCornerShape(20.dp)
    val hudPaddingModifier = if (isFullScreen) {
        Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .clip(containerShape)
            .background(fallbackBg)
            .testTag(if (isFullScreen) "ride_topo_map_fullscreen" else "ride_topo_map")
    ) {
        if (routePoints.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No GPS coordinates recorded for this ride",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp)
                )
            }
            return@Box
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .onSizeChanged { canvasSize = it }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, gestureZoom, gestureRotation ->
                        val updated = WebMercatorUtils.applyPinchPanZoom(
                            current = MapCameraState(centerLat, centerLon, zoom, rotationDegrees),
                            viewportWidth = size.width.toFloat(),
                            viewportHeight = size.height.toFloat(),
                            centroidX = centroid.x,
                            centroidY = centroid.y,
                            panX = pan.x,
                            panY = pan.y,
                            gestureZoom = gestureZoom,
                            baseTilePx = baseTilePx,
                            gestureRotation = gestureRotation
                        )
                        centerLat = updated.centerLat
                        centerLon = updated.centerLon
                        zoom = updated.zoom
                        rotationDegrees = updated.rotationDegrees
                    }
                }
        ) {
            drawFallbackTopoGrid(contourColor)

            val w = size.width
            val h = size.height
            val cTileX = WebMercatorUtils.lonToTileX(centerLon, zoom.toDouble())
            val cTileY = WebMercatorUtils.latToTileY(centerLat, zoom.toDouble())
            fun toUnrotatedMap(pt: GeoPoint): Offset {
                val px = w / 2f + ((WebMercatorUtils.lonToTileX(pt.lon, zoom.toDouble()) - cTileX) * baseTilePx).toFloat()
                val py = h / 2f + ((WebMercatorUtils.latToTileY(pt.lat, zoom.toDouble()) - cTileY) * baseTilePx).toFloat()
                return Offset(px, py)
            }

            rotate(degrees = rotationDegrees, pivot = Offset(w / 2f, h / 2f)) {
                drawTopoTileBackdrop(
                    overlayState = tileOverlayState,
                    centerLat = centerLat,
                    centerLon = centerLon,
                    zoom = zoom,
                    baseTilePx = baseTilePx
                )

                // 2. Recorded GPS route trace
                if (routePoints.size >= 2) {
                    reusableRoutePath.reset()
                    val first = toUnrotatedMap(routePoints.first())
                    reusableRoutePath.moveTo(first.x, first.y)
                    for (i in 1 until routePoints.size) {
                        val pt = toUnrotatedMap(routePoints[i])
                        reusableRoutePath.lineTo(pt.x, pt.y)
                    }

                    // Glow halo
                    drawPath(
                        path = reusableRoutePath,
                        color = routeAccent.copy(alpha = 0.28f),
                        style = haloStroke
                    )
                    // Crisp white casing
                    drawPath(
                        path = reusableRoutePath,
                        color = Color.White.copy(alpha = 0.96f),
                        style = casingStroke
                    )
                    // Primary track stroke
                    drawPath(
                        path = reusableRoutePath,
                        color = routeAccent,
                        style = coreStroke
                    )
                }

                // 3. Start and Finish markers
                val startPt = toUnrotatedMap(routePoints.first())
                val endPt = toUnrotatedMap(routePoints.last())

                drawCircle(color = Color.White, radius = 12f, center = startPt)
                drawCircle(color = DifficultyEasyColor, radius = 8f, center = startPt)

                if (routePoints.size >= 2) {
                    drawCircle(color = Color.White, radius = 12f, center = endPt)
                    drawCircle(color = routeAccent, radius = 8f, center = endPt)
                }
            }

            if (isDark) {
                drawRect(color = Color.Black.copy(alpha = 0.20f))
            }

            if (isFullScreen || zoom >= 12.5f) {
                val startScreen = mapToScreenOffset(toUnrotatedMap(routePoints.first()), rotationDegrees)
                drawMapLabelBadge(measured = startBadgeLayout, anchor = startScreen)
                if (routePoints.size >= 2) {
                    val endScreen = mapToScreenOffset(toUnrotatedMap(routePoints.last()), rotationDegrees)
                    drawMapLabelBadge(measured = finishBadgeLayout, anchor = endScreen)
                }
            }
        }

        // Floating HUD
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(hudPaddingModifier)
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val distStr = FormatUtils.formatDistance(ride.distanceMiles, measurementUnit, decimals = 2)
                    val speedStr = FormatUtils.formatSpeed(ride.avgSpeedMph, measurementUnit)
                    Text(
                        text = "$distStr • $speedStr • ${routePoints.size} pts",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = "© OpenTopoMap • OSM",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Top
            ) {
                MapCompassButton(
                    rotationDegrees = rotationDegrees,
                    onResetNorth = { rotationDegrees = 0f },
                    testTag = "ride_map_compass_button"
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (onToggleFullScreen != null) {
                        FilledIconButton(
                            onClick = onToggleFullScreen,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag(
                                    if (isFullScreen) "exit_fullscreen_ride_map" else "ride_map_expand_button"
                                )
                        ) {
                            Icon(
                                imageVector = if (isFullScreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                contentDescription = if (isFullScreen) {
                                    "Exit full screen ride map"
                                } else {
                                    "Expand ride route to full screen"
                                }
                            )
                        }
                    }
                    FilledIconButton(
                        onClick = { zoom = (zoom + 0.8f).coerceAtMost(17.5f) },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("ride_map_zoom_in_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Zoom in ride map")
                    }
                    FilledIconButton(
                        onClick = { zoom = (zoom - 0.8f).coerceAtLeast(8.5f) },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("ride_map_zoom_out_button")
                    ) {
                        Icon(Icons.Filled.Remove, contentDescription = "Zoom out ride map")
                    }
                    FilledIconButton(
                        onClick = {
                            centerLat = initialBounds.centerLat
                            centerLon = initialBounds.centerLon
                            zoom = initialBounds.zoom
                            rotationDegrees = 0f
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("ride_map_recenter_button")
                    ) {
                        Icon(Icons.Filled.MyLocation, contentDescription = "Recenter ride route")
                    }
                }
            }
        }
    }
}
