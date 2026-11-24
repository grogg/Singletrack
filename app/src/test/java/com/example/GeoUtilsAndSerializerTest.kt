package com.example

import com.example.data.bluetooth.CscMeasurementParser
import com.example.data.bluetooth.CscParserState
import com.example.data.bluetooth.HrMeasurementParser
import com.example.data.export.GpxExporter
import com.example.data.export.GpxTrackParser
import com.example.data.local.GeometrySerializer
import com.example.data.remote.OverpassClient
import com.example.data.repository.GeoUtils
import com.example.data.repository.GpxNavigationEngine
import com.example.data.repository.MapCameraState
import com.example.data.repository.SampledRoutePoint
import com.example.data.repository.WebMercatorUtils
import com.example.data.remote.OverpassCenter
import com.example.data.remote.OverpassElement
import com.example.domain.model.ElevationPoint
import com.example.domain.model.GeoPoint
import com.example.domain.model.GpxNavigationState
import com.example.domain.model.GpxRouteStatus
import com.example.domain.model.MeasurementUnit
import com.example.domain.model.PhysicalEffortRating
import com.example.domain.model.RecordedRide
import com.example.domain.model.RideSample
import com.example.domain.model.TrailDifficulty
import com.example.domain.model.TrailElevationProfile
import com.example.ui.components.FormatUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun geometrySerializer_roundTripsPointsAndRideSamplesIncludingCadenceAndLegacyFormat() {
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
            RideSample(38.57331, -109.54981, 1225.4, 5.42f, 1700000000000L, cadenceRpm = 84, heartRateBpm = 148),
            RideSample(38.58120, -109.54110, null, null, 1700000010000L, cadenceRpm = null, heartRateBpm = 152)
        )
        val decodedSamples = GeometrySerializer.decodeSamples(GeometrySerializer.encodeSamples(samples))
        assertEquals(2, decodedSamples.size)
        assertEquals(1225.4, decodedSamples[0].altitudeMeters ?: 0.0, 0.1)
        assertEquals(5.42f, decodedSamples[0].speedMps ?: 0f, 0.01f)
        assertEquals(84, decodedSamples[0].cadenceRpm)
        assertEquals(148, decodedSamples[0].heartRateBpm)
        assertNull(decodedSamples[1].cadenceRpm)
        assertEquals(152, decodedSamples[1].heartRateBpm)

        // Legacy 5-column and 6-column samples remain compatible
        val legacyRaw = "38.57331,-109.54981,1225.4,5.42,1700000000000"
        val legacyDecoded = GeometrySerializer.decodeSamples(legacyRaw)
        assertEquals(1, legacyDecoded.size)
        assertEquals(1225.4, legacyDecoded[0].altitudeMeters ?: 0.0, 0.1)
        assertNull(legacyDecoded[0].cadenceRpm)
        assertNull(legacyDecoded[0].heartRateBpm)

        val legacy6Raw = "38.57331,-109.54981,1225.4,5.42,1700000000000,90"
        val legacy6Decoded = GeometrySerializer.decodeSamples(legacy6Raw)
        assertEquals(1, legacy6Decoded.size)
        assertEquals(90, legacy6Decoded[0].cadenceRpm)
        assertNull(legacy6Decoded[0].heartRateBpm)
    }

    @Test
    fun cscMeasurementParser_parsesWheelSpeedDistanceCrankCadenceAndRollover() {
        // Combo packet: flags=0x03 (wheel + crank)
        // Packet 1: wheelRevs=100, wheelTime=65000, crankRevs=65534, crankTime=65000
        fun comboPacket(wheelRevs: Long, wheelTime: Int, crankRevs: Int, crankTime: Int): ByteArray {
            return byteArrayOf(
                0x03,
                (wheelRevs and 0xFF).toByte(),
                ((wheelRevs shr 8) and 0xFF).toByte(),
                ((wheelRevs shr 16) and 0xFF).toByte(),
                ((wheelRevs shr 24) and 0xFF).toByte(),
                (wheelTime and 0xFF).toByte(),
                ((wheelTime shr 8) and 0xFF).toByte(),
                (crankRevs and 0xFF).toByte(),
                ((crankRevs shr 8) and 0xFF).toByte(),
                (crankTime and 0xFF).toByte(),
                ((crankTime shr 8) and 0xFF).toByte()
            )
        }

        val first = CscMeasurementParser.parse(
            bytes = comboPacket(100L, 65000, 65534, 65000),
            previousState = CscParserState(),
            wheelCircumferenceMm = 2000 // 2.0 m per rev
        )
        assertNotNull(first)
        assertNull(first!!.speedMps)
        assertEquals(0.0, first.incrementalDistanceMeters, 0.001)
        assertNull(first.cadenceRpm)

        // Second packet 2.0s later (2048 ticks -> 65000 + 2048 wraps around 65536 to 1512)
        // +5 wheel revs = 10m in 2s = 5.0 m/s
        // +3 crank revs (65534 + 3 wraps around 65536 to 1) in 2s = 90 rpm
        val second = CscMeasurementParser.parse(
            bytes = comboPacket(105L, 1512, 1, 1512),
            previousState = first.nextState,
            wheelCircumferenceMm = 2000
        )
        assertNotNull(second)
        assertTrue(second!!.wheelDataPresent)
        assertTrue(second.crankDataPresent)
        assertEquals(5.0, second.speedMps ?: 0.0, 0.05)
        assertEquals(10.0, second.incrementalDistanceMeters, 0.01)
        assertEquals(90, second.cadenceRpm)
    }

    @Test
    fun hrMeasurementParser_parsesUint8Uint16ContactAndFiltersInvalid() {
        // Flags = 0x06 -> UINT8 format, sensor contact supported & detected; BPM = 154
        val uint8Packet = byteArrayOf(0x06, 154.toByte())
        val parsed8 = HrMeasurementParser.parse(uint8Packet)
        assertNotNull(parsed8)
        assertEquals(154, parsed8!!.heartRateBpm)
        assertTrue(parsed8.contactSupported)
        assertTrue(parsed8.contactDetected)

        // Flags = 0x05 -> UINT16 LE format, sensor contact supported & not detected; BPM = 182 (0x00B6 -> 0xB6, 0x00)
        val uint16Packet = byteArrayOf(0x05, 0xB6.toByte(), 0x00)
        val parsed16 = HrMeasurementParser.parse(uint16Packet)
        assertNotNull(parsed16)
        assertEquals(182, parsed16!!.heartRateBpm)
        assertTrue(parsed16.contactSupported)
        assertFalse(parsed16.contactDetected)

        // Invalid / out-of-range packets
        assertNull(HrMeasurementParser.parse(byteArrayOf()))
        assertNull(HrMeasurementParser.parse(byteArrayOf(0x00, 0x00)))
        assertNull(HrMeasurementParser.parse(byteArrayOf(0x01, 0x90.toByte())))
    }

    @Test
    fun gpxExporter_buildsValidGpx11WithCadenceAndHeartRateAndFallbackRoutePoints() {
        val rideWithSamples = RecordedRide(
            id = "ride-1",
            title = "Whole Enchilada & Loop",
            trailName = "Whole Enchilada",
            startTimeMillis = 1700000000000L,
            endTimeMillis = 1700000060000L,
            durationSeconds = 60L,
            distanceMiles = 1.25,
            avgSpeedMph = 12.5,
            maxSpeedMph = 18.0,
            elevationGainFeet = 320.0,
            routePoints = listOf(GeoPoint(38.5733, -109.5498), GeoPoint(38.5800, -109.5400)),
            samples = listOf(
                RideSample(38.5733, -109.5498, 1250.5, 5.2f, 1700000000000L, cadenceRpm = 88, heartRateBpm = 146),
                RideSample(38.5800, -109.5400, 1275.0, 6.1f, 1700000060000L, cadenceRpm = null, heartRateBpm = 158)
            ),
            avgCadenceRpm = 88,
            maxCadenceRpm = 94,
            sensorName = "Garmin CSC 2",
            avgHeartRateBpm = 152,
            maxHeartRateBpm = 162,
            hrSensorName = "Polar H10"
        )

        val xml = GpxExporter.buildGpxXml(rideWithSamples)
        assertTrue(xml.contains("<gpx version=\"1.1\""))
        assertTrue(xml.contains("<name>Whole Enchilada &amp; Loop</name>"))
        assertTrue(xml.contains("<trkpt lat=\"38.573300\" lon=\"-109.549800\">"))
        assertTrue(xml.contains("<ele>1250.5</ele>"))
        assertTrue(xml.contains("<gpxtpx:hr>146</gpxtpx:hr>"))
        assertTrue(xml.contains("<gpxtpx:cad>88</gpxtpx:cad>"))
        assertTrue(xml.contains("<gpxtpx:hr>158</gpxtpx:hr>"))
        assertTrue(xml.contains("Avg HR: 152 bpm"))
        assertTrue(xml.contains("Garmin CSC 2"))
        assertTrue(xml.contains("Polar H10"))

        // Fallback when samples list is empty uses routePoints
        val fallbackRide = rideWithSamples.copy(samples = emptyList(), avgCadenceRpm = null, avgHeartRateBpm = null)
        val fallbackXml = GpxExporter.buildGpxXml(fallbackRide)
        assertTrue(fallbackXml.contains("<trkpt lat=\"38.580000\" lon=\"-109.540000\">"))
        assertFalse(fallbackXml.contains("<gpxtpx:cad>"))
        assertFalse(fallbackXml.contains("<gpxtpx:hr>"))
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

    @Test
    fun webMercatorUtils_roundTripsAndComputesVisibleTiles() {
        val lat = 38.57331
        val lon = -109.51944
        val zoom = 12.0
        val tx = WebMercatorUtils.lonToTileX(lon, zoom)
        val ty = WebMercatorUtils.latToTileY(lat, zoom)

        assertEquals(lon, WebMercatorUtils.tileXToLon(tx, zoom), 0.0001)
        assertEquals(lat, WebMercatorUtils.tileYToLat(ty, zoom), 0.0001)

        val visible = WebMercatorUtils.computeVisibleTiles(
            centerLat = lat,
            centerLon = lon,
            zoom = 12.0f,
            tileZoom = 12,
            baseTilePx = 256f,
            viewportWidth = 800f,
            viewportHeight = 600f
        )
        assertTrue(visible.isNotEmpty())
        assertTrue(visible.all { it.z == 12 && it.x in 0 until 4096 && it.y in 0 until 4096 })

        val baseState = MapCameraState(centerLat = lat, centerLon = lon, zoom = 12.0f)
        val zoomed = WebMercatorUtils.applyPinchPanZoom(
            current = baseState,
            viewportWidth = 800f,
            viewportHeight = 600f,
            centroidX = 400f,
            centroidY = 300f,
            panX = 0f,
            panY = 0f,
            gestureZoom = 2.0f,
            baseTilePx = 256f
        )
        assertEquals(13.0f, zoomed.zoom, 0.001f)
        assertEquals(lat, zoomed.centerLat, 0.001)
        assertEquals(lon, zoomed.centerLon, 0.001)

        val projectedCenterX = WebMercatorUtils.projectX(lon, zoom, tx, 800f, 256f)
        val projectedCenterY = WebMercatorUtils.projectY(lat, zoom, ty, 600f, 256f)
        assertEquals(400f, projectedCenterX, 0.01f)
        assertEquals(300f, projectedCenterY, 0.01f)

        val cam = WebMercatorUtils.computeBoundingCamera(
            listOf(GeoPoint(38.57, -109.55), GeoPoint(38.58, -109.54))
        )
        assertEquals(38.575, cam.centerLat, 0.0001)
        assertEquals(-109.545, cam.centerLon, 0.0001)
        assertTrue(cam.zoom in 12.0f..15.0f)
    }

    @Test
    fun overpassEndpoints_useEuMirrorsAndExcludeRu() {
        assertTrue(OverpassClient.ENDPOINTS.isNotEmpty())
        assertTrue(OverpassClient.ENDPOINTS.contains("https://overpass-api.de/api/interpreter"))
        assertTrue(OverpassClient.ENDPOINTS.all { it.startsWith("https://") })
        assertFalse(OverpassClient.ENDPOINTS.any { it.contains(".ru", ignoreCase = true) })
    }

    @Test
    fun gpxTrackParser_parsesTracksRoutesWaypointsAndReverses() {
        val sampleGpx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="TestSuite">
              <metadata><name>Meta Fallback</name></metadata>
              <wpt lat="38.5720" lon="-109.5500">
                <name>Trailhead Water</name>
                <desc>Spigot near kiosk</desc>
              </wpt>
              <trk>
                <name>Porcupine &amp; Rim</name>
                <trkseg>
                  <trkpt lat="38.5700" lon="-109.5500"><ele>1200.0</ele></trkpt>
                  <trkpt lat="38.5740" lon="-109.5500"><ele>1250.0</ele></trkpt>
                  <trkpt lat="38.5740" lon="-109.5440"><ele>1220.0</ele></trkpt>
                </trkseg>
              </trk>
            </gpx>
        """.trimIndent()

        val track = GpxTrackParser.parseFromXml(sampleGpx, "porcupine.gpx").getOrThrow()
        assertEquals("Porcupine & Rim", track.name)
        assertEquals("porcupine.gpx", track.fileName)
        assertEquals(3, track.points.size)
        assertEquals(1, track.waypoints.size)
        assertEquals("Trailhead Water", track.waypoints.first().name)
        assertTrue(track.totalDistanceMiles > 0.5)
        assertTrue(track.elevationGainFeet > 150.0)
        assertFalse(track.isReversed)

        val reversed = GpxTrackParser.reverseTrack(track)
        assertTrue(reversed.isReversed)
        assertEquals(track.points.last().lat, reversed.points.first().lat, 0.00001)
        assertEquals(track.totalDistanceMiles, reversed.totalDistanceMiles, 0.001)

        // Route points (<rte>/<rtept>) fallback
        val routeGpx = """
            <gpx version="1.1">
              <rte>
                <name>Route Loop</name>
                <rtept lat="38.5700" lon="-109.5500"/>
                <rtept lat="38.5750" lon="-109.5480"/>
              </rte>
            </gpx>
        """.trimIndent()
        val routeTrack = GpxTrackParser.parseFromXml(routeGpx, "loop.gpx").getOrThrow()
        assertEquals("Route Loop", routeTrack.name)
        assertEquals(2, routeTrack.points.size)
    }

    @Test
    fun gpxNavigationEngine_computesOnTrackOffRouteTurnCueFinishAndNonFiniteGuard() {
        // North 0.3 mi -> turn right East 0.3 mi
        val track = GpxTrackParser.buildTrack(
            id = "nav-1",
            name = "L-Bend Singletrack",
            fileName = "lbend.gpx",
            rawPoints = listOf(
                Triple(38.5700, -109.5500, 1200.0),
                Triple(38.5743, -109.5500, 1210.0),
                Triple(38.5743, -109.5444, 1215.0)
            )
        )
        val idle = GpxNavigationEngine.idleState(track)
        assertEquals(GpxRouteStatus.IDLE, idle.routeStatus)
        assertEquals(track.totalDistanceMiles, idle.distanceRemainingMiles, 0.001)

        // 1. On track moving north approaching right turn
        val onTrack = GpxNavigationEngine.updateNavigationProgress(
            previousState = idle.copy(isNavigating = true),
            track = track,
            userLat = 38.5730,
            userLon = -109.5500
        )
        assertEquals(GpxRouteStatus.ON_TRACK, onTrack.routeStatus)
        assertTrue(onTrack.distanceCompletedMiles > 0.15)
        assertTrue(onTrack.distanceRemainingMiles > 0.25)
        assertTrue(onTrack.turnInstruction.contains("right", ignoreCase = true))

        // 2. Off route (> 180 ft west of track)
        val offRoute = GpxNavigationEngine.updateNavigationProgress(
            previousState = onTrack,
            track = track,
            userLat = 38.5720,
            userLon = -109.5530
        )
        assertEquals(GpxRouteStatus.OFF_ROUTE, offRoute.routeStatus)
        assertTrue((offRoute.offRouteFeet ?: 0.0) > 500.0)
        assertTrue(offRoute.turnInstruction.contains("Return", ignoreCase = true))

        // 3. Finished near end point
        val finished = GpxNavigationEngine.updateNavigationProgress(
            previousState = onTrack,
            track = track,
            userLat = 38.5743,
            userLon = -109.5444
        )
        assertEquals(GpxRouteStatus.FINISHED, finished.routeStatus)
        assertEquals(1.0f, finished.progressFraction, 0.01f)

        // 4. NaN coordinates safely return previousState
        val nanGuarded = GpxNavigationEngine.updateNavigationProgress(
            previousState = onTrack,
            track = track,
            userLat = Double.NaN,
            userLon = -109.5500
        )
        assertEquals(onTrack, nanGuarded)

        // 5. Large 5000-point track stress test stays fast
        val densePoints = (0 until 5000).map { idx ->
            Triple(38.5700 + idx * 0.00001, -109.5500 + idx * 0.00001, 1200.0)
        }
        val denseTrack = GpxTrackParser.buildTrack("dense", "Dense", "dense.gpx", densePoints)
        val denseState = GpxNavigationEngine.updateNavigationProgress(
            previousState = GpxNavigationState(track = denseTrack, isNavigating = true),
            track = denseTrack,
            userLat = 38.5950,
            userLon = -109.5250
        )
        assertEquals(GpxRouteStatus.ON_TRACK, denseState.routeStatus)
    }
}
