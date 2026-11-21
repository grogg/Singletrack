package com.example.data.repository

import com.example.data.remote.OverpassElement
import com.example.domain.model.GeoPoint
import com.example.domain.model.Trail
import com.example.domain.model.TrailDifficulty
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object GeoUtils {
    const val EARTH_RADIUS_MILES = 3958.7613
    const val RADIUS_TWENTY_MILES = 20.0
    const val RADIUS_TWENTY_MILES_METERS = 32187

    fun haversineMiles(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)

        val a = sin(dLat / 2).pow(2) +
            cos(rLat1) * cos(rLat2) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_MILES * c
    }

    fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)

        val y = sin(dLon) * cos(rLat2)
        val x = cos(rLat1) * sin(rLat2) - sin(rLat1) * cos(rLat2) * cos(dLon)
        val deg = Math.toDegrees(atan2(y, x))
        return (deg + 360.0) % 360.0
    }

    fun cardinalDirection(degrees: Double): String {
        val dirs = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        val index = (((degrees + 22.5) % 360.0) / 45.0).toInt()
        return dirs[index.coerceIn(0, dirs.lastIndex)]
    }

    fun polylineLengthMiles(points: List<GeoPoint>): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (i in 0 until points.lastIndex) {
            total += haversineMiles(
                points[i].lat,
                points[i].lon,
                points[i + 1].lat,
                points[i + 1].lon
            )
        }
        return total
    }

    fun extractGeometrySegments(element: OverpassElement): List<List<GeoPoint>> {
        val direct = element.geometry?.map { GeoPoint(it.lat, it.lon) }.orEmpty()
        if (direct.size >= 2) return listOf(direct)

        return element.members
            ?.mapNotNull { member ->
                val pts = member.geometry?.map { GeoPoint(it.lat, it.lon) }.orEmpty()
                if (pts.size >= 2) pts else null
            }
            .orEmpty()
    }

    fun extractGeometry(element: OverpassElement): List<GeoPoint> {
        val segments = extractGeometrySegments(element)
        return segments.flatten()
    }

    fun parseDifficulty(tags: Map<String, String>): Pair<TrailDifficulty, String?> {
        val imba = tags["mtb:scale:imba"]?.trim()?.lowercase(Locale.US)
        if (!imba.isNullOrBlank()) {
            val mapped = when {
                imba in listOf("0", "green", "easiest") -> TrailDifficulty.EASY
                imba in listOf("1", "blue", "more_difficult") -> TrailDifficulty.INTERMEDIATE
                imba in listOf("2", "3", "black", "black_diamond", "very_difficult") -> TrailDifficulty.ADVANCED
                imba in listOf("4", "double_black", "double_black_diamond", "pro", "extremely_difficult") -> TrailDifficulty.EXPERT
                else -> null
            }
            if (mapped != null) return mapped to "IMBA: $imba"
        }

        val sts = tags["mtb:scale"]?.trim()?.lowercase(Locale.US)
        if (!sts.isNullOrBlank()) {
            val digit = sts.firstOrNull { it.isDigit() }?.digitToIntOrNull()
            val mapped = when (digit) {
                0 -> TrailDifficulty.EASY
                1 -> TrailDifficulty.INTERMEDIATE
                2, 3 -> TrailDifficulty.ADVANCED
                4, 5, 6 -> TrailDifficulty.EXPERT
                else -> null
            }
            if (mapped != null) return mapped to "STS S$sts"
        }

        val genericDiff = tags["difficulty"]?.trim()?.lowercase(Locale.US)
        if (!genericDiff.isNullOrBlank()) {
            val mapped = when {
                genericDiff.contains("easy") || genericDiff.contains("green") -> TrailDifficulty.EASY
                genericDiff.contains("intermediate") || genericDiff.contains("moderate") || genericDiff.contains("blue") -> TrailDifficulty.INTERMEDIATE
                genericDiff.contains("double") || genericDiff.contains("expert") -> TrailDifficulty.EXPERT
                genericDiff.contains("hard") || genericDiff.contains("difficult") || genericDiff.contains("black") -> TrailDifficulty.ADVANCED
                else -> null
            }
            if (mapped != null) return mapped to genericDiff
        }

        return TrailDifficulty.UNRATED to null
    }

    fun parseLengthMiles(
        tags: Map<String, String>,
        segments: List<List<GeoPoint>>,
        element: OverpassElement
    ): Double? {
        val raw = tags["distance"] ?: tags["length"]
        if (!raw.isNullOrBlank()) {
            val cleaned = raw.trim().lowercase(Locale.US)
            val number = Regex("""([0-9]+(?:\.[0-9]+)?)""").find(cleaned)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
            if (number != null && number > 0.0) {
                return when {
                    cleaned.contains("mi") -> number
                    cleaned.contains("km") -> number * 0.621371
                    cleaned.endsWith("m") -> number * 0.000621371
                    else -> number * 0.621371
                }
            }
        }

        if (segments.isNotEmpty()) {
            val len = segments.sumOf { polylineLengthMiles(it) }
            if (len >= 0.05) return len
        }

        val bounds = element.bounds
        if (bounds != null) {
            val diag = haversineMiles(bounds.minLat, bounds.minLon, bounds.maxLat, bounds.maxLon)
            if (diag >= 0.1) return diag
        }
        return null
    }

    fun formatSurface(tags: Map<String, String>): String {
        val raw = tags["surface"] ?: tags["tracktype"] ?: return "Natural Trail"
        return raw.replace('_', ' ')
            .split(' ')
            .joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
            }
    }

    fun parseAndGroupElements(
        elements: List<OverpassElement>,
        originLat: Double,
        originLon: Double,
        isOfflineCached: Boolean = false
    ): List<Trail> {
        val parsed = elements.mapNotNull { el ->
            val tags = el.tags.orEmpty()
            val segments = extractGeometrySegments(el)
            val flatGeom = segments.flatten()
            val centerLat = el.center?.lat ?: el.lat ?: flatGeom.firstOrNull()?.lat ?: return@mapNotNull null
            val centerLon = el.center?.lon ?: el.lon ?: flatGeom.firstOrNull()?.lon ?: return@mapNotNull null

            val distanceMiles = haversineMiles(originLat, originLon, centerLat, centerLon)
            if (distanceMiles > RADIUS_TWENTY_MILES) return@mapNotNull null

            val rawName = tags["name"]?.trim()?.takeIf { it.isNotBlank() }
                ?: tags["ref"]?.trim()?.takeIf { it.isNotBlank() }?.let { "Trail $it" }

            val hasExplicitMtbTag = tags.containsKey("mtb:scale") ||
                tags.containsKey("mtb:scale:imba") ||
                tags["route"] == "mtb" ||
                tags.containsKey("mtb")

            if (rawName == null && !hasExplicitMtbTag) return@mapNotNull null

            val bearing = bearingDegrees(originLat, originLon, centerLat, centerLon)
            val (difficulty, rawDiff) = parseDifficulty(tags)
            val lengthMiles = parseLengthMiles(tags, segments, el)
            val displayName = rawName ?: buildFallbackName(tags, el.id)

            Trail(
                osmKey = "${el.type}/${el.id}",
                osmType = el.type,
                osmId = el.id,
                name = displayName,
                difficulty = difficulty,
                rawDifficultyTag = rawDiff,
                surface = formatSurface(tags),
                centerLat = centerLat,
                centerLon = centerLon,
                distanceFromUserMiles = distanceMiles,
                bearingDegrees = bearing,
                bearingCardinal = cardinalDirection(bearing),
                lengthMiles = lengthMiles,
                operatorName = tags["operator"],
                networkName = tags["network"] ?: tags["mtb:network"],
                description = tags["description"] ?: tags["note"] ?: tags["mtb:description"],
                oneway = tags["oneway:bicycle"] ?: tags["oneway"],
                uphillScale = tags["mtb:scale:uphill"],
                isDesignatedMtb = hasExplicitMtbTag,
                geometry = flatGeom,
                routeSegments = segments,
                isOfflineCached = isOfflineCached
            )
        }

        val result = mutableListOf<Trail>()
        val namedGroups = parsed.groupBy { it.name.lowercase(Locale.US) }

        for ((_, group) in namedGroups) {
            if (group.size == 1) {
                result.add(group.first())
                continue
            }

            val clusters = mutableListOf<MutableList<Trail>>()
            for (trail in group.sortedBy { it.distanceFromUserMiles }) {
                val targetCluster = clusters.firstOrNull { cluster ->
                    val rep = cluster.first()
                    haversineMiles(rep.centerLat, rep.centerLon, trail.centerLat, trail.centerLon) <= 4.0
                }
                if (targetCluster != null) {
                    targetCluster.add(trail)
                } else {
                    clusters.add(mutableListOf(trail))
                }
            }

            for (cluster in clusters) {
                val primary = cluster.sortedWith(
                    compareBy<Trail> { it.osmType != "relation" }
                        .thenBy { it.difficulty == TrailDifficulty.UNRATED }
                        .thenBy { it.distanceFromUserMiles }
                ).first()

                val allSegments = cluster
                    .flatMap { it.routeSegments }
                    .distinctBy { seg ->
                        val first = seg.firstOrNull()
                        val last = seg.lastOrNull()
                        "${first?.lat},${first?.lon}->${last?.lat},${last?.lon}:${seg.size}"
                    }

                val combinedLength = if (primary.osmType == "relation" && primary.lengthMiles != null) {
                    primary.lengthMiles
                } else {
                    val geomLen = allSegments.sumOf { polylineLengthMiles(it) }
                    if (geomLen >= 0.05) geomLen else cluster.mapNotNull { it.lengthMiles }.maxOrNull()
                }

                val mergedGeometry = if (primary.geometry.isNotEmpty()) {
                    primary.geometry
                } else {
                    allSegments.firstOrNull().orEmpty()
                }

                result.add(
                    primary.copy(
                        lengthMiles = combinedLength,
                        geometry = mergedGeometry,
                        routeSegments = if (allSegments.isNotEmpty()) allSegments else primary.routeSegments
                    )
                )
            }
        }

        return result.sortedBy { it.distanceFromUserMiles }
    }

    private fun buildFallbackName(tags: Map<String, String>, id: Long): String {
        val network = tags["network"]?.takeIf { it.isNotBlank() }
        val area = tags["park"] ?: tags["place"]
        return when {
            network != null -> "$network Singletrack"
            area != null -> "$area MTB Trail"
            else -> "MTB Trail #${id % 10000}"
        }
    }
}
