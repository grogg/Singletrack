package com.example.ui.viewmodel

import com.example.data.repository.GeoUtils
import com.example.domain.model.LocationPlace
import com.example.domain.model.OfflineRegion
import com.example.domain.model.Trail
import com.example.domain.model.TrailDifficulty
import com.example.domain.model.TrailSortOrder

internal fun applyTrailFiltersAndSort(
    trails: List<Trail>,
    query: String,
    difficulty: TrailDifficulty?,
    sortOrder: TrailSortOrder
): List<Trail> {
    val trimmedQuery = query.trim()
    val filtered = trails.filter { trail ->
        val matchesDiff = difficulty == null || trail.difficulty == difficulty
        val matchesQuery = trimmedQuery.isEmpty() ||
            trail.name.contains(trimmedQuery, ignoreCase = true) ||
            trail.surface.contains(trimmedQuery, ignoreCase = true) ||
            (trail.networkName?.contains(trimmedQuery, ignoreCase = true) == true) ||
            (trail.operatorName?.contains(trimmedQuery, ignoreCase = true) == true)
        matchesDiff && matchesQuery
    }

    return when (sortOrder) {
        TrailSortOrder.CLOSEST -> filtered.sortedBy { it.distanceFromUserMiles }
        TrailSortOrder.DIFFICULTY -> filtered.sortedWith(
            compareBy<Trail> { it.difficulty.rank }.thenBy { it.distanceFromUserMiles }
        )
        TrailSortOrder.LENGTH -> filtered.sortedByDescending { it.lengthMiles ?: -1.0 }
        TrailSortOrder.NAME -> filtered.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }
}

internal fun isLocationSavedOffline(
    location: LocationPlace?,
    regions: List<OfflineRegion>
): Boolean {
    if (location == null) return false
    return regions.any { region ->
        GeoUtils.haversineMiles(
            location.lat,
            location.lon,
            region.centerLat,
            region.centerLon
        ) <= 8.0
    }
}
