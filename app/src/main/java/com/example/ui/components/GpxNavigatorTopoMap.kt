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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.data.remote.TopoTileLoader
import com.example.data.repository.MapCameraState
import com.example.data.repository.WebMercatorUtils
import com.example.domain.model.GeoPoint
import com.example.domain.model.GpxNavigationState
import com.example.domain.model.GpxRouteStatus
import com.example.domain.model.GpxTrack
import com.example.domain.model.MeasurementUnit
import com.example.ui.theme.DifficultyEasyColor
import com.example.ui.theme.GpxCompletedDarkColor
import com.example.ui.theme.RiderBeaconBlue
import com.example.ui.theme.Terracotta
import com.example.ui.theme.TerracottaBright
import com.example.ui.theme.TopoMapFallbackDark
import com.example.ui.theme.TopoMapFallbackLight
import com.example.ui.theme.WaypointAmber

@Composable
fun GpxNavigatorTopoMap(
    track: GpxTrack,
    navigationState: GpxNavigationState,
    measurementUnit: MeasurementUnit,
    onToggleFollowLocation: () -> Unit,
    onDisableFollowLocation: () -> Unit,
    modifier: Modifier = Modifier,
    isFullScreen: Boolean = false,
    onToggleFullScreen: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val tileLoader = remember(context) { TopoTileLoader(context) }
    val textMeasurer = rememberTextMeasurer()

    val routeGeoPoints = remember(track.id, track.isReversed, track.points.size) {
        track.geoPoints
    }
    val initialCamera = remember(routeGeoPoints) {
        WebMercatorUtils.computeBoundingCamera(routeGeoPoints)
    }

    var centerLat by remember(track.id, initialCamera) { mutableDoubleStateOf(initialCamera.centerLat) }
    var centerLon by remember(track.id, initialCamera) { mutableDoubleStateOf(initialCamera.centerLon) }
    var zoom by remember(track.id, initialCamera) { mutableFloatStateOf(initialCamera.zoom) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(
        navigationState.userLocation,
        navigationState.followUserLocation,
        navigationState.isNavigating
    ) {
        val loc = navigationState.userLocation
        if (navigationState.isNavigating && navigationState.followUserLocation && loc != null) {
            centerLat = loc.lat
            centerLon = loc.lon
            if (zoom < 14.5f) {
                zoom = 15.2f
            }
        }
    }

    val baseTilePx = with(density) { 200.dp.toPx() }
    val isDark = isSystemInDarkTheme()
    val fallbackBg = if (isDark) TopoMapFallbackDark else TopoMapFallbackLight
    val contourColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
    val routeAccent = if (isDark) TerracottaBright else Terracotta
    val completedAccent = if (isDark) GpxCompletedDarkColor else DifficultyEasyColor
    val labelStyle = MaterialTheme.typography.labelSmall

    val tileOverlayState = rememberTopoTileOverlayState(
        centerLat = centerLat,
        centerLon = centerLon,
        zoom = zoom,
        canvasSize = canvasSize,
        baseTilePx = baseTilePx,
        tileLoader = tileLoader,
        enabled = routeGeoPoints.isNotEmpty()
    )

    val reusableRemainingPath = remember { Path() }
    val reusableCompletedPath = remember { Path() }
    val haloStroke = remember { Stroke(width = 16f, cap = StrokeCap.Round, join = StrokeJoin.Round) }
    val casingStroke = remember { Stroke(width = 10f, cap = StrokeCap.Round, join = StrokeJoin.Round) }
    val coreStroke = remember { Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round) }
    val dashedTargetEffect = remember { PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f) }

    val startBadgeLayout = remember(labelStyle) { textMeasurer.measure("START", labelStyle) }
    val finishBadgeLayout = remember(labelStyle) { textMeasurer.measure("FINISH", labelStyle) }
    val waypointLayouts = remember(track.waypoints, labelStyle) {
        track.waypoints.take(10).map { wpt ->
            wpt to textMeasurer.measure(wpt.name.take(18), labelStyle)
        }
    }

    val containerShape = if (isFullScreen) RoundedCornerShape(0.dp) else RoundedCornerShape(18.dp)
    val hudPaddingModifier = if (isFullScreen) {
        Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .clip(containerShape)
            .background(fallbackBg)
            .testTag(if (isFullScreen) "gpx_navigator_map_fullscreen" else "gpx_navigator_map")
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, gestureZoom, _ ->
                        if (pan != Offset.Zero) {
                            onDisableFollowLocation()
                        }
                        val updated = WebMercatorUtils.applyPinchPanZoom(
                            current = MapCameraState(centerLat, centerLon, zoom),
                            viewportWidth = size.width.toFloat(),
                            viewportHeight = size.height.toFloat(),
                            centroidX = centroid.x,
                            centroidY = centroid.y,
                            panX = pan.x,
                            panY = pan.y,
                            gestureZoom = gestureZoom,
                            baseTilePx = baseTilePx
                        )
                        centerLat = updated.centerLat
                        centerLon = updated.centerLon
                        zoom = updated.zoom
                    }
                }
        ) {
            drawTopoTileBackdrop(
                overlayState = tileOverlayState,
                centerLat = centerLat,
                centerLon = centerLon,
                zoom = zoom,
                baseTilePx = baseTilePx,
                contourColor = contourColor,
                isDark = isDark
            )

            if (routeGeoPoints.isEmpty()) return@Canvas

            val w = size.width
            val h = size.height
            val cTileX = WebMercatorUtils.lonToTileX(centerLon, zoom.toDouble())
            val cTileY = WebMercatorUtils.latToTileY(centerLat, zoom.toDouble())
            fun toScreen(pt: GeoPoint): Offset {
                val px = w / 2f + ((WebMercatorUtils.lonToTileX(pt.lon, zoom.toDouble()) - cTileX) * baseTilePx).toFloat()
                val py = h / 2f + ((WebMercatorUtils.latToTileY(pt.lat, zoom.toDouble()) - cTileY) * baseTilePx).toFloat()
                return Offset(px, py)
            }

            val splitIdx = navigationState.nearestSegmentIndex.coerceIn(0, (routeGeoPoints.lastIndex - 1).coerceAtLeast(0))
            val splitAnchor = navigationState.nearestPointOnTrack

            // 1. Remaining / Full route path
            if (routeGeoPoints.size >= 2) {
                reusableRemainingPath.reset()
                if (navigationState.isNavigating && splitAnchor != null) {
                    val startScreen = toScreen(splitAnchor)
                    reusableRemainingPath.moveTo(startScreen.x, startScreen.y)
                    for (i in (splitIdx + 1) until routeGeoPoints.size) {
                        val pt = toScreen(routeGeoPoints[i])
                        reusableRemainingPath.lineTo(pt.x, pt.y)
                    }
                } else {
                    val first = toScreen(routeGeoPoints.first())
                    reusableRemainingPath.moveTo(first.x, first.y)
                    for (i in 1 until routeGeoPoints.size) {
                        val pt = toScreen(routeGeoPoints[i])
                        reusableRemainingPath.lineTo(pt.x, pt.y)
                    }
                }

                drawPath(path = reusableRemainingPath, color = routeAccent.copy(alpha = 0.28f), style = haloStroke)
                drawPath(path = reusableRemainingPath, color = Color.White.copy(alpha = 0.96f), style = casingStroke)
                drawPath(path = reusableRemainingPath, color = routeAccent, style = coreStroke)

                // 2. Completed progress segment while navigating
                if (navigationState.isNavigating && (splitIdx > 0 || splitAnchor != null)) {
                    reusableCompletedPath.reset()
                    val first = toScreen(routeGeoPoints.first())
                    reusableCompletedPath.moveTo(first.x, first.y)
                    for (i in 1..splitIdx) {
                        val pt = toScreen(routeGeoPoints[i])
                        reusableCompletedPath.lineTo(pt.x, pt.y)
                    }
                    if (splitAnchor != null) {
                        val anchorScreen = toScreen(splitAnchor)
                        reusableCompletedPath.lineTo(anchorScreen.x, anchorScreen.y)
                    }
                    drawPath(path = reusableCompletedPath, color = Color.White.copy(alpha = 0.95f), style = casingStroke)
                    drawPath(path = reusableCompletedPath, color = completedAccent, style = coreStroke)
                }
            }

            // 3. Connector line from user location back to track / target if approaching or off-route
            val userLoc = navigationState.userLocation
            val targetLoc = navigationState.targetPoint
            if (navigationState.isNavigating && userLoc != null && targetLoc != null &&
                (navigationState.routeStatus == GpxRouteStatus.OFF_ROUTE ||
                    navigationState.routeStatus == GpxRouteStatus.APPROACHING_START)
            ) {
                drawLine(
                    color = RiderBeaconBlue,
                    start = toScreen(userLoc),
                    end = toScreen(targetLoc),
                    strokeWidth = 5f,
                    pathEffect = dashedTargetEffect
                )
            }

            // 4. GPX Waypoints
            waypointLayouts.forEach { (wpt, measured) ->
                val pt = toScreen(GeoPoint(wpt.lat, wpt.lon))
                drawCircle(color = Color.White, radius = 10f, center = pt)
                drawCircle(color = WaypointAmber, radius = 6.5f, center = pt)
                if (isFullScreen || zoom >= 13.0f) {
                    drawMapLabelBadge(measured = measured, anchor = pt, badgeAlpha = 0.86f)
                }
            }

            // 5. Start and Finish markers
            val startPt = toScreen(routeGeoPoints.first())
            val endPt = toScreen(routeGeoPoints.last())
            drawCircle(color = Color.White, radius = 12f, center = startPt)
            drawCircle(color = DifficultyEasyColor, radius = 8f, center = startPt)

            if (routeGeoPoints.size >= 2) {
                drawCircle(color = Color.White, radius = 12f, center = endPt)
                drawCircle(color = routeAccent, radius = 8f, center = endPt)
            }

            if (isFullScreen || zoom >= 12.2f) {
                drawMapLabelBadge(measured = startBadgeLayout, anchor = startPt)
                if (routeGeoPoints.size >= 2) {
                    drawMapLabelBadge(measured = finishBadgeLayout, anchor = endPt)
                }
            }

            // 6. Rider GPS Beacon
            if (userLoc != null) {
                val userPt = toScreen(userLoc)
                drawCircle(color = RiderBeaconBlue.copy(alpha = 0.24f), radius = 28f, center = userPt)
                drawCircle(color = Color.White, radius = 13f, center = userPt)
                drawCircle(color = RiderBeaconBlue, radius = 9f, center = userPt)
            }
        }

        // Floating HUD Controls
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(hudPaddingModifier)
        ) {
            // Top-left live cue pill (when fullscreen) + attribution
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isFullScreen && navigationState.isNavigating) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Explore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = navigationState.turnInstruction,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Left: ${
                                        FormatUtils.formatDistance(
                                            navigationState.distanceRemainingMiles,
                                            measurementUnit,
                                            decimals = 2
                                        )
                                    } • ${navigationState.routeStatus.label}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "© OpenTopoMap • OSM",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.84f))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }

            // Top-right Map Controls (48dp touch targets)
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (onToggleFullScreen != null) {
                    FilledIconButton(
                        onClick = onToggleFullScreen,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag(if (isFullScreen) "gpx_exit_fullscreen_button" else "gpx_expand_map_button")
                    ) {
                        Icon(
                            imageVector = if (isFullScreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                            contentDescription = if (isFullScreen) "Exit full screen GPX map" else "Expand GPX map to full screen"
                        )
                    }
                }

                if (navigationState.isNavigating) {
                    FilledIconButton(
                        onClick = onToggleFollowLocation,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (navigationState.followUserLocation) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                            },
                            contentColor = if (navigationState.followUserLocation) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("gpx_toggle_follow_button")
                    ) {
                        Icon(
                            imageVector = if (navigationState.followUserLocation) Icons.Filled.GpsFixed else Icons.Filled.GpsNotFixed,
                            contentDescription = if (navigationState.followUserLocation) "Disable GPS follow lock" else "Follow GPS position"
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
                        .testTag("gpx_zoom_in_button")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Zoom in GPX map")
                }

                FilledIconButton(
                    onClick = { zoom = (zoom - 0.8f).coerceAtLeast(8.5f) },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("gpx_zoom_out_button")
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = "Zoom out GPX map")
                }

                FilledIconButton(
                    onClick = {
                        onDisableFollowLocation()
                        centerLat = initialCamera.centerLat
                        centerLon = initialCamera.centerLon
                        zoom = initialCamera.zoom
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("gpx_recenter_map_button")
                ) {
                    Icon(Icons.Filled.CenterFocusStrong, contentDescription = "Fit GPX route to screen")
                }
            }
        }
    }
}
