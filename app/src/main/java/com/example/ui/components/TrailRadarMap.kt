package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.data.remote.TopoTileLoader
import com.example.data.repository.GeoUtils
import com.example.data.repository.MapCameraState
import com.example.data.repository.WebMercatorUtils
import com.example.domain.model.GeoPoint
import com.example.domain.model.LocationPlace
import com.example.domain.model.MeasurementUnit
import com.example.domain.model.Trail
import com.example.domain.model.TrailDifficulty
import com.example.ui.theme.DifficultyAdvancedColor
import com.example.ui.theme.DifficultyEasyColor
import com.example.ui.theme.DifficultyExpertColor
import com.example.ui.theme.DifficultyIntermediateColor
import com.example.ui.theme.DifficultyUnratedColor
import com.example.ui.theme.PineDeep
import com.example.ui.theme.SelectedTrailOrange
import com.example.ui.theme.SelectedTrailOrangeGlow
import com.example.ui.theme.Terracotta
import com.example.ui.theme.TopoMapBadgeScrim
import com.example.ui.theme.TopoMapFallbackDark
import com.example.ui.theme.TopoMapFallbackLight
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

@Composable
fun TrailRadarMap(
    trails: List<Trail>,
    origin: LocationPlace,
    onOpenTrailDetail: (Trail) -> Unit,
    modifier: Modifier = Modifier,
    isFullScreen: Boolean = false,
    onToggleFullScreen: (() -> Unit)? = null,
    initialSelectedTrail: Trail? = null,
    measurementUnit: MeasurementUnit = MeasurementUnit.IMPERIAL
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val tileLoader = remember(context) { TopoTileLoader(context) }
    val textMeasurer: TextMeasurer = rememberTextMeasurer()

    val startLat = initialSelectedTrail?.centerLat ?: origin.lat
    val startLon = initialSelectedTrail?.centerLon ?: origin.lon
    val startZoom = if (initialSelectedTrail != null) 13.4f else 10.8f

    var centerLat by remember(origin, initialSelectedTrail?.osmKey) { mutableDoubleStateOf(startLat) }
    var centerLon by remember(origin, initialSelectedTrail?.osmKey) { mutableDoubleStateOf(startLon) }
    var zoom by remember(origin, initialSelectedTrail?.osmKey) { mutableFloatStateOf(startZoom) }
    var rotationDegrees by remember(origin, initialSelectedTrail?.osmKey) { mutableFloatStateOf(0f) }
    var selectedPinOsmKey by remember(initialSelectedTrail?.osmKey) {
        mutableStateOf(initialSelectedTrail?.osmKey ?: trails.firstOrNull()?.osmKey)
    }
    val selectedPin = remember(trails, initialSelectedTrail, selectedPinOsmKey) {
        trails.firstOrNull { it.osmKey == selectedPinOsmKey }
            ?: initialSelectedTrail?.takeIf { selectedPinOsmKey == null || selectedPinOsmKey == it.osmKey }
            ?: trails.firstOrNull()
    }

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val latestTrails by rememberUpdatedState(trails)
    val baseTilePx = with(density) { 200.dp.toPx() }
    val isDark = isSystemInDarkTheme()
    val fallbackBg = if (isDark) TopoMapFallbackDark else TopoMapFallbackLight
    val contourColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
    val routeLabelStyle = MaterialTheme.typography.labelSmall

    fun routeColor(difficulty: TrailDifficulty): Color = when (difficulty) {
        TrailDifficulty.EASY -> DifficultyEasyColor
        TrailDifficulty.INTERMEDIATE -> DifficultyIntermediateColor
        TrailDifficulty.ADVANCED -> DifficultyAdvancedColor
        TrailDifficulty.EXPERT -> DifficultyExpertColor
        TrailDifficulty.UNRATED -> DifficultyUnratedColor
    }

    val outerRing20Mi = remember(origin) {
        buildGeodesicCircle(origin.lat, origin.lon, GeoUtils.RADIUS_TWENTY_MILES)
    }
    val innerRing10Mi = remember(origin) {
        buildGeodesicCircle(origin.lat, origin.lon, 10.0)
    }

    val tileOverlayState = rememberTopoTileOverlayState(
        centerLat = centerLat,
        centerLon = centerLon,
        zoom = zoom,
        canvasSize = canvasSize,
        baseTilePx = baseTilePx,
        tileLoader = tileLoader,
        rotationDegrees = rotationDegrees
    )

    val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f) }
    val ring10Stroke = remember(dashEffect) { Stroke(width = 2.5f, pathEffect = dashEffect) }
    val ring20Stroke = remember(dashEffect) { Stroke(width = 4f, pathEffect = dashEffect) }
    val selectedHaloStroke = remember { Stroke(width = 22f, cap = StrokeCap.Round, join = StrokeJoin.Round) }
    val selectedCasingStroke = remember { Stroke(width = 11.5f, cap = StrokeCap.Round, join = StrokeJoin.Round) }
    val selectedCoreStroke = remember { Stroke(width = 7.5f, cap = StrokeCap.Round, join = StrokeJoin.Round) }
    val unselectedCasingStroke = remember { Stroke(width = 6.5f, cap = StrokeCap.Round, join = StrokeJoin.Round) }
    val unselectedCoreStroke = remember { Stroke(width = 3.8f, cap = StrokeCap.Round, join = StrokeJoin.Round) }
    val reusablePath = remember { Path() }
    val unselectedTrails = remember(trails, selectedPin?.osmKey) {
        trails.filter { it.osmKey != selectedPin?.osmKey }
    }
    val labelLayoutCache = remember(routeLabelStyle) {
        android.util.LruCache<String, TextLayoutResult>(64)
    }

    val containerShape = if (isFullScreen) RoundedCornerShape(0.dp) else RoundedCornerShape(22.dp)
    val hudPaddingModifier = if (isFullScreen) {
        Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .clip(containerShape)
            .background(fallbackBg)
            .testTag(if (isFullScreen) "topo_map_fullscreen" else "trail_radar_map")
    ) {
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
                .pointerInput(Unit) {
                    detectTapGestures { tapOffset ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val mappedTap = WebMercatorUtils.screenToUnrotatedMapOffset(
                            screenX = tapOffset.x,
                            screenY = tapOffset.y,
                            viewportWidth = w,
                            viewportHeight = h,
                            rotationDegrees = rotationDegrees
                        )
                        val unrotatedTap = Offset(mappedTap.x, mappedTap.y)
                        val currentZoom = zoom.toDouble()
                        val cTileX = WebMercatorUtils.lonToTileX(centerLon, currentZoom)
                        val cTileY = WebMercatorUtils.latToTileY(centerLat, currentZoom)

                        fun toScreen(pt: GeoPoint): Offset {
                            val px = w / 2f + ((WebMercatorUtils.lonToTileX(pt.lon, currentZoom) - cTileX) * baseTilePx).toFloat()
                            val py = h / 2f + ((WebMercatorUtils.latToTileY(pt.lat, currentZoom) - cTileY) * baseTilePx).toFloat()
                            return Offset(px, py)
                        }

                        var bestTrail: Trail? = null
                        var bestDist = 42f

                        for (trail in latestTrails) {
                            val pinPos = toScreen(GeoPoint(trail.centerLat, trail.centerLon))
                            val pinDist = hypot(unrotatedTap.x - pinPos.x, unrotatedTap.y - pinPos.y)
                            if (pinDist < bestDist) {
                                bestDist = pinDist
                                bestTrail = trail
                            }

                            val segments = if (trail.routeSegments.isNotEmpty()) {
                                trail.routeSegments
                            } else if (trail.geometry.size >= 2) {
                                listOf(trail.geometry)
                            } else {
                                emptyList()
                            }

                            for (seg in segments) {
                                for (i in 0 until seg.lastIndex) {
                                    val a = toScreen(seg[i])
                                    val b = toScreen(seg[i + 1])
                                    val segDist = pointToSegmentDistance(unrotatedTap, a, b)
                                    if (segDist < bestDist) {
                                        bestDist = segDist
                                        bestTrail = trail
                                    }
                                }
                            }
                        }

                        if (bestTrail != null) {
                            selectedPinOsmKey = bestTrail.osmKey
                        }
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

                // 2. Geodesic 10-mile and 20-mile boundary circles
                drawGeodesicRing(
                    points = innerRing10Mi,
                    toScreen = ::toUnrotatedMap,
                    reusablePath = reusablePath,
                    color = PineDeep.copy(alpha = 0.55f),
                    stroke = ring10Stroke
                )
                drawGeodesicRing(
                    points = outerRing20Mi,
                    toScreen = ::toUnrotatedMap,
                    reusablePath = reusablePath,
                    color = Terracotta.copy(alpha = 0.90f),
                    stroke = ring20Stroke
                )

                // 3. Draw unselected trail routes first, then highlight selected trail in bright orange on top
                for (trail in unselectedTrails) {
                    drawTrailOverlay(
                        trail = trail,
                        color = routeColor(trail.difficulty),
                        haloColor = SelectedTrailOrangeGlow,
                        isSelected = false,
                        reusablePath = reusablePath,
                        haloStroke = selectedHaloStroke,
                        casingStroke = unselectedCasingStroke,
                        coreStroke = unselectedCoreStroke,
                        toScreen = ::toUnrotatedMap
                    )
                }
                selectedPin?.let { selected ->
                    drawTrailOverlay(
                        trail = selected,
                        color = SelectedTrailOrange,
                        haloColor = SelectedTrailOrangeGlow,
                        isSelected = true,
                        reusablePath = reusablePath,
                        haloStroke = selectedHaloStroke,
                        casingStroke = selectedCasingStroke,
                        coreStroke = selectedCoreStroke,
                        toScreen = ::toUnrotatedMap
                    )
                }

                // 5. User origin GPS marker
                val originOffset = toUnrotatedMap(GeoPoint(origin.lat, origin.lon))
                drawCircle(color = Terracotta.copy(alpha = 0.24f), radius = 24f, center = originOffset)
                drawCircle(color = Color.White, radius = 12f, center = originOffset)
                drawCircle(color = Terracotta, radius = 8f, center = originOffset)
            }

            if (isDark) {
                drawRect(color = Color.Black.copy(alpha = 0.20f))
            }

            // 4. Draw upright trail name labels when zoomed in or in full-screen
            if (isFullScreen || zoom >= 12.0f) {
                fun drawTrailLabel(trail: Trail, badgeAlpha: Float, borderColor: Color? = null) {
                    val pos = mapToScreenOffset(
                        unrotated = toUnrotatedMap(GeoPoint(trail.centerLat, trail.centerLon)),
                        rotationDegrees = rotationDegrees
                    )
                    val labelText = trail.name.take(26)
                    val measured = labelLayoutCache.get(labelText) ?: textMeasurer.measure(
                        text = labelText,
                        style = routeLabelStyle
                    ).also { labelLayoutCache.put(labelText, it) }
                    drawMapLabelBadge(
                        measured = measured,
                        anchor = pos,
                        badgeAlpha = badgeAlpha,
                        badgeColor = TopoMapBadgeScrim,
                        borderColor = borderColor
                    )
                }

                selectedPin?.let {
                    drawTrailLabel(
                        trail = it,
                        badgeAlpha = 0.96f,
                        borderColor = SelectedTrailOrange
                    )
                }
                if (zoom >= 12.2f) {
                    val maxUnselectedLabels = minOf(14, unselectedTrails.size)
                    for (i in 0 until maxUnselectedLabels) {
                        drawTrailLabel(unselectedTrails[i], badgeAlpha = 0.76f)
                    }
                }
            }
        }

        // Floating Top & Bottom HUD respecting safeDrawing insets when full-screen
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(hudPaddingModifier)
        ) {
            // Top-left summary & attribution
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
                    Text(
                        text = "${trails.size} trails • ${FormatUtils.formatRadiusShort(GeoUtils.RADIUS_TWENTY_MILES, measurementUnit)} Topo",
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

            // Top-right Compass, Fullscreen, Zoom In, Zoom Out, Recenter controls
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
                    testTag = "map_compass_button"
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
                                .testTag(if (isFullScreen) "exit_fullscreen_map_button" else "expand_fullscreen_map_button")
                        ) {
                            Icon(
                                imageVector = if (isFullScreen) {
                                    Icons.Filled.FullscreenExit
                                } else {
                                    Icons.Filled.Fullscreen
                                },
                                contentDescription = if (isFullScreen) {
                                    "Exit full screen map"
                                } else {
                                    "Expand map to full screen"
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
                            .testTag("map_zoom_in_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Zoom in map")
                    }
                    FilledIconButton(
                        onClick = { zoom = (zoom - 0.8f).coerceAtLeast(8.5f) },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("map_zoom_out_button")
                    ) {
                        Icon(Icons.Filled.Remove, contentDescription = "Zoom out map")
                    }
                    FilledIconButton(
                        onClick = {
                            centerLat = startLat
                            centerLon = startLon
                            zoom = startZoom
                            rotationDegrees = 0f
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("map_recenter_button")
                    ) {
                        Icon(Icons.Filled.MyLocation, contentDescription = "Recenter map")
                    }
                }
            }

            // Bottom selected trail preview card
            selectedPin?.let { trail ->
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(12.dp)
                        .testTag("selected_trail_preview_card"),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.5.dp, SelectedTrailOrange.copy(alpha = 0.72f)),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                DifficultyBadge(difficulty = trail.difficulty, showFullLabel = false)
                                Text(
                                    text = "${FormatUtils.formatDistance(trail.distanceFromUserMiles, measurementUnit)} ${trail.bearingCardinal}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = trail.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val metaLine = buildString {
                                append(trail.surface)
                                trail.lengthMiles?.let {
                                    append(" • ${FormatUtils.formatDistance(it, measurementUnit)} route")
                                }
                            }
                            Text(
                                text = metaLine,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Button(
                            onClick = { onOpenTrailDetail(trail) },
                            modifier = Modifier.testTag("radar_open_trail_button")
                        ) {
                            Text("Details")
                        }
                    }
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGeodesicRing(
    points: List<GeoPoint>,
    toScreen: (GeoPoint) -> Offset,
    reusablePath: Path,
    color: Color,
    stroke: Stroke
) {
    if (points.size < 2) return
    reusablePath.reset()
    val first = toScreen(points.first())
    reusablePath.moveTo(first.x, first.y)
    for (i in 1 until points.size) {
        val pt = toScreen(points[i])
        reusablePath.lineTo(pt.x, pt.y)
    }
    reusablePath.close()
    drawPath(
        path = reusablePath,
        color = color,
        style = stroke
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTrailOverlay(
    trail: Trail,
    color: Color,
    haloColor: Color,
    isSelected: Boolean,
    reusablePath: Path,
    haloStroke: Stroke,
    casingStroke: Stroke,
    coreStroke: Stroke,
    toScreen: (GeoPoint) -> Offset
) {
    val segments = if (trail.routeSegments.isNotEmpty()) {
        trail.routeSegments
    } else if (trail.geometry.size >= 2) {
        listOf(trail.geometry)
    } else {
        emptyList()
    }

    for (segment in segments) {
        if (segment.size < 2) continue
        reusablePath.reset()
        val first = toScreen(segment.first())
        reusablePath.moveTo(first.x, first.y)
        for (i in 1 until segment.size) {
            val pt = toScreen(segment[i])
            reusablePath.lineTo(pt.x, pt.y)
        }

        if (isSelected) {
            drawPath(
                path = reusablePath,
                color = haloColor.copy(alpha = 0.50f),
                style = haloStroke
            )
        }

        drawPath(
            path = reusablePath,
            color = Color.White.copy(alpha = if (isSelected) 0.96f else 0.82f),
            style = casingStroke
        )

        drawPath(
            path = reusablePath,
            color = color,
            style = coreStroke
        )
    }

    val pinOffset = toScreen(GeoPoint(trail.centerLat, trail.centerLon))
    if (isSelected) {
        drawCircle(color = haloColor.copy(alpha = 0.42f), radius = 24f, center = pinOffset)
    }
    drawCircle(
        color = Color.White,
        radius = if (isSelected) 13f else 7.5f,
        center = pinOffset
    )
    drawCircle(
        color = color,
        radius = if (isSelected) 9.5f else 5f,
        center = pinOffset
    )
}

private fun buildGeodesicCircle(
    centerLat: Double,
    centerLon: Double,
    radiusMiles: Double,
    segments: Int = 64
): List<GeoPoint> {
    val angularDist = radiusMiles / GeoUtils.EARTH_RADIUS_MILES
    val lat1 = Math.toRadians(centerLat)
    val lon1 = Math.toRadians(centerLon)
    return (0 until segments).map { i ->
        val bearing = 2.0 * PI * i / segments
        val lat2 = asin(
            sin(lat1) * cos(angularDist) +
                cos(lat1) * sin(angularDist) * cos(bearing)
        )
        val lon2 = lon1 + atan2(
            sin(bearing) * sin(angularDist) * cos(lat1),
            cos(angularDist) - sin(lat1) * sin(lat2)
        )
        GeoPoint(Math.toDegrees(lat2), Math.toDegrees(lon2))
    }
}

private fun pointToSegmentDistance(p: Offset, a: Offset, b: Offset): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val lenSq = dx * dx + dy * dy
    if (lenSq == 0f) return hypot(p.x - a.x, p.y - a.y)
    val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / lenSq).coerceIn(0f, 1f)
    val projX = a.x + t * dx
    val projY = a.y + t * dy
    return hypot(p.x - projX, p.y - projY)
}
