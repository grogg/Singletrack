package com.example.data.export

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.repository.GeoUtils
import com.example.domain.model.GpxTrack
import com.example.domain.model.GpxTrackPoint
import com.example.domain.model.GpxWaypoint
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import java.io.StringReader
import java.util.UUID
import javax.xml.XMLConstants
import javax.xml.parsers.SAXParserFactory

object GpxTrackParser {
    private const val METERS_TO_FEET = 3.28084
    private const val ELEVATION_NOISE_THRESHOLD_FT = 5.0
    private const val MAX_TRACK_POINTS = 12_000

    private data class RawPoint(
        val lat: Double,
        val lon: Double,
        val eleMeters: Double?
    )

    fun parseFromUri(context: Context, uri: Uri): Result<GpxTrack> = runCatching {
        val fileName = resolveDisplayName(context, uri)
            ?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "route.gpx"

        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Could not open selected GPX file.")

        stream.use { input ->
            parseFromStream(input, fileName).getOrThrow()
        }
    }

    fun parseFromXml(xml: String, fileName: String = "route.gpx"): Result<GpxTrack> = runCatching {
        if (xml.isBlank()) {
            throw IllegalArgumentException("GPX file is empty.")
        }
        val handler = SaxGpxHandler()
        newSecureSaxParser().parse(InputSource(StringReader(xml)), handler)
        finalizeParsedHandler(handler, fileName)
    }

    fun parseFromStream(inputStream: InputStream, fileName: String = "route.gpx"): Result<GpxTrack> = runCatching {
        val handler = SaxGpxHandler()
        newSecureSaxParser().parse(inputStream, handler)
        finalizeParsedHandler(handler, fileName)
    }

    fun reverseTrack(track: GpxTrack): GpxTrack {
        val reversedRaw = track.points.asReversed().map { pt ->
            Triple(pt.lat, pt.lon, pt.elevationFeet)
        }
        return buildTrackFromFeet(
            id = track.id,
            name = track.name,
            fileName = track.fileName,
            rawPointsFeet = reversedRaw,
            waypoints = track.waypoints,
            isReversed = !track.isReversed
        )
    }

    fun buildTrack(
        id: String,
        name: String,
        fileName: String,
        rawPoints: List<Triple<Double, Double, Double?>>,
        waypoints: List<GpxWaypoint> = emptyList(),
        isReversed: Boolean = false
    ): GpxTrack {
        val converted = rawPoints.map { (lat, lon, eleMeters) ->
            Triple(lat, lon, eleMeters?.let { it * METERS_TO_FEET })
        }
        return buildTrackFromFeet(
            id = id,
            name = name,
            fileName = fileName,
            rawPointsFeet = converted,
            waypoints = waypoints,
            isReversed = isReversed
        )
    }

    internal fun buildTrackFromFeet(
        id: String,
        name: String,
        fileName: String,
        rawPointsFeet: List<Triple<Double, Double, Double?>>,
        waypoints: List<GpxWaypoint> = emptyList(),
        isReversed: Boolean = false,
        loadedAtMillis: Long = System.currentTimeMillis()
    ): GpxTrack {
        val bounded = downsample(rawPointsFeet)
        val builtPoints = ArrayList<GpxTrackPoint>(bounded.size)
        var cumulativeMiles = 0.0
        var gainFeet = 0.0
        var lossFeet = 0.0
        var elevationAnchorFeet: Double? = null

        bounded.forEachIndexed { idx, (lat, lon, eleFeet) ->
            if (idx > 0) {
                val prev = bounded[idx - 1]
                cumulativeMiles += GeoUtils.haversineMiles(prev.first, prev.second, lat, lon)
            }
            if (eleFeet != null && eleFeet.isFinite()) {
                val anchor = elevationAnchorFeet
                if (anchor == null) {
                    elevationAnchorFeet = eleFeet
                } else {
                    val delta = eleFeet - anchor
                    if (delta >= ELEVATION_NOISE_THRESHOLD_FT) {
                        gainFeet += delta
                        elevationAnchorFeet = eleFeet
                    } else if (delta <= -ELEVATION_NOISE_THRESHOLD_FT) {
                        lossFeet += -delta
                        elevationAnchorFeet = eleFeet
                    }
                }
            }
            builtPoints.add(
                GpxTrackPoint(
                    lat = lat,
                    lon = lon,
                    elevationFeet = eleFeet,
                    cumulativeMiles = cumulativeMiles
                )
            )
        }

        return GpxTrack(
            id = id,
            name = name,
            fileName = fileName,
            points = builtPoints,
            waypoints = waypoints,
            totalDistanceMiles = cumulativeMiles,
            elevationGainFeet = gainFeet,
            elevationLossFeet = lossFeet,
            isReversed = isReversed,
            loadedAtMillis = loadedAtMillis
        )
    }

    private fun finalizeParsedHandler(handler: SaxGpxHandler, fileName: String): GpxTrack {
        val chosenPoints = when {
            handler.trackPoints.size >= 2 -> handler.trackPoints
            handler.routePoints.size >= 2 -> handler.routePoints
            handler.trackPoints.isNotEmpty() -> handler.trackPoints
            handler.routePoints.isNotEmpty() -> handler.routePoints
            handler.waypoints.isNotEmpty() -> handler.waypoints.map {
                RawPoint(it.lat, it.lon, null)
            }
            else -> throw IllegalArgumentException("No valid GPX track, route, or waypoint coordinates found.")
        }

        val trackName = handler.primaryTrackName
            ?: handler.metadataName
            ?: fileName.substringBeforeLast('.').ifBlank { "GPX Route" }

        return buildTrack(
            id = UUID.randomUUID().toString(),
            name = trackName,
            fileName = fileName,
            rawPoints = chosenPoints.map { Triple(it.lat, it.lon, it.eleMeters) },
            waypoints = handler.waypoints,
            isReversed = false
        )
    }

    private fun <T> downsample(list: List<T>): List<T> {
        if (list.size <= MAX_TRACK_POINTS) return list
        val result = ArrayList<T>(MAX_TRACK_POINTS)
        val step = (list.size - 1).toDouble() / (MAX_TRACK_POINTS - 1)
        for (i in 0 until MAX_TRACK_POINTS - 1) {
            val idx = (i * step).toInt().coerceIn(0, list.lastIndex)
            result.add(list[idx])
        }
        result.add(list.last())
        return result
    }

    private fun newSecureSaxParser() = SAXParserFactory.newInstance().apply {
        isNamespaceAware = true
        runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
        runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
    }.newSAXParser()

    private class SaxGpxHandler : DefaultHandler() {
        val trackPoints = mutableListOf<RawPoint>()
        val routePoints = mutableListOf<RawPoint>()
        val waypoints = mutableListOf<GpxWaypoint>()
        var metadataName: String? = null
        var primaryTrackName: String? = null

        private enum class PointKind { NONE, TRKPT, RTEPT, WPT }

        private var activePointKind = PointKind.NONE
        private var currentLat: Double? = null
        private var currentLon: Double? = null
        private var currentEleMeters: Double? = null
        private var currentWptName: String? = null
        private var currentWptDesc: String? = null
        private var inMetadata = false
        private var inTrackOrRoute = false
        private var captureText = false
        private val textBuffer = StringBuilder()

        override fun startElement(
            uri: String?,
            localName: String?,
            qName: String?,
            attributes: Attributes?
        ) {
            val tag = (localName?.takeIf { it.isNotEmpty() } ?: qName?.substringAfter(':') ?: "").lowercase()
            textBuffer.setLength(0)
            captureText = false

            when (tag) {
                "metadata" -> inMetadata = true
                "trk", "rte" -> inTrackOrRoute = true
                "trkpt" -> {
                    activePointKind = PointKind.TRKPT
                    currentLat = attributes?.getValue("lat")?.toDoubleOrNull()
                    currentLon = attributes?.getValue("lon")?.toDoubleOrNull()
                    currentEleMeters = null
                }
                "rtept" -> {
                    activePointKind = PointKind.RTEPT
                    currentLat = attributes?.getValue("lat")?.toDoubleOrNull()
                    currentLon = attributes?.getValue("lon")?.toDoubleOrNull()
                    currentEleMeters = null
                }
                "wpt" -> {
                    activePointKind = PointKind.WPT
                    currentLat = attributes?.getValue("lat")?.toDoubleOrNull()
                    currentLon = attributes?.getValue("lon")?.toDoubleOrNull()
                    currentEleMeters = null
                    currentWptName = null
                    currentWptDesc = null
                }
                "ele", "name", "desc", "cmt" -> {
                    captureText = true
                }
            }
        }

        override fun characters(ch: CharArray?, start: Int, length: Int) {
            if (captureText && ch != null && length > 0) {
                textBuffer.append(ch, start, length)
            }
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            val tag = (localName?.takeIf { it.isNotEmpty() } ?: qName?.substringAfter(':') ?: "").lowercase()
            val text = if (captureText) textBuffer.toString().trim() else ""
            captureText = false

            when (tag) {
                "ele" -> {
                    if (activePointKind != PointKind.NONE) {
                        currentEleMeters = text.toDoubleOrNull()?.takeIf { it.isFinite() }
                    }
                }
                "name" -> {
                    if (text.isNotEmpty()) {
                        when {
                            activePointKind == PointKind.WPT -> currentWptName = text
                            inTrackOrRoute && activePointKind == PointKind.NONE && primaryTrackName == null ->
                                primaryTrackName = text
                            inMetadata && metadataName == null -> metadataName = text
                        }
                    }
                }
                "desc", "cmt" -> {
                    if (text.isNotEmpty() && activePointKind == PointKind.WPT && currentWptDesc == null) {
                        currentWptDesc = text
                    }
                }
                "trkpt" -> {
                    val lat = currentLat
                    val lon = currentLon
                    if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
                        trackPoints.add(RawPoint(lat, lon, currentEleMeters))
                    }
                    activePointKind = PointKind.NONE
                }
                "rtept" -> {
                    val lat = currentLat
                    val lon = currentLon
                    if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
                        routePoints.add(RawPoint(lat, lon, currentEleMeters))
                    }
                    activePointKind = PointKind.NONE
                }
                "wpt" -> {
                    val lat = currentLat
                    val lon = currentLon
                    if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
                        waypoints.add(
                            GpxWaypoint(
                                lat = lat,
                                lon = lon,
                                name = currentWptName ?: "Waypoint ${waypoints.size + 1}",
                                description = currentWptDesc
                            )
                        )
                    }
                    activePointKind = PointKind.NONE
                }
                "trk", "rte" -> inTrackOrRoute = false
                "metadata" -> inMetadata = false
            }
        }
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else {
                        null
                    }
                }
        }.getOrNull()
    }
}
