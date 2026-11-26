package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.data.remote.TopoTileLoader
import com.example.data.repository.MapTileSpec
import com.example.data.repository.WebMercatorUtils
import com.example.ui.theme.Terracotta
import com.example.ui.theme.TerracottaBright
import com.example.ui.theme.TopoMapBadgeScrim
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.roundToInt

class TopoTileOverlayState(
    val tileLoader: TopoTileLoader,
    val tileZoom: Int,
    val visibleTiles: List<MapTileSpec>,
    val tileTick: Int
)

@Composable
fun rememberTopoTileOverlayState(
    centerLat: Double,
    centerLon: Double,
    zoom: Float,
    canvasSize: IntSize,
    baseTilePx: Float,
    tileLoader: TopoTileLoader,
    rotationDegrees: Float = 0f,
    enabled: Boolean = true
): TopoTileOverlayState {
    val coroutineScope = rememberCoroutineScope()
    var tileTick by remember { mutableIntStateOf(0) }
    val tileZoom = zoom.roundToInt().coerceIn(8, 17)

    val visibleTiles = remember(
        centerLat,
        centerLon,
        zoom,
        tileZoom,
        canvasSize,
        baseTilePx,
        rotationDegrees,
        enabled
    ) {
        if (!enabled || canvasSize.width == 0 || canvasSize.height == 0) {
            emptyList()
        } else {
            WebMercatorUtils.computeVisibleTiles(
                centerLat = centerLat,
                centerLon = centerLon,
                zoom = zoom,
                tileZoom = tileZoom,
                baseTilePx = baseTilePx,
                viewportWidth = canvasSize.width.toFloat(),
                viewportHeight = canvasSize.height.toFloat(),
                rotationDegrees = rotationDegrees
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

    return remember(tileLoader, tileZoom, visibleTiles, tileTick) {
        TopoTileOverlayState(
            tileLoader = tileLoader,
            tileZoom = tileZoom,
            visibleTiles = visibleTiles,
            tileTick = tileTick
        )
    }
}

fun DrawScope.drawFallbackTopoGrid(contourColor: Color) {
    val w = size.width
    val h = size.height
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
}

fun DrawScope.drawTopoTileBackdrop(
    overlayState: TopoTileOverlayState,
    centerLat: Double,
    centerLon: Double,
    zoom: Float,
    baseTilePx: Float
) {
    if (overlayState.tileTick < 0) return

    val w = size.width
    val h = size.height
    val tileZoom = overlayState.tileZoom
    val scaleFactor = 2.0.pow((zoom - tileZoom).toDouble()).toFloat()
    val renderedTilePx = baseTilePx * scaleFactor
    val cTileXInt = WebMercatorUtils.lonToTileX(centerLon, tileZoom.toDouble())
    val cTileYInt = WebMercatorUtils.latToTileY(centerLat, tileZoom.toDouble())

    for (spec in overlayState.visibleTiles) {
        val bitmap = overlayState.tileLoader.getCachedTile(spec.z, spec.x, spec.y) ?: continue
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
}

fun DrawScope.mapToScreenOffset(unrotated: Offset, rotationDegrees: Float): Offset {
    if (rotationDegrees == 0f) return unrotated
    val projected = WebMercatorUtils.mapOffsetToScreen(
        unrotatedX = unrotated.x,
        unrotatedY = unrotated.y,
        viewportWidth = size.width,
        viewportHeight = size.height,
        rotationDegrees = rotationDegrees
    )
    return Offset(projected.x, projected.y)
}

fun DrawScope.drawMapLabelBadge(
    measured: TextLayoutResult,
    anchor: Offset,
    badgeAlpha: Float = 0.88f,
    badgeColor: Color = TopoMapBadgeScrim
) {
    val w = size.width
    val h = size.height
    if (anchor.x !in -40f..(w + 40f) || anchor.y !in -40f..(h + 40f)) return

    val padH = 6f
    val padV = 3f
    val tl = Offset(
        x = (anchor.x - measured.size.width / 2f).coerceIn(
            8f,
            (w - measured.size.width - 16f).coerceAtLeast(8f)
        ),
        y = (anchor.y - measured.size.height - 16f).coerceAtLeast(8f)
    )
    drawRoundRect(
        color = badgeColor.copy(alpha = badgeAlpha),
        topLeft = Offset(tl.x - padH, tl.y - padV),
        size = Size(measured.size.width + padH * 2, measured.size.height + padV * 2),
        cornerRadius = CornerRadius(8f, 8f)
    )
    drawText(
        textLayoutResult = measured,
        color = Color.White,
        topLeft = tl
    )
}

@Composable
fun MapCompassButton(
    rotationDegrees: Float,
    onResetNorth: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val normalized = WebMercatorUtils.normalizeRotationDegrees(rotationDegrees)
    val isRotated = abs(normalized) > 0.5f
    val southTipColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val bezelColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    val northTipColor = if (isRotated) TerracottaBright else Terracotta
    val needlePath = remember { Path() }
    val bezelStroke = remember { Stroke(width = 2.2f) }
    val a11yDescription = if (isRotated) {
        "Compass rotated ${abs(normalized).roundToInt()} degrees. Tap to reset north up"
    } else {
        "Compass: North up"
    }

    FilledIconButton(
        onClick = onResetNorth,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = if (isRotated) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.96f)
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
            },
            contentColor = if (isRotated) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        ),
        modifier = modifier
            .size(48.dp)
            .semantics { contentDescription = a11yDescription }
            .testTag(testTag)
    ) {
        Canvas(modifier = Modifier.size(26.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val radius = size.minDimension * 0.44f
            drawCircle(
                color = bezelColor,
                radius = radius,
                center = Offset(cx, cy),
                style = bezelStroke
            )

            rotate(degrees = normalized, pivot = Offset(cx, cy)) {
                val tipOffset = radius * 0.84f
                val wingOffset = radius * 0.28f

                // North needle (red/terracotta)
                needlePath.reset()
                needlePath.moveTo(cx, cy - tipOffset)
                needlePath.lineTo(cx - wingOffset, cy)
                needlePath.lineTo(cx + wingOffset, cy)
                needlePath.close()
                drawPath(path = needlePath, color = northTipColor)

                // South needle (muted)
                needlePath.reset()
                needlePath.moveTo(cx, cy + tipOffset)
                needlePath.lineTo(cx - wingOffset, cy)
                needlePath.lineTo(cx + wingOffset, cy)
                needlePath.close()
                drawPath(path = needlePath, color = southTipColor)

                drawCircle(
                    color = Color.White,
                    radius = radius * 0.14f,
                    center = Offset(cx, cy)
                )
            }
        }
    }
}
