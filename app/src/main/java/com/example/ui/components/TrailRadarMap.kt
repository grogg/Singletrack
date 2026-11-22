package com.example.ui.components

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.data.remote.TopoTileLoader
import com.example.data.repository.GeoUtils
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
import com.example.ui.theme.Terracotta
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.tan

private data class VisibleTileSpec(val z: Int, val x: Int, val y: Int)

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
    val coroutineScope = rememberCoroutineScope()
    val tileLoader = remember(context) { TopoTileLoader(context) }
    val textMeasurer: TextMeasurer = rememberTextMeasurer()

    val startLat = initialSelectedTrail?.centerLat ?: origin.lat
    val startLon = initialSelectedTrail?.centerLon ?: origin.lon
    val startZoom = if (initialSelectedTrail != null) 13.4f else 10.8f

    var centerLat by remember(origin, initialSelectedTrail?.osmKey) { mutableDoubleStateOf(startLat) }
    var centerLon by remember(origin, initialSelectedTrail?.osmKey) { mutableDoubleStateOf(startLon) }
    var zoom by remember(origin, initialSelectedTrail?.osmKey) { mutableFloatStateOf(startZoom) }
    var selectedPin by remember(trails, initialSelectedTrail?.osmKey) {
        mutableStateOf(initialSelectedTrail ?: trails.firstOrNull())
    }

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var tileTick by remember { mutableIntStateOf(0) }

    val latestTrails by rememberUpdatedState(trails)
    val baseTilePx = with(density) { 200.dp.toPx() }
    val isDark = isSystemInDarkTheme()
    val fallbackBg = if (isDark) Color(0xFF13211B) else Color(0xFFEBE7DC)
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

    val tileZoom = zoom.roundToInt().coerceIn(8, 17)
    val visibleTiles = remember(centerLat, centerLon, zoom, canvasSize) {
        if (canvasSize.width == 0 || canvasSize.height == 0) {
            emptyList()
        } else {
            computeVisibleTiles(
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

    // Launch non-cancelling tile loads in coroutineScope so active panning doesn't abort downloads
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

    val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f) }
    val ring10Stroke = remember(dashEffect) { Stroke(width = 2.5f, pathEffect = dashEffect) }
    val ring20Stroke = remember(dashEffect) { Stroke(width = 4f, pathEffect = dashEffect) }
    val selectedHaloStroke = remember { Stroke(width = 16f, cap = StrokeCap.Round, join = StrokeJoin.Round) }
    val selectedCasingStroke = remember { Stroke(width = 10.5f, cap = StrokeCap.Round, join = StrokeJoin.Round) }
    val selectedCoreStroke = remember { Stroke(width = 6.5f, cap = StrokeCap.Round, join = StrokeJoin.Round) }
    val unselectedCasingStroke = remember { Stroke(width = 6.5f, cap = StrokeCap.Round, join = StrokeJoin.Round) }
    val unselectedCoreStroke = remember { Stroke(width = 3.8f, cap = StrokeCap.Round, join = StrokeJoin.Round) }
    val reusablePath = remember { Path() }
    val unselectedTrails = remember(trails, selectedPin?.osmKey) {
        trails.filter { it.osmKey != selectedPin?.osmKey }
    }
    val labelLayoutCache = remember(trails, routeLabelStyle) {
        HashMap<String, TextLayoutResult>()
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
                .onSizeChanged { canvasSize = it }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, gestureZoom, _ ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()

                        val oldZoom = zoom
                        val zoomDelta = (ln(gestureZoom.toDouble().coerceAtLeast(0.1)) / ln(2.0)).toFloat()
                        val newZoom = (oldZoom + zoomDelta).coerceIn(8.5f, 17.5f)

                        // Keep pinch centroid anchored when zooming
                        val dxFromCenter = centroid.x - w / 2f
                        val dyFromCenter = centroid.y - h / 2f

                        val oldCenterX = lonToTileX(centerLon, oldZoom.toDouble())
                        val oldCenterY = latToTileY(centerLat, oldZoom.toDouble())

                        val focalTileXAtOld = oldCenterX + (dxFromCenter / baseTilePx)
                        val focalTileYAtOld = oldCenterY + (dyFromCenter / baseTilePx)
                        val focalLon = tileXToLon(focalTileXAtOld, oldZoom.toDouble())
                        val focalLat = tileYToLat(focalTileYAtOld, oldZoom.toDouble())

                        zoom = newZoom

                        val focalTileXAtNew = lonToTileX(focalLon, newZoom.toDouble())
                        val focalTileYAtNew = latToTileY(focalLat, newZoom.toDouble())

                        val nextCenterX = focalTileXAtNew - ((dxFromCenter + pan.x) / baseTilePx)
                        val nextCenterY = focalTileYAtNew - ((dyFromCenter + pan.y) / baseTilePx)

                        centerLon = tileXToLon(nextCenterX, newZoom.toDouble())
                        centerLat = tileYToLat(nextCenterY, newZoom.toDouble())
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { tapOffset ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val currentZoom = zoom.toDouble()
                        val cTileX = lonToTileX(centerLon, currentZoom)
                        val cTileY = latToTileY(centerLat, currentZoom)

                        fun toScreen(pt: GeoPoint): Offset {
                            val px = w / 2f + ((lonToTileX(pt.lon, currentZoom) - cTileX) * baseTilePx).toFloat()
                            val py = h / 2f + ((latToTileY(pt.lat, currentZoom) - cTileY) * baseTilePx).toFloat()
                            return Offset(px, py)
                        }

                        var bestTrail: Trail? = null
                        var bestDist = 42f

                        for (trail in latestTrails) {
                            val pinPos = toScreen(GeoPoint(trail.centerLat, trail.centerLon))
                            val pinDist = hypot(tapOffset.x - pinPos.x, tapOffset.y - pinPos.y)
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
                                    val segDist = pointToSegmentDistance(tapOffset, a, b)
                                    if (segDist < bestDist) {
                                        bestDist = segDist
                                        bestTrail = trail
                                    }
                                }
                            }
                        }

                        if (bestTrail != null) {
                            selectedPin = bestTrail
                        }
                    }
                }
        ) {
            @Suppress("UNUSED_EXPRESSION")
            tileTick

            val w = size.width
            val h = size.height

            // Procedural topo grid fallback underneath tiles
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

            // 1. Draw Topographic Map Tiles
            val scaleFactor = 2.0.pow((zoom - tileZoom).toDouble()).toFloat()
            val renderedTilePx = baseTilePx * scaleFactor
            val cTileXInt = lonToTileX(centerLon, tileZoom.toDouble())
            val cTileYInt = latToTileY(centerLat, tileZoom.toDouble())

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

            val cTileX = lonToTileX(centerLon, zoom.toDouble())
            val cTileY = latToTileY(centerLat, zoom.toDouble())
            fun toScreen(pt: GeoPoint): Offset {
                val px = w / 2f + ((lonToTileX(pt.lon, zoom.toDouble()) - cTileX) * baseTilePx).toFloat()
                val py = h / 2f + ((latToTileY(pt.lat, zoom.toDouble()) - cTileY) * baseTilePx).toFloat()
                return Offset(px, py)
            }

            // 2. Geodesic 10-mile and 20-mile boundary circles
            drawGeodesicRing(
                points = innerRing10Mi,
                toScreen = ::toScreen,
                reusablePath = reusablePath,
                color = Color(0xFF133125).copy(alpha = 0.55f),
                stroke = ring10Stroke
            )
            drawGeodesicRing(
                points = outerRing20Mi,
                toScreen = ::toScreen,
                reusablePath = reusablePath,
                color = Terracotta.copy(alpha = 0.90f),
                stroke = ring20Stroke
            )

            // 3. Draw unselected trail routes first, then selected trail route on top
            for (trail in unselectedTrails) {
                drawTrailOverlay(
                    trail = trail,
                    color = routeColor(trail.difficulty),
                    isSelected = false,
                    reusablePath = reusablePath,
                    haloStroke = selectedHaloStroke,
                    casingStroke = unselectedCasingStroke,
                    coreStroke = unselectedCoreStroke,
                    toScreen = ::toScreen
                )
            }
            selectedPin?.let { selected ->
                drawTrailOverlay(
                    trail = selected,
                    color = routeColor(selected.difficulty),
                    isSelected = true,
                    reusablePath = reusablePath,
                    haloStroke = selectedHaloStroke,
                    casingStroke = selectedCasingStroke,
                    coreStroke = selectedCoreStroke,
                    toScreen = ::toScreen
                )
            }

            // 4. Draw trail name labels when zoomed in or in full-screen for selected trail
            if (isFullScreen || zoom >= 12.0f) {
                val labeledTrails = buildList {
                    selectedPin?.let { add(it) }
                    if (zoom >= 12.2f) {
                        addAll(unselectedTrails.take(14))
                    }
                }
                for (trail in labeledTrails) {
                    val pos = toScreen(GeoPoint(trail.centerLat, trail.centerLon))
                    if (pos.x in -40f..(w + 40f) && pos.y in -40f..(h + 40f)) {
                        val labelText = trail.name.take(26)
                        val measured = labelLayoutCache.getOrPut(labelText) {
                            textMeasurer.measure(
                                text = labelText,
                                style = routeLabelStyle
                            )
                        }
                        val padH = 6f
                        val padV = 3f
                        val labelTopLeft = Offset(
                            x = (pos.x - measured.size.width / 2f).coerceIn(8f, (w - measured.size.width - 16f).coerceAtLeast(8f)),
                            y = (pos.y - measured.size.height - 16f).coerceAtLeast(8f)
                        )
                        drawRoundRect(
                            color = Color(0xFF133125).copy(alpha = if (trail.osmKey == selectedPin?.osmKey) 0.92f else 0.76f),
                            topLeft = Offset(labelTopLeft.x - padH, labelTopLeft.y - padV),
                            size = androidx.compose.ui.geometry.Size(
                                measured.size.width + padH * 2,
                                measured.size.height + padV * 2
                            ),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
                        )
                        drawText(
                            textLayoutResult = measured,
                            color = Color.White,
                            topLeft = labelTopLeft
                        )
                    }
                }
            }

            // 5. User origin GPS marker
            val originOffset = toScreen(GeoPoint(origin.lat, origin.lon))
            drawCircle(color = Terracotta.copy(alpha = 0.24f), radius = 24f, center = originOffset)
            drawCircle(color = Color.White, radius = 12f, center = originOffset)
            drawCircle(color = Terracotta, radius = 8f, center = originOffset)
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

            // Top-right Fullscreen, Zoom In, Zoom Out, Recenter controls
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

            // Bottom selected trail preview card
            selectedPin?.let { trail ->
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(12.dp),
                    shape = RoundedCornerShape(18.dp),
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
                color = Terracotta.copy(alpha = 0.35f),
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
        drawCircle(color = Terracotta.copy(alpha = 0.30f), radius = 20f, center = pinOffset)
    }
    drawCircle(
        color = Color.White,
        radius = if (isSelected) 11f else 7.5f,
        center = pinOffset
    )
    drawCircle(
        color = color,
        radius = if (isSelected) 8f else 5f,
        center = pinOffset
    )
}

private fun computeVisibleTiles(
    centerLat: Double,
    centerLon: Double,
    zoom: Float,
    tileZoom: Int,
    baseTilePx: Float,
    viewportWidth: Float,
    viewportHeight: Float
): List<VisibleTileSpec> {
    val nInt = 1 shl tileZoom
    val scaleFactor = 2.0.pow((zoom - tileZoom).toDouble()).toFloat()
    val renderedTilePx = baseTilePx * scaleFactor

    val cTileX = lonToTileX(centerLon, tileZoom.toDouble())
    val cTileY = latToTileY(centerLat, tileZoom.toDouble())

    val halfTilesW = (viewportWidth / 2f) / renderedTilePx + 1.0
    val halfTilesH = (viewportHeight / 2f) / renderedTilePx + 1.0

    val minX = floor(cTileX - halfTilesW).toInt()
    val maxX = ceil(cTileX + halfTilesW).toInt()
    val minY = floor(cTileY - halfTilesH).toInt().coerceAtLeast(0)
    val maxY = ceil(cTileY + halfTilesH).toInt().coerceAtMost(nInt - 1)

    val specs = mutableListOf<VisibleTileSpec>()
    for (y in minY..maxY) {
        for (x in minX..maxX) {
            val wrappedX = ((x % nInt) + nInt) % nInt
            specs.add(VisibleTileSpec(tileZoom, wrappedX, y))
        }
    }
    return specs
}

private fun lonToTileX(lon: Double, zoom: Double): Double {
    return (lon + 180.0) / 360.0 * 2.0.pow(zoom)
}

private fun latToTileY(lat: Double, zoom: Double): Double {
    val clamped = lat.coerceIn(-85.05112878, 85.05112878)
    val latRad = Math.toRadians(clamped)
    return (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * 2.0.pow(zoom)
}

private fun tileXToLon(tileX: Double, zoom: Double): Double {
    val lon = tileX / 2.0.pow(zoom) * 360.0 - 180.0
    return ((lon + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
}

private fun tileYToLat(tileY: Double, zoom: Double): Double {
    val n = PI - 2.0 * PI * tileY / 2.0.pow(zoom)
    return Math.toDegrees(atan(sinh(n))).coerceIn(-85.0, 85.0)
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
