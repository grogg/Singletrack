package com.example.data.local

import com.example.domain.model.GeoPoint
import com.example.domain.model.RideSample
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
            String.format(
                Locale.US,
                "%.5f,%.5f,%s,%s,%d",
                sample.lat,
                sample.lon,
                alt,
                spd,
                sample.timestampMillis
            )
        }
    }

    fun decodeSamples(raw: String): List<RideSample> {
        if (raw.isBlank()) return emptyList()
        return raw.split(";").mapNotNull { token ->
            val parts = token.split(",")
            if (parts.size == 5) {
                val lat = parts[0].toDoubleOrNull() ?: return@mapNotNull null
                val lon = parts[1].toDoubleOrNull() ?: return@mapNotNull null
                val alt = parts[2].toDoubleOrNull()
                val spd = parts[3].toFloatOrNull()
                val ts = parts[4].toLongOrNull() ?: return@mapNotNull null
                RideSample(
                    lat = lat,
                    lon = lon,
                    altitudeMeters = alt,
                    speedMps = spd,
                    timestampMillis = ts
                )
            } else {
                null
            }
        }
    }
}
