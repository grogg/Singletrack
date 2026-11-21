package com.example

import com.example.data.local.GeometrySerializer
import com.example.data.repository.GeoUtils
import com.example.data.remote.OverpassCenter
import com.example.data.remote.OverpassElement
import com.example.domain.model.GeoPoint
import com.example.domain.model.RideSample
import com.example.domain.model.TrailDifficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoUtilsAndSerializerTest {

    @Test
    fun parseAndGroupElements_filtersBeyondFiftyMilesAndDeduplicates() {
        val moabLat = 38.5733
        val moabLon = -109.5498

        val nearbyWay = OverpassElement(
            type = "way",
            id = 101L,
            center = OverpassCenter(38.5900, -109.5200),
            tags = mapOf(
                "name" to "Slickrock Bike Trail",
                "mtb:scale" to "2",
                "surface" to "rock",
                "distance" to "10.5 mi"
            )
        )
        val duplicateSegment = OverpassElement(
            type = "way",
            id = 102L,
            center = OverpassCenter(38.5950, -109.5150),
            tags = mapOf(
                "name" to "Slickrock Bike Trail",
                "mtb:scale" to "2"
            )
        )
        val tooFarWay = OverpassElement(
            type = "way",
            id = 200L,
            center = OverpassCenter(39.0100, -109.5498), // ~30.1 miles north (> 20 miles)
            tags = mapOf(
                "name" to "Far Away Trail",
                "mtb:scale:imba" to "green"
            )
        )

        val parsed = GeoUtils.parseAndGroupElements(
            elements = listOf(nearbyWay, duplicateSegment, tooFarWay),
            originLat = moabLat,
            originLon = moabLon
        )

        assertEquals(1, parsed.size)
        val trail = parsed.first()
        assertEquals("Slickrock Bike Trail", trail.name)
        assertEquals(TrailDifficulty.ADVANCED, trail.difficulty)
        assertEquals("Rock", trail.surface)
        assertTrue(trail.distanceFromUserMiles <= 20.0)
        assertEquals(10.5, trail.lengthMiles ?: 0.0, 0.01)
    }

    @Test
    fun geometrySerializer_roundTripsPointsAndRideSamples() {
        val points = listOf(
            GeoPoint(38.57331, -109.54981),
            GeoPoint(38.58120, -109.54110)
        )
        val decodedPoints = GeometrySerializer.decode(GeometrySerializer.encode(points))
        assertEquals(2, decodedPoints.size)
        assertEquals(38.57331, decodedPoints[0].lat, 0.0001)

        val seg2 = listOf(
            GeoPoint(38.59000, -109.53000),
            GeoPoint(38.59500, -109.52500)
        )
        val decodedSegments = GeometrySerializer.decodeSegments(
            GeometrySerializer.encodeSegments(listOf(points, seg2))
        )
        assertEquals(2, decodedSegments.size)
        assertEquals(38.59500, decodedSegments[1][1].lat, 0.0001)

        val samples = listOf(
            RideSample(38.57331, -109.54981, 1225.4, 5.42f, 1700000000000L),
            RideSample(38.58120, -109.54110, null, null, 1700000010000L)
        )
        val decodedSamples = GeometrySerializer.decodeSamples(GeometrySerializer.encodeSamples(samples))
        assertEquals(2, decodedSamples.size)
        assertEquals(1225.4, decodedSamples[0].altitudeMeters ?: 0.0, 0.1)
        assertEquals(5.42f, decodedSamples[0].speedMps ?: 0f, 0.01f)
    }
}
