package com.example.data.local

import com.example.domain.model.ElevationPoint
import com.example.domain.model.GeoPoint
import com.example.domain.model.RideSample
import com.example.domain.model.TrailElevationProfile
import java.util.Locale

object GeometrySerializer {
    fun encode(points: List<GeoPoint>): String {
        if (points.isEmpty()) return ""
        return points.joinToString(separator = ";") { point ->
            String.format(Locale.US, "%.5f,%.5f", point.lat, point.lon)
        }
    }

    fun decode(raw: String): List<GeoPoint> {
        if (raw.isBlank()) return emptyList()
        val firstSegment = raw.substringBefore("|")
        return decodePointList(firstSegment)
    }

    fun encodeSegments(segments: List<List<GeoPoint>>): String {
        val nonEmpty = segments.filter { it.size >= 2 }
        if (nonEmpty.isEmpty()) return ""
        return nonEmpty.joinToString(separator = "|") { seg -> encode(seg) }
    }

    fun decodeSegments(raw: String): List<List<GeoPoint>> {
        if (raw.isBlank()) return emptyList()
        return raw.split("|")
            .map { decodePointList(it) }
            .filter { it.size >= 2 }
    }

    private fun decodePointList(raw: String): List<GeoPoint> {
        if (raw.isBlank()) return emptyList()
        return raw.split(";").mapNotNull { token ->
            val parts = token.split(",")
            if (parts.size == 2) {
                val lat = parts[0].toDoubleOrNull()
                val lon = parts[1].toDoubleOrNull()
                if (lat != null && lon != null) GeoPoint(lat, lon) else null
            } else {
                null
            }
        }
    }

    fun encodeSamples(samples: List<RideSample>): String {
        if (samples.isEmpty()) return ""
        return samples.joinToString(separator = ";") { sample ->
            val alt = sample.altitudeMeters?.let { String.format(Locale.US, "%.1f", it) } ?: ""
            val spd = sample.speedMps?.let { String.format(Locale.US, "%.2f", it) } ?: ""
            val cad = sample.cadenceRpm?.toString() ?: ""
            val hr = sample.heartRateBpm?.toString() ?: ""
            String.format(
                Locale.US,
                "%.5f,%.5f,%s,%s,%d,%s,%s",
                sample.lat,
                sample.lon,
                alt,
                spd,
                sample.timestampMillis,
                cad,
                hr
            )
        }
    }

    fun decodeSamples(raw: String): List<RideSample> {
        if (raw.isBlank()) return emptyList()
        return raw.split(";").mapNotNull { token ->
            val parts = token.split(",")
            if (parts.size >= 5) {
                val lat = parts[0].toDoubleOrNull() ?: return@mapNotNull null
                val lon = parts[1].toDoubleOrNull() ?: return@mapNotNull null
                val alt = parts[2].toDoubleOrNull()
                val spd = parts[3].toFloatOrNull()
                val ts = parts[4].toLongOrNull() ?: return@mapNotNull null
                val cad = parts.getOrNull(5)?.toIntOrNull()
                val hr = parts.getOrNull(6)?.toIntOrNull()
                RideSample(
                    lat = lat,
                    lon = lon,
                    altitudeMeters = alt,
                    speedMps = spd,
                    timestampMillis = ts,
                    cadenceRpm = cad,
                    heartRateBpm = hr
                )
            } else {
                null
            }
        }
    }

    fun encodeElevationProfile(profile: TrailElevationProfile): String {
        if (profile.points.isEmpty()) return ""
        val header = "${profile.elevationGainFeet},${profile.elevationLossFeet}," +
            "${profile.minElevationFeet},${profile.maxElevationFeet},${profile.maxGradePercent}"
        val pts = profile.points.joinToString(separator = ";") { pt ->
            String.format(Locale.US, "%.4f,%.1f", pt.distanceMiles, pt.elevationFeet)
        }
        return "$header|$pts"
    }

    fun decodeElevationProfile(raw: String): TrailElevationProfile? {
        if (raw.isBlank()) return null
        val parts = raw.split("|", limit = 2)
        if (parts.size != 2) return null
        val header = parts[0].split(",")
        if (header.size != 5) return null
        val gain = header[0].toIntOrNull() ?: return null
        val loss = header[1].toIntOrNull() ?: return null
        val minFt = header[2].toIntOrNull() ?: return null
        val maxFt = header[3].toIntOrNull() ?: return null
        val maxGrade = header[4].toIntOrNull() ?: return null
        val points = parts[1].split(";").mapNotNull { token ->
            val coords = token.split(",")
            if (coords.size == 2) {
                val dist = coords[0].toDoubleOrNull()
                val elev = coords[1].toDoubleOrNull()
                if (dist != null && elev != null) ElevationPoint(dist, elev) else null
            } else {
                null
            }
        }
        if (points.size < 2) return null
        return TrailElevationProfile(
            elevationGainFeet = gain,
            elevationLossFeet = loss,
            minElevationFeet = minFt,
            maxElevationFeet = maxFt,
            maxGradePercent = maxGrade,
            points = points,
            sourceLabel = "Cached Offline Elevation Profile"
        )
    }
}
