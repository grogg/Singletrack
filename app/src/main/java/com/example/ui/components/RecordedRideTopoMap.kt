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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.data.remote.TopoTileLoader
import com.example.data.repository.GeoUtils
import com.example.domain.model.GeoPoint
import com.example.domain.model.MeasurementUnit
import com.example.domain.model.RecordedRide
import com.example.ui.theme.DifficultyEasyColor
import com.example.ui.theme.Terracotta
import com.example.ui.theme.TerracottaBright
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sinh
import kotlin.math.tan

private data class RideTileSpec(val z: Int, val x: Int, val y: Int)

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
    val coroutineScope = rememberCoroutineScope()
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
        computeInitialCamera(routePoints)
    }

    var centerLat by remember(ride.id, initialBounds) { mutableDoubleStateOf(initialBounds.centerLat) }
    var centerLon by remember(ride.id, initialBounds) { mutableDoubleStateOf(initialBounds.centerLon) }
    var zoom by remember(ride.id, initialBounds) { mutableFloatStateOf(initialBounds.zoom) }

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var tileTick by remember { mutableIntStateOf(0) }

    val baseTilePx = with(density) { 200.dp.toPx() }
    val isDark = isSystemInDarkTheme()
    val fallbackBg = if (isDark) Color(0xFF13211B) else Color(0xFFEBE7DC)
    val contourColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
    val routeAccent = if (isDark) TerracottaBright else Terracotta
    val endpointLabelStyle = MaterialTheme.typography.labelSmall

    val tileZoom = zoom.roundToInt().coerceIn(8, 17)
    val visibleTiles = remember(centerLat, centerLon, zoom, canvasSize) {
        if (canvasSize.width == 0 || canvasSize.height == 0 || routePoints.isEmpty()) {
            emptyList()
        } else {
            computeVisibleRideTiles(
                centerLat = centerLat,
                centerLon = centerLon,
                zoom = zoom,
                tileZoom = tileZoom,
                baseTilePx = baseTilePx,
                viewportWidth = canvasSize.width.toFloat(),
                viewportHeight = canvasSize.height.toFloat()
            )
        }
    }

    LaunchedEffect(visibleTiles) {
        visibleTiles.forEach { spec ->
            if (tileLoader.shouldFetch(spec.z, spec.x, spec.y)) {
                coroutineScope.launch {
                    val loaded = tileLoader.loadTile(spec.z, spec.x, spec.y)
                    if (loaded != null) {
                        tileTick++
                    }
                }
            }
        }
    }

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
                .onSizeChanged { canvasSize = it }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, gestureZoom, _ ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()

                        val oldZoom = zoom
                        val zoomDelta = (ln(gestureZoom.toDouble().coerceAtLeast(0.1)) / ln(2.0)).toFloat()
                        val newZoom = (oldZoom + zoomDelta).coerceIn(8.5f, 17.5f)

                        val dxFromCenter = centroid.x - w / 2f
                        val dyFromCenter = centroid.y - h / 2f

                        val oldCenterX = rideLonToTileX(centerLon, oldZoom.toDouble())
                        val oldCenterY = rideLatToTileY(centerLat, oldZoom.toDouble())

                        val focalTileXAtOld = oldCenterX + (dxFromCenter / baseTilePx)
                        val focalTileYAtOld = oldCenterY + (dyFromCenter / baseTilePx)
                        val focalLon = rideTileXToLon(focalTileXAtOld, oldZoom.toDouble())
                        val focalLat = rideTileYToLat(focalTileYAtOld, oldZoom.toDouble())

                        zoom = newZoom

                        val focalTileXAtNew = rideLonToTileX(focalLon, newZoom.toDouble())
                        val focalTileYAtNew = rideLatToTileY(focalLat, newZoom.toDouble())

                        val nextCenterX = focalTileXAtNew - ((dxFromCenter + pan.x) / baseTilePx)
                        val nextCenterY = focalTileYAtNew - ((dyFromCenter + pan.y) / baseTilePx)

                        centerLon = rideTileXToLon(nextCenterX, newZoom.toDouble())
                        centerLat = rideTileYToLat(nextCenterY, newZoom.toDouble())
                    }
                }
        ) {
            @Suppress("UNUSED_EXPRESSION")
            tileTick

            val w = size.width
            val h = size.height

            // Procedural topo contour grid fallback underneath tiles
            val gridStep = 64f
            var gx = 0f
            while (gx < w) {
                drawLine(contourColor, Offset(gx, 0f), Offset(gx, h), strokeWidth = 1f)
                gx += gridStep
            }
            var gy = 0f
            while (gy < h) {
                drawLine(contourColor, Offset(0f, gy), Offset(w, gy), strokeWidth = 1f)
                gy += gridStep
            }

            // 1. Topographic Map Tiles
            val scaleFactor = 2.0.pow((zoom - tileZoom).toDouble()).toFloat()
            val renderedTilePx = baseTilePx * scaleFactor
            val cTileXInt = rideLonToTileX(centerLon, tileZoom.toDouble())
            val cTileYInt = rideLatToTileY(centerLat, tileZoom.toDouble())

            for (spec in visibleTiles) {
                val bitmap = tileLoader.getCachedTile(spec.z, spec.x, spec.y) ?: continue
                val nInt = 1 shl spec.z
                var dxTiles = spec.x - cTileXInt
                if (dxTiles > nInt / 2.0) dxTiles -= nInt
                if (dxTiles < -nInt / 2.0) dxTiles += nInt

                val left = w / 2f + (dxTiles * renderedTilePx).toFloat()
                val top = h / 2f + ((spec.y - cTileYInt) * renderedTilePx).toFloat()
                val tileDim = ceil(renderedTilePx).toInt() + 1

                drawImage(
                    image = bitmap,
                    dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                    dstSize = IntSize(tileDim, tileDim)
                )
            }

            if (isDark) {
                drawRect(color = Color.Black.copy(alpha = 0.20f))
            }

            val cTileX = rideLonToTileX(centerLon, zoom.toDouble())
            val cTileY = rideLatToTileY(centerLat, zoom.toDouble())
            fun toScreen(pt: GeoPoint): Offset {
                val px = w / 2f + ((rideLonToTileX(pt.lon, zoom.toDouble()) - cTileX) * baseTilePx).toFloat()
                val py = h / 2f + ((rideLatToTileY(pt.lat, zoom.toDouble()) - cTileY) * baseTilePx).toFloat()
                return Offset(px, py)
            }

            // 2. Recorded GPS route trace
            if (routePoints.size >= 2) {
                reusableRoutePath.reset()
                val first = toScreen(routePoints.first())
                reusableRoutePath.moveTo(first.x, first.y)
                for (i in 1 until routePoints.size) {
                    val pt = toScreen(routePoints[i])
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
            val startPt = toScreen(routePoints.first())
            val endPt = toScreen(routePoints.last())

            drawCircle(color = Color.White, radius = 12f, center = startPt)
            drawCircle(color = DifficultyEasyColor, radius = 8f, center = startPt)

            if (routePoints.size >= 2) {
                drawCircle(color = Color.White, radius = 12f, center = endPt)
                drawCircle(color = routeAccent, radius = 8f, center = endPt)
            }

            if (isFullScreen || zoom >= 12.5f) {
                val badges = buildList {
                    add(startBadgeLayout to startPt)
                    if (routePoints.size >= 2) {
                        add(finishBadgeLayout to endPt)
                    }
                }
                badges.forEach { (measured, pt) ->
                    if (pt.x in -20f..(w + 20f) && pt.y in -20f..(h + 20f)) {
                        val padH = 6f
                        val padV = 3f
                        val tl = Offset(
                            x = (pt.x - measured.size.width / 2f).coerceIn(8f, (w - measured.size.width - 16f).coerceAtLeast(8f)),
                            y = (pt.y - measured.size.height - 16f).coerceAtLeast(8f)
                        )
                        drawRoundRect(
                            color = Color(0xFF133125).copy(alpha = 0.88f),
                            topLeft = Offset(tl.x - padH, tl.y - padV),
                            size = Size(measured.size.width + padH * 2, measured.size.height + padV * 2),
                            cornerRadius = CornerRadius(8f, 8f)
                        )
                        drawText(textLayoutResult = measured, color = Color.White, topLeft = tl)
                    }
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

            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
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

private data class RideMapCamera(
    val centerLat: Double,
    val centerLon: Double,
    val zoom: Float
)

private fun computeInitialCamera(points: List<GeoPoint>): RideMapCamera {
    if (points.isEmpty()) return RideMapCamera(39.0, -98.0, 12.0f)
    if (points.size == 1) return RideMapCamera(points.first().lat, points.first().lon, 15.0f)

    val minLat = points.minOf { it.lat }
    val maxLat = points.maxOf { it.lat }
    val minLon = points.minOf { it.lon }
    val maxLon = points.maxOf { it.lon }

    val cLat = (minLat + maxLat) / 2.0
    val cLon = (minLon + maxLon) / 2.0
    val spanMiles = GeoUtils.haversineMiles(minLat, minLon, maxLat, maxLon)

    val zoom = when {
        spanMiles < 0.25 -> 16.0f
        spanMiles < 0.6 -> 15.0f
        spanMiles < 1.5 -> 14.0f
        spanMiles < 3.5 -> 13.0f
        spanMiles < 8.0 -> 12.0f
        spanMiles < 18.0 -> 11.0f
        else -> 10.0f
    }
    return RideMapCamera(cLat, cLon, zoom)
}

private fun computeVisibleRideTiles(
    centerLat: Double,
    centerLon: Double,
    zoom: Float,
    tileZoom: Int,
    baseTilePx: Float,
    viewportWidth: Float,
    viewportHeight: Float
): List<RideTileSpec> {
    val nInt = 1 shl tileZoom
    val scaleFactor = 2.0.pow((zoom - tileZoom).toDouble()).toFloat()
    val renderedTilePx = baseTilePx * scaleFactor

    val cTileX = rideLonToTileX(centerLon, tileZoom.toDouble())
    val cTileY = rideLatToTileY(centerLat, tileZoom.toDouble())

    val halfTilesW = (viewportWidth / 2f) / renderedTilePx + 1.0
    val halfTilesH = (viewportHeight / 2f) / renderedTilePx + 1.0

    val minX = floor(cTileX - halfTilesW).toInt()
    val maxX = ceil(cTileX + halfTilesW).toInt()
    val minY = floor(cTileY - halfTilesH).toInt().coerceAtLeast(0)
    val maxY = ceil(cTileY + halfTilesH).toInt().coerceAtMost(nInt - 1)

    val specs = mutableListOf<RideTileSpec>()
    for (y in minY..maxY) {
        for (x in minX..maxX) {
            val wrappedX = ((x % nInt) + nInt) % nInt
            specs.add(RideTileSpec(tileZoom, wrappedX, y))
        }
    }
    return specs
}

private fun rideLonToTileX(lon: Double, zoom: Double): Double {
    return (lon + 180.0) / 360.0 * 2.0.pow(zoom)
}

private fun rideLatToTileY(lat: Double, zoom: Double): Double {
    val clamped = lat.coerceIn(-85.05112878, 85.05112878)
    val latRad = Math.toRadians(clamped)
    return (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * 2.0.pow(zoom)
}

private fun rideTileXToLon(tileX: Double, zoom: Double): Double {
    val lon = tileX / 2.0.pow(zoom) * 360.0 - 180.0
    return ((lon + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
}

private fun rideTileYToLat(tileY: Double, zoom: Double): Double {
    val n = PI - 2.0 * PI * tileY / 2.0.pow(zoom)
    return Math.toDegrees(atan(sinh(n))).coerceIn(-85.0, 85.0)
}
