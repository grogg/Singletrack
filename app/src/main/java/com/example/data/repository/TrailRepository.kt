package com.example.data.repository

import com.example.data.local.CachedTrailEntity
import com.example.data.local.GeometrySerializer
import com.example.data.local.OfflineRegionEntity
import com.example.data.local.OfflineTrailDao
import com.example.data.remote.ElevationClient
import com.example.data.remote.NominatimClient
import com.example.data.remote.OverpassClient
import com.example.data.remote.OverpassElement
import com.example.data.remote.OverpassResponse
import com.example.domain.model.GeoPoint
import com.example.domain.model.LocationPlace
import com.example.domain.model.OfflineRegion
import com.example.domain.model.Trail
import com.example.domain.model.TrailDifficulty
import com.example.domain.model.TrailElevationProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.Locale

data class TrailScanResult(
    val trails: List<Trail>,
    val isFromOfflineCache: Boolean,
    val offlineRegionName: String?,
    val usedSingletrackFallback: Boolean
)

class TrailRepository(
    private val offlineTrailDao: OfflineTrailDao
) {
    private val elevationMemoryCache: MutableMap<String, TrailElevationProfile> =
        Collections.synchronizedMap(
            object : LinkedHashMap<String, TrailElevationProfile>(64, 0.75f, true) {
                override fun removeEldestEntry(
                    eldest: MutableMap.MutableEntry<String, TrailElevationProfile>?
                ): Boolean = size > MAX_ELEVATION_CACHE_ENTRIES
            }
        )

    fun observeOfflineRegions(): Flow<List<OfflineRegion>> {
        return offlineTrailDao.observeRegions().map { list ->
            list.map { it.toDomain() }
        }
    }

    suspend fun getOfflineRegionsOnce(): List<OfflineRegion> = withContext(Dispatchers.IO) {
        offlineTrailDao.getAllRegionsOnce().map { it.toDomain() }
    }

    suspend fun fetchTrailsNear(lat: Double, lon: Double): Result<TrailScanResult> = withContext(Dispatchers.IO) {
        try {
            // For a 20-mile radius, fetch geometry directly so trail routes overlay on the topo map
            val primaryElements = queryWithGeomFallback(
                geomQuery = buildPrimaryMtbQuery(lat, lon, includeGeometry = true),
                lightQuery = buildPrimaryMtbQuery(lat, lon, includeGeometry = false)
            )
            var trails = GeoUtils.parseAndGroupElements(primaryElements, lat, lon)
            var usedFallback = false

            if (trails.isEmpty()) {
                val fallbackElements = queryWithGeomFallback(
                    geomQuery = buildSingletrackFallbackQuery(lat, lon, includeGeometry = true),
                    lightQuery = buildSingletrackFallbackQuery(lat, lon, includeGeometry = false)
                )
                trails = GeoUtils.parseAndGroupElements(fallbackElements, lat, lon)
                usedFallback = trails.isNotEmpty()
            }

            Result.success(
                TrailScanResult(
                    trails = trails,
                    isFromOfflineCache = false,
                    offlineRegionName = null,
                    usedSingletrackFallback = usedFallback
                )
            )
        } catch (networkError: Exception) {
            val matchingRegion = findMatchingOfflineRegion(lat, lon)
            if (matchingRegion != null) {
                val cachedTrails = loadOfflineRegionTrails(matchingRegion.id, lat, lon)
                Result.success(
                    TrailScanResult(
                        trails = cachedTrails,
                        isFromOfflineCache = true,
                        offlineRegionName = matchingRegion.name,
                        usedSingletrackFallback = false
                    )
                )
            } else {
                Result.failure(networkError)
            }
        }
    }

    suspend fun fetchTrailGeometry(trail: Trail): List<GeoPoint> = withContext(Dispatchers.IO) {
        if (trail.geometry.size >= 2) return@withContext trail.geometry

        val cached = offlineTrailDao.findCachedTrailByKey(trail.osmKey)
        if (cached != null) {
            val decoded = GeometrySerializer.decode(cached.geometryEncoded)
            if (decoded.size >= 2) return@withContext decoded
        }

        try {
            val query = "[out:json][timeout:15];${trail.osmType}(${trail.osmId});out geom;"
            val response = executeOverpassWithFallback(query)
            val element = response.elements.firstOrNull() ?: return@withContext emptyList()
            GeoUtils.extractGeometry(element)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun fetchTrailElevation(trail: Trail): TrailElevationProfile? = withContext(Dispatchers.IO) {
        trail.elevationProfile?.let { return@withContext it }
        elevationMemoryCache[trail.osmKey]?.let { return@withContext it }

        val cachedEntity = offlineTrailDao.findCachedTrailByKey(trail.osmKey)
        if (cachedEntity != null && cachedEntity.elevationProfileEncoded.isNotBlank()) {
            val decoded = GeometrySerializer.decodeElevationProfile(cachedEntity.elevationProfileEncoded)
            if (decoded != null) {
                elevationMemoryCache[trail.osmKey] = decoded
                return@withContext decoded
            }
        }

        val segments = when {
            trail.routeSegments.any { it.size >= 2 } -> trail.routeSegments
            trail.geometry.size >= 2 -> listOf(trail.geometry)
            else -> emptyList()
        }
        val sampled = GeoUtils.sampleRouteWithDistances(segments, maxSamples = 36)
        if (sampled.size < 2) return@withContext null

        val profile = try {
            val lats = sampled.joinToString(",") { String.format(Locale.US, "%.5f", it.point.lat) }
            val lons = sampled.joinToString(",") { String.format(Locale.US, "%.5f", it.point.lon) }
            val resp = ElevationClient.service.getOpenMeteoElevations(latitudes = lats, longitudes = lons)
            GeoUtils.buildElevationProfile(sampled, resp.elevation, sourceLabel = "DEM Elevation Profile")
        } catch (_: Exception) {
            null
        } ?: try {
            val locations = sampled.joinToString("|") {
                String.format(Locale.US, "%.5f,%.5f", it.point.lat, it.point.lon)
            }
            val resp = ElevationClient.service.getOpenTopoDataElevations(locations = locations)
            GeoUtils.buildElevationProfile(
                sampled,
                resp.results.map { it.elevation },
                sourceLabel = "SRTM 30m Elevation Profile"
            )
        } catch (_: Exception) {
            null
        }

        if (profile != null) {
            elevationMemoryCache[trail.osmKey] = profile
            if (cachedEntity != null) {
                offlineTrailDao.updateCachedTrailElevation(
                    osmKey = trail.osmKey,
                    gainFeet = profile.elevationGainFeet,
                    profileEncoded = GeometrySerializer.encodeElevationProfile(profile)
                )
            }
        }
        profile
    }

    suspend fun downloadRegionForOffline(
        regionName: String,
        centerLat: Double,
        centerLon: Double
    ): Result<OfflineRegion> = withContext(Dispatchers.IO) {
        try {
            var hasFullGeometry = true
            var elements = try {
                val fullQuery = buildPrimaryMtbQuery(centerLat, centerLon, includeGeometry = true)
                executeOverpassWithFallback(fullQuery).elements
            } catch (_: Exception) {
                hasFullGeometry = false
                val lightQuery = buildPrimaryMtbQuery(centerLat, centerLon, includeGeometry = false)
                executeOverpassWithFallback(lightQuery).elements
            }

            if (elements.isEmpty()) {
                val fallbackQuery = buildSingletrackFallbackQuery(centerLat, centerLon, includeGeometry = true)
                elements = try {
                    executeOverpassWithFallback(fallbackQuery).elements
                } catch (_: Exception) {
                    hasFullGeometry = false
                    val lightFallback = buildSingletrackFallbackQuery(centerLat, centerLon, includeGeometry = false)
                    executeOverpassWithFallback(lightFallback).elements
                }
            }

            val trails = GeoUtils.parseAndGroupElements(
                elements = elements,
                originLat = centerLat,
                originLon = centerLon,
                isOfflineCached = true
            )

            val regionId = buildRegionId(centerLat, centerLon)
            val entities = trails.map { trail ->
                val segmentsToSave = if (trail.routeSegments.isNotEmpty()) {
                    trail.routeSegments
                } else if (trail.geometry.size >= 2) {
                    listOf(trail.geometry)
                } else {
                    emptyList()
                }
                val encodedGeom = GeometrySerializer.encodeSegments(segmentsToSave)
                val cachedProfile = elevationMemoryCache[trail.osmKey] ?: trail.elevationProfile
                CachedTrailEntity(
                    regionId = regionId,
                    osmKey = trail.osmKey,
                    osmType = trail.osmType,
                    osmId = trail.osmId,
                    name = trail.name,
                    difficultyName = trail.difficulty.name,
                    rawDifficultyTag = trail.rawDifficultyTag,
                    surface = trail.surface,
                    centerLat = trail.centerLat,
                    centerLon = trail.centerLon,
                    lengthMiles = trail.lengthMiles,
                    operatorName = trail.operatorName,
                    networkName = trail.networkName,
                    description = trail.description,
                    oneway = trail.oneway,
                    uphillScale = trail.uphillScale,
                    isDesignatedMtb = trail.isDesignatedMtb,
                    geometryEncoded = encodedGeom,
                    elevationGainFeet = cachedProfile?.elevationGainFeet ?: trail.elevationGainFeet,
                    elevationProfileEncoded = cachedProfile?.let { GeometrySerializer.encodeElevationProfile(it) } ?: ""
                )
            }

            val approxBytes = entities.sumOf {
                (it.name.length + it.geometryEncoded.length + (it.description?.length ?: 0) + 128).toLong()
            }.coerceAtLeast(1024L)

            val regionEntity = OfflineRegionEntity(
                id = regionId,
                name = regionName,
                centerLat = centerLat,
                centerLon = centerLon,
                radiusMiles = GeoUtils.RADIUS_TWENTY_MILES,
                trailCount = entities.size,
                downloadedAtMillis = System.currentTimeMillis(),
                approxSizeBytes = approxBytes,
                hasFullGeometry = hasFullGeometry && entities.any { it.geometryEncoded.isNotEmpty() }
            )

            offlineTrailDao.replaceRegionWithTrails(regionEntity, entities)
            Result.success(regionEntity.toDomain())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loadOfflineRegionTrails(
        regionId: String,
        referenceLat: Double,
        referenceLon: Double
    ): List<Trail> = withContext(Dispatchers.IO) {
        val entities = offlineTrailDao.getTrailsForRegion(regionId)
        entities.map { entity ->
            val dist = GeoUtils.haversineMiles(
                referenceLat,
                referenceLon,
                entity.centerLat,
                entity.centerLon
            )
            val bearing = GeoUtils.bearingDegrees(
                referenceLat,
                referenceLon,
                entity.centerLat,
                entity.centerLon
            )
            val difficulty = TrailDifficulty.entries.firstOrNull { it.name == entity.difficultyName }
                ?: TrailDifficulty.UNRATED
            val segments = GeometrySerializer.decodeSegments(entity.geometryEncoded)
            val decodedProfile = GeometrySerializer.decodeElevationProfile(entity.elevationProfileEncoded)
            Trail(
                osmKey = entity.osmKey,
                osmType = entity.osmType,
                osmId = entity.osmId,
                name = entity.name,
                difficulty = difficulty,
                rawDifficultyTag = entity.rawDifficultyTag,
                surface = entity.surface,
                centerLat = entity.centerLat,
                centerLon = entity.centerLon,
                distanceFromUserMiles = dist,
                bearingDegrees = bearing,
                bearingCardinal = GeoUtils.cardinalDirection(bearing),
                lengthMiles = entity.lengthMiles,
                operatorName = entity.operatorName,
                networkName = entity.networkName,
                description = entity.description,
                oneway = entity.oneway,
                uphillScale = entity.uphillScale,
                isDesignatedMtb = entity.isDesignatedMtb,
                geometry = segments.flatten(),
                routeSegments = segments,
                isOfflineCached = true,
                elevationGainFeet = decodedProfile?.elevationGainFeet ?: entity.elevationGainFeet,
                elevationProfile = decodedProfile
            )
        }.sortedBy { it.distanceFromUserMiles }
    }

    suspend fun findMatchingOfflineRegion(lat: Double, lon: Double): OfflineRegion? = withContext(Dispatchers.IO) {
        val regions = offlineTrailDao.getAllRegionsOnce()
        var closest: OfflineRegionEntity? = null
        var closestDistance = Double.MAX_VALUE
        for (region in regions) {
            val distance = GeoUtils.haversineMiles(lat, lon, region.centerLat, region.centerLon)
            if (distance <= GeoUtils.RADIUS_TWENTY_MILES && distance < closestDistance) {
                closestDistance = distance
                closest = region
            }
        }
        closest?.toDomain()
    }

    suspend fun deleteOfflineRegion(regionId: String) = withContext(Dispatchers.IO) {
        offlineTrailDao.deleteRegionById(regionId)
    }

    suspend fun searchPlaces(query: String): Result<List<LocationPlace>> = withContext(Dispatchers.IO) {
        try {
            val results = NominatimClient.service.searchPlaces(query = query.trim())
            val mapped = results.mapNotNull { dto ->
                val lat = dto.lat.toDoubleOrNull() ?: return@mapNotNull null
                val lon = dto.lon.toDoubleOrNull() ?: return@mapNotNull null
                val cleanName = dto.displayName
                    .split(",")
                    .take(2)
                    .joinToString(", ") { it.trim() }
                LocationPlace(name = cleanName, lat = lat, lon = lon, isCurrentGps = false)
            }
            Result.success(mapped)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun buildRegionId(lat: Double, lon: Double): String {
        return String.format(Locale.US, "region_%.2f_%.2f", lat, lon)
    }

    private suspend fun queryWithGeomFallback(
        geomQuery: String,
        lightQuery: String
    ): List<OverpassElement> {
        return try {
            executeOverpassWithFallback(geomQuery).elements
        } catch (_: Exception) {
            executeOverpassWithFallback(lightQuery).elements
        }
    }

    private suspend fun executeOverpassWithFallback(query: String): OverpassResponse {
        var lastError: Exception? = null
        for (endpoint in OverpassClient.ENDPOINTS) {
            try {
                return OverpassClient.service.query(url = endpoint, query = query)
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("Overpass query failed")
    }

    private fun buildPrimaryMtbQuery(lat: Double, lon: Double, includeGeometry: Boolean): String {
        val latStr = String.format(Locale.US, "%.5f", lat)
        val lonStr = String.format(Locale.US, "%.5f", lon)
        val radius = GeoUtils.RADIUS_TWENTY_MILES_METERS
        val outClause = if (includeGeometry) "out tags bb center geom;" else "out tags bb center;"
        val timeout = if (includeGeometry) 30 else 25
        return """
            [out:json][timeout:$timeout];
            (
              relation["route"="mtb"](around:$radius,$latStr,$lonStr);
              way["route"="mtb"](around:$radius,$latStr,$lonStr);
              way["mtb:scale"](around:$radius,$latStr,$lonStr);
              way["mtb:scale:imba"](around:$radius,$latStr,$lonStr);
              way["mtb"](around:$radius,$latStr,$lonStr);
            );
            $outClause
        """.trimIndent()
    }

    private fun buildSingletrackFallbackQuery(lat: Double, lon: Double, includeGeometry: Boolean): String {
        val latStr = String.format(Locale.US, "%.5f", lat)
        val lonStr = String.format(Locale.US, "%.5f", lon)
        val radius = GeoUtils.RADIUS_TWENTY_MILES_METERS
        val outClause = if (includeGeometry) "out tags bb center geom;" else "out tags bb center;"
        return """
            [out:json][timeout:25];
            (
              way["highway"~"path|track"]["bicycle"~"designated|yes"]["name"](around:$radius,$latStr,$lonStr);
            );
            $outClause
        """.trimIndent()
    }

    private fun OfflineRegionEntity.toDomain(): OfflineRegion = OfflineRegion(
        id = id,
        name = name,
        centerLat = centerLat,
        centerLon = centerLon,
        radiusMiles = radiusMiles,
        trailCount = trailCount,
        downloadedAtMillis = downloadedAtMillis,
        approxSizeBytes = approxSizeBytes,
        hasFullGeometry = hasFullGeometry
    )

    private companion object {
        const val MAX_ELEVATION_CACHE_ENTRIES = 150
    }
}
