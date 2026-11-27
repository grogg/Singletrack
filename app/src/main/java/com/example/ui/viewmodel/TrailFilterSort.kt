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
    val tokens = if (trimmedQuery.isEmpty()) {
        emptyList()
    } else {
        trimmedQuery.split(QUERY_SPLIT_REGEX).filter { it.isNotEmpty() }
    }

    val filtered = if (difficulty == null && tokens.isEmpty()) {
        trails
    } else {
        trails.filter { trail ->
            val matchesDiff = difficulty == null || trail.difficulty == difficulty
            if (!matchesDiff) return@filter false
            if (tokens.isEmpty()) return@filter true
            tokens.all { token -> trailMatchesToken(trail, token) }
        }
    }

    val baseComparator = when (sortOrder) {
        TrailSortOrder.CLOSEST -> compareBy<Trail> { it.distanceFromUserMiles }
        TrailSortOrder.DIFFICULTY -> compareBy<Trail> { it.difficulty.rank }.thenBy { it.distanceFromUserMiles }
        TrailSortOrder.LENGTH -> compareByDescending<Trail> { it.lengthMiles ?: -1.0 }
            .thenBy { it.distanceFromUserMiles }
        TrailSortOrder.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
    }

    if (tokens.isEmpty()) {
        return filtered.sortedWith(baseComparator)
    }

    return filtered.sortedWith(
        compareBy<Trail> { searchRelevanceTier(it, trimmedQuery, tokens) }
            .then(baseComparator)
    )
}

private fun trailMatchesToken(trail: Trail, token: String): Boolean {
    return trail.name.contains(token, ignoreCase = true) ||
        trail.surface.contains(token, ignoreCase = true) ||
        trail.difficulty.label.contains(token, ignoreCase = true) ||
        trail.difficulty.shortLabel.contains(token, ignoreCase = true) ||
        (trail.rawDifficultyTag?.contains(token, ignoreCase = true) == true) ||
        (trail.networkName?.contains(token, ignoreCase = true) == true) ||
        (trail.operatorName?.contains(token, ignoreCase = true) == true) ||
        (trail.description?.contains(token, ignoreCase = true) == true)
}

private fun searchRelevanceTier(
    trail: Trail,
    fullQuery: String,
    tokens: List<String>
): Int {
    return when {
        trail.name.equals(fullQuery, ignoreCase = true) -> 0
        trail.name.startsWith(fullQuery, ignoreCase = true) -> 1
        trail.name.contains(fullQuery, ignoreCase = true) -> 2
        tokens.all { trail.name.contains(it, ignoreCase = true) } -> 3
        else -> 4
    }
}

private val QUERY_SPLIT_REGEX = Regex("""\s+""")

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
