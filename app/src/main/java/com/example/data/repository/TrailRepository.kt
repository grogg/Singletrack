package com.example.data.repository

import com.example.data.local.CachedTrailEntity
import com.example.data.local.GeometrySerializer
import com.example.data.local.OfflineRegionEntity
import com.example.data.local.OfflineTrailDao
import com.example.data.remote.ElevationClient
import com.example.data.remote.NominatimClient
import com.example.data.remote.NominatimPlaceDto
import com.example.data.remote.OverpassClient
import com.example.data.remote.OverpassElement
import com.example.data.remote.OverpassResponse
import com.example.data.remote.PhotonClient
import com.example.data.remote.PhotonFeature
import com.example.domain.model.GeoPoint
import com.example.domain.model.LocationPlace
import com.example.domain.model.OfflineRegion
import com.example.domain.model.Trail
import com.example.domain.model.TrailDifficulty
import com.example.domain.model.TrailElevationProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.Collections
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class TrailScanResult(
    val trails: List<Trail>,
    val isFromOfflineCache: Boolean,
    val offlineRegionName: String?,
    val usedSingletrackFallback: Boolean
)

class TrailRepository(
    private val offlineTrailDao: OfflineTrailDao
) {
    private data class CachedScanEntry(
        val result: TrailScanResult,
        val timestampMillis: Long
    )

    private data class CachedPlaceEntry(
        val places: List<LocationPlace>,
        val timestampMillis: Long
    )

    private val elevationMemoryCache: MutableMap<String, TrailElevationProfile> =
        Collections.synchronizedMap(
            object : LinkedHashMap<String, TrailElevationProfile>(64, 0.75f, true) {
                override fun removeEldestEntry(
                    eldest: MutableMap.MutableEntry<String, TrailElevationProfile>?
                ): Boolean = size > MAX_ELEVATION_CACHE_ENTRIES
            }
        )

    private val scanMemoryCache: MutableMap<String, CachedScanEntry> =
        Collections.synchronizedMap(
            object : LinkedHashMap<String, CachedScanEntry>(16, 0.75f, true) {
                override fun removeEldestEntry(
                    eldest: MutableMap.MutableEntry<String, CachedScanEntry>?
                ): Boolean = size > MAX_SCAN_CACHE_ENTRIES
            }
        )

    private val placeSearchCache: MutableMap<String, CachedPlaceEntry> =
        Collections.synchronizedMap(
            object : LinkedHashMap<String, CachedPlaceEntry>(32, 0.75f, true) {
                override fun removeEldestEntry(
                    eldest: MutableMap.MutableEntry<String, CachedPlaceEntry>?
                ): Boolean = size > MAX_PLACE_CACHE_ENTRIES
            }
        )

    private val endpointCooldownUntilMs = ConcurrentHashMap<String, Long>()

    @Volatile
    private var preferredEndpoint: String? = null

    fun observeOfflineRegions(): Flow<List<OfflineRegion>> {
        return offlineTrailDao.observeRegions().map { list ->
            list.map { it.toDomain() }
        }
    }

    suspend fun getOfflineRegionsOnce(): List<OfflineRegion> = withContext(Dispatchers.IO) {
        offlineTrailDao.getAllRegionsOnce().map { it.toDomain() }
    }

    suspend fun fetchTrailsNear(
        lat: Double,
        lon: Double,
        forceRefresh: Boolean = false
    ): Result<TrailScanResult> = withContext(Dispatchers.IO) {
        val cacheKey = buildRegionId(lat, lon)
        if (!forceRefresh) {
            val cached = scanMemoryCache[cacheKey]
            if (cached != null && (System.currentTimeMillis() - cached.timestampMillis) <= SCAN_CACHE_TTL_MS) {
                return@withContext Result.success(cached.result)
            }
        }

        try {
            // For a 20-mile radius, fetch geometry directly so trail routes overlay on the topo map,
            // degrading cleanly to fast core MTB or lightweight queries in dense metro areas.
            val (primaryElements, hasFullGeom) = fetchMtbElementsWithFallback(lat, lon)
            var trails = GeoUtils.parseAndGroupElements(primaryElements, lat, lon)
            var usedFallback = false

            if (trails.size < MIN_PRIMARY_TRAIL_THRESHOLD) {
                val fallbackElements = fetchSingletrackFallbackElements(lat, lon, preferGeometry = hasFullGeom)
                val fallbackTrails = GeoUtils.parseAndGroupElements(fallbackElements, lat, lon)
                if (fallbackTrails.isNotEmpty()) {
                    val existingKeys = trails.mapTo(HashSet()) { it.osmKey }
                    val newAdditions = fallbackTrails.filter { it.osmKey !in existingKeys }
                    if (newAdditions.isNotEmpty()) {
                        trails = (trails + newAdditions).sortedBy { it.distanceFromUserMiles }
                        usedFallback = true
                    }
                }
            }

            val scanResult = TrailScanResult(
                trails = trails,
                isFromOfflineCache = false,
                offlineRegionName = null,
                usedSingletrackFallback = usedFallback
            )
            if (trails.isNotEmpty()) {
                scanMemoryCache[cacheKey] = CachedScanEntry(
                    result = scanResult,
                    timestampMillis = System.currentTimeMillis()
                )
            }
            Result.success(scanResult)
        } catch (ce: CancellationException) {
            throw ce
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
        } catch (ce: CancellationException) {
            throw ce
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
        } catch (ce: CancellationException) {
            throw ce
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
        } catch (ce: CancellationException) {
            throw ce
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
            val regionId = buildRegionId(centerLat, centerLon)
            val cachedScan = scanMemoryCache[regionId]
                ?.takeIf { (System.currentTimeMillis() - it.timestampMillis) <= SCAN_CACHE_TTL_MS }
                ?.result
            val cachedWithGeometry = cachedScan?.trails?.takeIf { cachedTrails ->
                cachedTrails.isNotEmpty() && cachedTrails.any { t ->
                    t.geometry.size >= 2 || t.routeSegments.any { seg -> seg.size >= 2 }
                }
            }

            val hasFullGeometry: Boolean
            var trails: List<Trail>
            if (cachedWithGeometry != null) {
                hasFullGeometry = true
                trails = cachedWithGeometry.map { it.copy(isOfflineCached = true) }
            } else {
                val (elements, fetchedFullGeom) = fetchMtbElementsWithFallback(centerLat, centerLon)
                hasFullGeometry = fetchedFullGeom
                trails = GeoUtils.parseAndGroupElements(
                    elements = elements,
                    originLat = centerLat,
                    originLon = centerLon,
                    isOfflineCached = true
                )

                if (trails.size < MIN_PRIMARY_TRAIL_THRESHOLD) {
                    val fallbackElements = fetchSingletrackFallbackElements(
                        centerLat,
                        centerLon,
                        preferGeometry = hasFullGeometry
                    )
                    val fallbackTrails = GeoUtils.parseAndGroupElements(
                        elements = fallbackElements,
                        originLat = centerLat,
                        originLon = centerLon,
                        isOfflineCached = true
                    )
                    if (fallbackTrails.isNotEmpty()) {
                        val existingKeys = trails.mapTo(HashSet()) { it.osmKey }
                        trails = (trails + fallbackTrails.filter { it.osmKey !in existingKeys })
                            .sortedBy { it.distanceFromUserMiles }
                    }
                }
            }
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
        } catch (ce: CancellationException) {
            throw ce
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
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext Result.success(emptyList())

        val offlineMatches = runCatching {
            offlineTrailDao.getAllRegionsOnce()
                .filter { it.name.contains(trimmed, ignoreCase = true) }
                .map { LocationPlace(name = "${it.name} (Offline)", lat = it.centerLat, lon = it.centerLon) }
        }.getOrElse { emptyList() }

        parseCoordinateQuery(trimmed)?.let { coordPlace ->
            val combined = deduplicatePlaces(listOf(coordPlace) + offlineMatches).take(MAX_PLACE_RESULTS)
            return@withContext Result.success(combined)
        }

        val cacheKey = trimmed.lowercase(Locale.US)
        val cached = placeSearchCache[cacheKey]
        if (cached != null && (System.currentTimeMillis() - cached.timestampMillis) <= PLACE_CACHE_TTL_MS) {
            val combined = deduplicatePlaces(offlineMatches + cached.places).take(MAX_PLACE_RESULTS)
            return@withContext Result.success(combined)
        }

        try {
            val (photonResult, nominatimResult) = coroutineScope {
                val photonDeferred = async {
                    try {
                        Result.success(
                            PhotonClient.service.searchPlaces(query = trimmed).features.mapNotNull(::mapPhotonFeature)
                        )
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                }
                val nominatimDeferred = async {
                    try {
                        Result.success(
                            NominatimClient.service.searchPlaces(query = trimmed).mapNotNull(::mapNominatimDto)
                        )
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                }
                photonDeferred.await() to nominatimDeferred.await()
            }

            val photonPlaces = photonResult.getOrElse { emptyList() }
            val nominatimPlaces = nominatimResult.getOrElse { emptyList() }
            val remotePlaces = deduplicatePlaces(photonPlaces + nominatimPlaces)

            if (photonResult.isFailure && nominatimResult.isFailure && offlineMatches.isEmpty()) {
                return@withContext Result.failure(
                    photonResult.exceptionOrNull()
                        ?: nominatimResult.exceptionOrNull()
                        ?: IllegalStateException("Location search failed")
                )
            }

            if (remotePlaces.isNotEmpty()) {
                placeSearchCache[cacheKey] = CachedPlaceEntry(
                    places = remotePlaces,
                    timestampMillis = System.currentTimeMillis()
                )
            }

            val combined = deduplicatePlaces(offlineMatches + remotePlaces).take(MAX_PLACE_RESULTS)
            Result.success(combined)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            if (offlineMatches.isNotEmpty()) {
                Result.success(offlineMatches.take(MAX_PLACE_RESULTS))
            } else {
                Result.failure(e)
            }
        }
    }

    fun buildRegionId(lat: Double, lon: Double): String {
        return String.format(Locale.US, "region_%.2f_%.2f", lat, lon)
    }

    private suspend fun fetchMtbElementsWithFallback(
        lat: Double,
        lon: Double
    ): Pair<List<OverpassElement>, Boolean> {
        val endpoints = OverpassClient.endpointsForLocation(lat, lon)
        val attempts = listOf(
            buildPrimaryMtbQuery(lat, lon, includeGeometry = true) to true,
            buildCoreMtbQuery(lat, lon, includeGeometry = true) to true,
            buildPrimaryMtbQuery(lat, lon, includeGeometry = false) to false,
            buildCoreMtbQuery(lat, lon, includeGeometry = false) to false
        )
        var lastError: Exception? = null
        for ((query, hasGeom) in attempts) {
            try {
                return executeOverpassWithFallback(query, endpoints).elements to hasGeom
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("Overpass query failed")
    }

    private suspend fun fetchSingletrackFallbackElements(
        lat: Double,
        lon: Double,
        preferGeometry: Boolean
    ): List<OverpassElement> {
        val endpoints = OverpassClient.endpointsForLocation(lat, lon)
        val attempts = buildList {
            add(buildSingletrackFallbackQuery(lat, lon, radiusMiles = 12.0, includeGeometry = preferGeometry))
            add(buildSingletrackFallbackQuery(lat, lon, radiusMiles = 8.0, includeGeometry = preferGeometry))
            if (preferGeometry) {
                add(buildSingletrackFallbackQuery(lat, lon, radiusMiles = 8.0, includeGeometry = false))
            }
        }
        for (query in attempts) {
            try {
                return executeOverpassWithFallback(query, endpoints).elements
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                // Try tighter radius / lighter payload
            }
        }
        return emptyList()
    }

    private fun orderEndpoints(baseEndpoints: List<String>): List<String> {
        val now = System.currentTimeMillis()
        val preferred = preferredEndpoint
        return baseEndpoints.sortedWith(
            compareBy<String> { (endpointCooldownUntilMs[it] ?: 0L) > now }
                .thenBy { it != preferred }
        )
    }

    private suspend fun executeOverpassWithFallback(
        query: String,
        baseEndpoints: List<String> = OverpassClient.ENDPOINTS
    ): OverpassResponse = coroutineScope {
        val endpoints = orderEndpoints(baseEndpoints)
        val resultsChannel = Channel<Pair<String, Result<OverpassResponse>>>(endpoints.size)
        val wakeSignal = Channel<Unit>(endpoints.size)
        val completedOrEmpty = AtomicInteger(0)

        endpoints.forEachIndexed { index, endpoint ->
            launch {
                if (index > 0) {
                    val delayMs = index * HEDGE_STAGGER_MS
                    val deadline = System.currentTimeMillis() + delayMs
                    while (completedOrEmpty.get() < index) {
                        val remaining = deadline - System.currentTimeMillis()
                        if (remaining <= 0L) break
                        withTimeoutOrNull(remaining) {
                            wakeSignal.receive()
                        }
                    }
                }
                val attempt = try {
                    val response = OverpassClient.service.query(url = endpoint, query = query)
                    if (response.hasRuntimeError()) {
                        throw IOException("Overpass runtime error: ${response.remark}")
                    }
                    endpointCooldownUntilMs.remove(endpoint)
                    if (response.elements.isEmpty()) {
                        completedOrEmpty.incrementAndGet()
                        wakeSignal.trySend(Unit)
                    }
                    Result.success(response)
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Exception) {
                    endpointCooldownUntilMs[endpoint] = System.currentTimeMillis() + MIRROR_COOLDOWN_MS
                    completedOrEmpty.incrementAndGet()
                    wakeSignal.trySend(Unit)
                    Result.failure(e)
                }
                resultsChannel.trySend(endpoint to attempt)
            }
        }

        var lastError: Throwable? = null
        var firstEmptyResponse: OverpassResponse? = null
        var remainingOutcomes = endpoints.size

        while (remainingOutcomes > 0) {
            val received = if (firstEmptyResponse == null) {
                resultsChannel.receive()
            } else {
                withTimeoutOrNull(EMPTY_VERIFY_WAIT_MS) { resultsChannel.receive() }
            }
            if (received == null) break
            remainingOutcomes--

            val (endpoint, outcome) = received
            outcome.onSuccess { response ->
                if (response.elements.isNotEmpty()) {
                    preferredEndpoint = endpoint
                    coroutineContext.cancelChildren()
                    return@coroutineScope response
                } else if (firstEmptyResponse == null) {
                    firstEmptyResponse = response
                }
            }.onFailure { err ->
                lastError = err
            }
        }

        if (firstEmptyResponse != null) {
            coroutineContext.cancelChildren()
            return@coroutineScope firstEmptyResponse
        }
        throw (lastError as? Exception) ?: IllegalStateException("Overpass query failed")
    }

    private fun buildOverpassHeaderAndFilter(
        lat: Double,
        lon: Double,
        radiusMiles: Double,
        timeoutSeconds: Int
    ): Pair<String, String> {
        val bbox = GeoUtils.computeBoundingBox(lat, lon, radiusMiles)
        return if (!bbox.crossesAntimeridian) {
            "[out:json][timeout:$timeoutSeconds][bbox:${bbox.toOverpassBboxString()}];" to ""
        } else {
            val latStr = String.format(Locale.US, "%.5f", lat)
            val lonStr = String.format(Locale.US, "%.5f", lon)
            val meters = (radiusMiles * 1609.344).toInt()
            "[out:json][timeout:$timeoutSeconds];" to "(around:$meters,$latStr,$lonStr)"
        }
    }

    private fun buildPrimaryMtbQuery(lat: Double, lon: Double, includeGeometry: Boolean): String {
        val timeout = if (includeGeometry) 12 else 10
        val (header, filter) = buildOverpassHeaderAndFilter(
            lat = lat,
            lon = lon,
            radiusMiles = GeoUtils.RADIUS_TWENTY_MILES,
            timeoutSeconds = timeout
        )
        val outClause = if (includeGeometry) "out geom qt;" else "out tags center qt;"
        return """
            $header
            (
              relation["route"="mtb"]$filter;
              way["route"="mtb"]$filter;
              way["mtb:scale"]$filter;
              way["mtb:scale:imba"]$filter;
              way["mtb:type"]$filter;
              way["mtb"~"^(yes|designated|official|permissive|trail)$"]$filter;
              way["highway"="path"]["bicycle"="designated"]$filter;
            );
            $outClause
        """.trimIndent()
    }

    private fun buildCoreMtbQuery(lat: Double, lon: Double, includeGeometry: Boolean): String {
        val timeout = if (includeGeometry) 10 else 9
        val (header, filter) = buildOverpassHeaderAndFilter(
            lat = lat,
            lon = lon,
            radiusMiles = GeoUtils.RADIUS_TWENTY_MILES,
            timeoutSeconds = timeout
        )
        val outClause = if (includeGeometry) "out geom qt;" else "out tags center qt;"
        return """
            $header
            (
              relation["route"="mtb"]$filter;
              way["route"="mtb"]$filter;
              way["mtb:scale"]$filter;
              way["mtb:scale:imba"]$filter;
              way["mtb:type"]$filter;
              way["mtb"~"^(yes|designated|official|permissive|trail)$"]$filter;
            );
            $outClause
        """.trimIndent()
    }

    private fun buildSingletrackFallbackQuery(
        lat: Double,
        lon: Double,
        radiusMiles: Double = 12.0,
        includeGeometry: Boolean
    ): String {
        val timeout = if (includeGeometry) 10 else 8
        val (header, filter) = buildOverpassHeaderAndFilter(
            lat = lat,
            lon = lon,
            radiusMiles = radiusMiles,
            timeoutSeconds = timeout
        )
        val outClause = if (includeGeometry) "out geom qt;" else "out tags center qt;"
        return """
            $header
            (
              way["highway"="path"]["bicycle"~"^(yes|permissive)$"]["name"]$filter;
              way["highway"="track"]["name"]["bicycle"!="no"]["access"!="private"]$filter;
              way["highway"="path"]["name"]["surface"~"^(dirt|ground|earth|rock|gravel|unpaved|fine_gravel|compacted)$"]["bicycle"!="no"]$filter;
            );
            $outClause
        """.trimIndent()
    }

    private fun mapPhotonFeature(feature: PhotonFeature): LocationPlace? {
        val coords = feature.geometry?.coordinates ?: return null
        if (coords.size < 2) return null
        val lon = coords[0]
        val lat = coords[1]
        if (!lat.isFinite() || !lon.isFinite()) return null
        val props = feature.properties ?: return null
        val parts = listOfNotNull(
            props.name?.takeIf { it.isNotBlank() },
            props.city?.takeIf { it.isNotBlank() },
            props.county?.takeIf { it.isNotBlank() }?.takeIf { props.city.isNullOrBlank() && props.name != it },
            props.state?.takeIf { it.isNotBlank() } ?: props.country?.takeIf { it.isNotBlank() }
        ).distinct()
        val label = parts.take(3).joinToString(", ").ifBlank {
            String.format(Locale.US, "%.3f°, %.3f°", lat, lon)
        }
        return LocationPlace(name = label, lat = lat, lon = lon, isCurrentGps = false)
    }

    private fun mapNominatimDto(dto: NominatimPlaceDto): LocationPlace? {
        val lat = dto.lat.toDoubleOrNull() ?: return null
        val lon = dto.lon.toDoubleOrNull() ?: return null
        if (!lat.isFinite() || !lon.isFinite()) return null
        val address = dto.address
        val cleanName = if (!address.isNullOrEmpty()) {
            val primary = address["tourism"]
                ?: address["leisure"]
                ?: address["park"]
                ?: address["natural"]
                ?: address["city"]
                ?: address["town"]
                ?: address["village"]
                ?: address["hamlet"]
                ?: address["county"]
            val locality = address["city"] ?: address["town"] ?: address["village"]
            val region = address["state"] ?: address["country"]
            listOfNotNull(
                primary?.takeIf { it.isNotBlank() },
                locality?.takeIf { it.isNotBlank() && it != primary },
                region?.takeIf { it.isNotBlank() && it != primary }
            ).distinct().take(3).joinToString(", ")
        } else {
            ""
        }.ifBlank {
            dto.displayName
                .split(",")
                .take(2)
                .joinToString(", ") { it.trim() }
        }
        return LocationPlace(name = cleanName, lat = lat, lon = lon, isCurrentGps = false)
    }

    private fun deduplicatePlaces(places: List<LocationPlace>): List<LocationPlace> {
        val deduped = ArrayList<LocationPlace>(places.size)
        for (candidate in places) {
            val alreadyPresent = deduped.any { existing ->
                existing.name.equals(candidate.name, ignoreCase = true) ||
                    GeoUtils.haversineMiles(existing.lat, existing.lon, candidate.lat, candidate.lon) < 0.5
            }
            if (!alreadyPresent) {
                deduped.add(candidate)
            }
        }
        return deduped
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

    internal companion object {
        private const val MAX_ELEVATION_CACHE_ENTRIES = 150
        private const val MAX_SCAN_CACHE_ENTRIES = 12
        private const val SCAN_CACHE_TTL_MS = 5 * 60_000L
        private const val MAX_PLACE_CACHE_ENTRIES = 40
        private const val PLACE_CACHE_TTL_MS = 10 * 60_000L
        private const val MAX_PLACE_RESULTS = 10
        private const val HEDGE_STAGGER_MS = 700L
        private const val MIRROR_COOLDOWN_MS = 60_000L
        private const val EMPTY_VERIFY_WAIT_MS = 3_500L
        private const val MIN_PRIMARY_TRAIL_THRESHOLD = 5

        private val COORDINATE_QUERY_REGEX = Regex(
            """^\s*(-?\d{1,2}(?:\.\d+)?)\s*[,;\s]\s*(-?\d{1,3}(?:\.\d+)?)\s*$"""
        )

        internal fun parseCoordinateQuery(query: String): LocationPlace? {
            val match = COORDINATE_QUERY_REGEX.matchEntire(query) ?: return null
            val lat = match.groupValues[1].toDoubleOrNull() ?: return null
            val lon = match.groupValues[2].toDoubleOrNull() ?: return null
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
            val label = String.format(Locale.US, "Coordinates (%.4f°, %.4f°)", lat, lon)
            return LocationPlace(name = label, lat = lat, lon = lon, isCurrentGps = false)
        }
    }
}
