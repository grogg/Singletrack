package com.example

import com.example.data.local.GeometrySerializer
import com.example.data.repository.GeoUtils
import com.example.data.repository.SampledRoutePoint
import com.example.data.remote.OverpassCenter
import com.example.data.remote.OverpassElement
import com.example.domain.model.ElevationPoint
import com.example.domain.model.GeoPoint
import com.example.domain.model.MeasurementUnit
import com.example.domain.model.PhysicalEffortRating
import com.example.domain.model.RideSample
import com.example.domain.model.TrailDifficulty
import com.example.domain.model.TrailElevationProfile
import com.example.ui.components.FormatUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
                "distance" to "10.5 mi",
                "ascent" to "450 m"
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
        assertEquals(1476, trail.elevationGainFeet)
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

    @Test
    fun parseElevationFeet_handlesMetersFeetAndInvalid() {
        assertEquals(1476, GeoUtils.parseElevationFeet("450"))
        assertEquals(1476, GeoUtils.parseElevationFeet("450 m"))
        assertEquals(1200, GeoUtils.parseElevationFeet("1,200 ft"))
        assertNull(GeoUtils.parseElevationFeet(""))
        assertNull(GeoUtils.parseElevationFeet("-20"))
    }

    @Test
    fun sampleRouteWithDistances_andBuildElevationProfile_isolatesSegmentsAndFiltersJitter() {
        val seg1 = listOf(
            GeoPoint(38.5700, -109.5500),
            GeoPoint(38.5750, -109.5500)
        )
        val seg2 = listOf(
            GeoPoint(38.5850, -109.5400),
            GeoPoint(38.5900, -109.5400)
        )
        val sampled = GeoUtils.sampleRouteWithDistances(listOf(seg1, seg2), maxSamples = 10)
        assertTrue(sampled.size >= 4)
        assertTrue(sampled.zipWithNext().all { (a, b) -> b.distanceMiles >= a.distanceMiles })

        // Manually test segment isolation & 5 ft jitter threshold
        val syntheticSamples = listOf(
            SampledRoutePoint(0, 0.0, GeoPoint(38.57, -109.55)),
            SampledRoutePoint(0, 0.25, GeoPoint(38.572, -109.55)), // +1m (~3.28ft < 5ft threshold)
            SampledRoutePoint(0, 0.50, GeoPoint(38.575, -109.55)), // +100m from start (~328ft gain)
            SampledRoutePoint(1, 0.75, GeoPoint(38.585, -109.54)), // new segment starts at 2000m -> jump ignored
            SampledRoutePoint(1, 1.00, GeoPoint(38.590, -109.54))  // +100m climb (~328ft gain)
        )
        val elevationsMeters = listOf(1000.0, 1001.0, 1100.0, 2000.0, 2100.0)
        val profile = GeoUtils.buildElevationProfile(syntheticSamples, elevationsMeters, "Test DEM")
        assertNotNull(profile)
        // Total climb should be ~656 ft (two +100m climbs), NOT including the +900m segment discontinuity
        assertEquals(656, profile!!.elevationGainFeet)
        assertEquals(0, profile.elevationLossFeet)
        assertEquals(3281, profile.minElevationFeet)
        assertEquals(6890, profile.maxElevationFeet)
        assertTrue(profile.maxGradePercent in 20..30)
        assertEquals(PhysicalEffortRating.GRUELING, profile.effortRating)

        // Serializer round-trip for TrailElevationProfile
        val encoded = GeometrySerializer.encodeElevationProfile(profile)
        val decoded = GeometrySerializer.decodeElevationProfile(encoded)
        assertNotNull(decoded)
        assertEquals(profile.elevationGainFeet, decoded!!.elevationGainFeet)
        assertEquals(profile.elevationLossFeet, decoded.elevationLossFeet)
        assertEquals(profile.minElevationFeet, decoded.minElevationFeet)
        assertEquals(profile.maxElevationFeet, decoded.maxElevationFeet)
        assertEquals(profile.maxGradePercent, decoded.maxGradePercent)
        assertEquals(profile.points.size, decoded.points.size)
        assertEquals("Cached Offline Elevation Profile", decoded.sourceLabel)
    }

    @Test
    fun effortRating_thresholdsClassifyCorrectly() {
        val gentle = TrailElevationProfile(
            elevationGainFeet = 120,
            elevationLossFeet = 100,
            minElevationFeet = 4000,
            maxElevationFeet = 4120,
            maxGradePercent = 4,
            points = listOf(ElevationPoint(0.0, 4000.0), ElevationPoint(3.0, 4120.0)),
            sourceLabel = "DEM"
        )
        assertEquals(PhysicalEffortRating.GENTLE, gentle.effortRating)

        val moderate = gentle.copy(elevationGainFeet = 450, maxGradePercent = 8)
        assertEquals(PhysicalEffortRating.MODERATE, moderate.effortRating)

        val strenuous = gentle.copy(elevationGainFeet = 900, maxGradePercent = 14)
        assertEquals(PhysicalEffortRating.STRENUOUS, strenuous.effortRating)

        val grueling = gentle.copy(elevationGainFeet = 2600, maxGradePercent = 22)
        assertEquals(PhysicalEffortRating.GRUELING, grueling.effortRating)
    }

    @Test
    fun formatUtils_convertsImperialAndMetricCorrectly() {
        assertEquals("10.0 mi", FormatUtils.formatDistance(10.0, MeasurementUnit.IMPERIAL))
        assertEquals("16.1 km", FormatUtils.formatDistance(10.0, MeasurementUnit.METRIC))
        assertEquals("12.4 mph", FormatUtils.formatSpeed(12.4, MeasurementUnit.IMPERIAL))
        assertEquals("20.0 km/h", FormatUtils.formatSpeed(12.4, MeasurementUnit.METRIC))
        assertEquals("+1,000 ft", FormatUtils.formatElevation(1000, MeasurementUnit.IMPERIAL, prefix = "+"))
        assertEquals("+305 m", FormatUtils.formatElevation(1000, MeasurementUnit.METRIC, prefix = "+"))
        assertEquals("20 mi", FormatUtils.formatRadiusShort(20.0, MeasurementUnit.IMPERIAL))
        assertEquals("32 km", FormatUtils.formatRadiusShort(20.0, MeasurementUnit.METRIC))
    }
}
