package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class OverpassResponse(
    @Json(name = "remark") val remark: String? = null,
    @Json(name = "elements") val elements: List<OverpassElement> = emptyList()
) {
    fun hasRuntimeError(): Boolean {
        val text = remark?.trim() ?: return false
        if (text.isEmpty()) return false
        return elements.isEmpty() ||
            text.contains("error", ignoreCase = true) ||
            text.contains("timed out", ignoreCase = true) ||
            text.contains("timeout", ignoreCase = true) ||
            text.contains("out of memory", ignoreCase = true) ||
            text.contains("runtime", ignoreCase = true)
    }
}

@JsonClass(generateAdapter = true)
data class OverpassElement(
    @Json(name = "type") val type: String,
    @Json(name = "id") val id: Long,
    @Json(name = "lat") val lat: Double? = null,
    @Json(name = "lon") val lon: Double? = null,
    @Json(name = "center") val center: OverpassCenter? = null,
    @Json(name = "bounds") val bounds: OverpassBounds? = null,
    @Json(name = "tags") val tags: Map<String, String>? = null,
    @Json(name = "geometry") val geometry: List<OverpassGeomPoint>? = null,
    @Json(name = "members") val members: List<OverpassMember>? = null
)

@JsonClass(generateAdapter = true)
data class OverpassCenter(
    @Json(name = "lat") val lat: Double,
    @Json(name = "lon") val lon: Double
)

@JsonClass(generateAdapter = true)
data class OverpassBounds(
    @Json(name = "minlat") val minLat: Double,
    @Json(name = "minlon") val minLon: Double,
    @Json(name = "maxlat") val maxLat: Double,
    @Json(name = "maxlon") val maxLon: Double
)

@JsonClass(generateAdapter = true)
data class OverpassGeomPoint(
    @Json(name = "lat") val lat: Double,
    @Json(name = "lon") val lon: Double
)

@JsonClass(generateAdapter = true)
data class OverpassMember(
    @Json(name = "type") val type: String? = null,
    @Json(name = "ref") val ref: Long? = null,
    @Json(name = "role") val role: String? = null,
    @Json(name = "geometry") val geometry: List<OverpassGeomPoint>? = null
)
