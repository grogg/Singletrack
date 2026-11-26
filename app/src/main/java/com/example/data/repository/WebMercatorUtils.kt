package com.example.data.repository

import com.example.domain.model.GeoPoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.tan

data class MapTileSpec(val z: Int, val x: Int, val y: Int)

data class MapCameraState(
    val centerLat: Double,
    val centerLon: Double,
    val zoom: Float,
    val rotationDegrees: Float = 0f
)

data class ProjectedPoint(val x: Float, val y: Float)

object WebMercatorUtils {
    private const val MAX_LAT = 85.05112878
    private const val MIN_ZOOM = 8.5f
    private const val MAX_ZOOM = 17.5f

    fun normalizeRotationDegrees(degrees: Float): Float {
        if (!degrees.isFinite()) return 0f
        var normalized = degrees % 360f
        if (normalized <= -180f) normalized += 360f
        if (normalized > 180f) normalized -= 360f
        return if (normalized == -0f) 0f else normalized
    }

    fun mapOffsetToScreen(
        unrotatedX: Float,
        unrotatedY: Float,
        viewportWidth: Float,
        viewportHeight: Float,
        rotationDegrees: Float
    ): ProjectedPoint {
        if (rotationDegrees == 0f) return ProjectedPoint(unrotatedX, unrotatedY)
        val cx = viewportWidth / 2f
        val cy = viewportHeight / 2f
        val dx = (unrotatedX - cx).toDouble()
        val dy = (unrotatedY - cy).toDouble()
        val rad = Math.toRadians(rotationDegrees.toDouble())
        val cosR = cos(rad)
        val sinR = sin(rad)
        return ProjectedPoint(
            x = cx + (dx * cosR - dy * sinR).toFloat(),
            y = cy + (dx * sinR + dy * cosR).toFloat()
        )
    }

    fun screenToUnrotatedMapOffset(
        screenX: Float,
        screenY: Float,
        viewportWidth: Float,
        viewportHeight: Float,
        rotationDegrees: Float
    ): ProjectedPoint {
        if (rotationDegrees == 0f) return ProjectedPoint(screenX, screenY)
        val cx = viewportWidth / 2f
        val cy = viewportHeight / 2f
        val dx = (screenX - cx).toDouble()
        val dy = (screenY - cy).toDouble()
        val rad = Math.toRadians(rotationDegrees.toDouble())
        val cosR = cos(rad)
        val sinR = sin(rad)
        return ProjectedPoint(
            x = cx + (dx * cosR + dy * sinR).toFloat(),
            y = cy + (-dx * sinR + dy * cosR).toFloat()
        )
    }

    fun lonToTileX(lon: Double, zoom: Double): Double {
        return (lon + 180.0) / 360.0 * 2.0.pow(zoom)
    }

    fun latToTileY(lat: Double, zoom: Double): Double {
        val clamped = lat.coerceIn(-MAX_LAT, MAX_LAT)
        val latRad = Math.toRadians(clamped)
        return (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * 2.0.pow(zoom)
    }

    fun tileXToLon(tileX: Double, zoom: Double): Double {
        val lon = tileX / 2.0.pow(zoom) * 360.0 - 180.0
        return ((lon + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
    }

    fun tileYToLat(tileY: Double, zoom: Double): Double {
        val n = PI - 2.0 * PI * tileY / 2.0.pow(zoom)
        return Math.toDegrees(atan(sinh(n))).coerceIn(-85.0, 85.0)
    }

    fun applyPinchPanZoom(
        current: MapCameraState,
        viewportWidth: Float,
        viewportHeight: Float,
        centroidX: Float,
        centroidY: Float,
        panX: Float,
        panY: Float,
        gestureZoom: Float,
        baseTilePx: Float,
        gestureRotation: Float = 0f
    ): MapCameraState {
        val zoomDelta = (ln(gestureZoom.toDouble().coerceAtLeast(0.1)) / ln(2.0)).toFloat()
        val newZoom = (current.zoom + zoomDelta).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val newRotation = normalizeRotationDegrees(current.rotationDegrees + gestureRotation)

        val oldRad = Math.toRadians(current.rotationDegrees.toDouble())
        val oldCos = cos(oldRad)
        val oldSin = sin(oldRad)
        val prevRelX = (centroidX - panX - viewportWidth / 2f).toDouble()
        val prevRelY = (centroidY - panY - viewportHeight / 2f).toDouble()
        val oldUx = prevRelX * oldCos + prevRelY * oldSin
        val oldUy = -prevRelX * oldSin + prevRelY * oldCos

        val oldZoom = current.zoom.toDouble()
        val oldCenterX = lonToTileX(current.centerLon, oldZoom)
        val oldCenterY = latToTileY(current.centerLat, oldZoom)

        val focalLon = tileXToLon(oldCenterX + (oldUx / baseTilePx), oldZoom)
        val focalLat = tileYToLat(oldCenterY + (oldUy / baseTilePx), oldZoom)

        val newRad = Math.toRadians(newRotation.toDouble())
        val newCos = cos(newRad)
        val newSin = sin(newRad)
        val currRelX = (centroidX - viewportWidth / 2f).toDouble()
        val currRelY = (centroidY - viewportHeight / 2f).toDouble()
        val newUx = currRelX * newCos + currRelY * newSin
        val newUy = -currRelX * newSin + currRelY * newCos

        val nextZoom = newZoom.toDouble()
        val focalTileXAtNew = lonToTileX(focalLon, nextZoom)
        val focalTileYAtNew = latToTileY(focalLat, nextZoom)

        val nextCenterX = focalTileXAtNew - (newUx / baseTilePx)
        val nextCenterY = focalTileYAtNew - (newUy / baseTilePx)

        return MapCameraState(
            centerLat = tileYToLat(nextCenterY, nextZoom),
            centerLon = tileXToLon(nextCenterX, nextZoom),
            zoom = newZoom,
            rotationDegrees = newRotation
        )
    }

    fun projectX(
        lon: Double,
        zoom: Double,
        centerTileX: Double,
        viewportWidth: Float,
        baseTilePx: Float
    ): Float = viewportWidth / 2f + ((lonToTileX(lon, zoom) - centerTileX) * baseTilePx).toFloat()

    fun projectY(
        lat: Double,
        zoom: Double,
        centerTileY: Double,
        viewportHeight: Float,
        baseTilePx: Float
    ): Float = viewportHeight / 2f + ((latToTileY(lat, zoom) - centerTileY) * baseTilePx).toFloat()

    fun computeVisibleTiles(
        centerLat: Double,
        centerLon: Double,
        zoom: Float,
        tileZoom: Int,
        baseTilePx: Float,
        viewportWidth: Float,
        viewportHeight: Float,
        rotationDegrees: Float = 0f
    ): List<MapTileSpec> {
        val nInt = 1 shl tileZoom
        val scaleFactor = 2.0.pow((zoom - tileZoom).toDouble()).toFloat()
        val renderedTilePx = baseTilePx * scaleFactor

        val cTileX = lonToTileX(centerLon, tileZoom.toDouble())
        val cTileY = latToTileY(centerLat, tileZoom.toDouble())

        val rad = Math.toRadians(rotationDegrees.toDouble())
        val absCos = abs(cos(rad))
        val absSin = abs(sin(rad))
        val halfViewW = (viewportWidth / 2.0) * absCos + (viewportHeight / 2.0) * absSin
        val halfViewH = (viewportWidth / 2.0) * absSin + (viewportHeight / 2.0) * absCos

        val halfTilesW = halfViewW / renderedTilePx + 1.0
        val halfTilesH = halfViewH / renderedTilePx + 1.0

        val minX = floor(cTileX - halfTilesW).toInt()
        val maxX = ceil(cTileX + halfTilesW).toInt()
        val minY = floor(cTileY - halfTilesH).toInt().coerceAtLeast(0)
        val maxY = ceil(cTileY + halfTilesH).toInt().coerceAtMost(nInt - 1)

        val specs = ArrayList<MapTileSpec>(((maxY - minY + 1) * (maxX - minX + 1)).coerceAtLeast(0))
        for (y in minY..maxY) {
            for (x in minX..maxX) {
                val wrappedX = ((x % nInt) + nInt) % nInt
                specs.add(MapTileSpec(tileZoom, wrappedX, y))
            }
        }
        return specs
    }

    fun computeBoundingCamera(points: List<GeoPoint>): MapCameraState {
        if (points.isEmpty()) return MapCameraState(39.0, -98.0, 12.0f)
        if (points.size == 1) return MapCameraState(points.first().lat, points.first().lon, 15.0f)

        var minLat = Double.POSITIVE_INFINITY
        var maxLat = Double.NEGATIVE_INFINITY
        var minLon = Double.POSITIVE_INFINITY
        var maxLon = Double.NEGATIVE_INFINITY
        for (pt in points) {
            if (pt.lat < minLat) minLat = pt.lat
            if (pt.lat > maxLat) maxLat = pt.lat
            if (pt.lon < minLon) minLon = pt.lon
            if (pt.lon > maxLon) maxLon = pt.lon
        }

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
        return MapCameraState(cLat, cLon, zoom)
    }
}
