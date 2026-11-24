package com.example.data.repository

import com.example.domain.model.GeoPoint
import com.example.domain.model.GpxNavigationState
import com.example.domain.model.GpxRouteStatus
import com.example.domain.model.GpxTrack
import com.example.domain.model.GpxTrackPoint
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max

object GpxNavigationEngine {
    private const val FEET_PER_MILE = 5280.0
    private const val OFF_ROUTE_THRESHOLD_FEET = 180.0
    private const val FINISH_RADIUS_FEET = 120.0
    private const val LOOKAHEAD_MILES = 0.04

    fun idleState(track: GpxTrack?): GpxNavigationState {
        return if (track == null) {
            GpxNavigationState()
        } else {
            GpxNavigationState(
                track = track,
                isNavigating = false,
                routeStatus = GpxRouteStatus.IDLE,
                distanceCompletedMiles = 0.0,
                distanceRemainingMiles = track.totalDistanceMiles,
                progressFraction = 0f,
                turnInstruction = "Tap Start Navigation to follow ${track.name}"
            )
        }
    }

    fun updateNavigationProgress(
        previousState: GpxNavigationState,
        track: GpxTrack,
        userLat: Double,
        userLon: Double,
        userHeadingDegrees: Float? = null
    ): GpxNavigationState {
        if (!userLat.isFinite() || !userLon.isFinite()) return previousState
        val points = track.points
        if (points.isEmpty()) return previousState

        val userGeo = GeoPoint(userLat, userLon)

        if (points.size == 1) {
            val target = points.first()
            val distMiles = GeoUtils.haversineMiles(userLat, userLon, target.lat, target.lon)
            val distFeet = distMiles * FEET_PER_MILE
            val bearing = GeoUtils.bearingDegrees(userLat, userLon, target.lat, target.lon)
            val cardinal = GeoUtils.cardinalDirection(bearing)
            val finished = distFeet <= FINISH_RADIUS_FEET
            return previousState.copy(
                track = track,
                userLocation = userGeo,
                userHeadingDegrees = userHeadingDegrees,
                routeStatus = if (finished) GpxRouteStatus.FINISHED else GpxRouteStatus.APPROACHING_START,
                offRouteFeet = distFeet,
                distanceCompletedMiles = 0.0,
                distanceRemainingMiles = distMiles,
                bearingToTargetDegrees = bearing,
                bearingCardinal = cardinal,
                targetPoint = GeoPoint(target.lat, target.lon),
                nearestSegmentIndex = 0,
                nearestPointOnTrack = GeoPoint(target.lat, target.lon),
                progressFraction = if (finished) 1f else 0f,
                turnInstruction = if (finished) "Arrived at waypoint" else "Head $cardinal to waypoint",
                distanceToCueMiles = distMiles
            )
        }

        val cosLat = cos(Math.toRadians(userLat))
        var bestSegIndex = 0
        var bestFraction = 0.0
        var bestSqDist = Double.POSITIVE_INFINITY
        var bestProjLat = points.first().lat
        var bestProjLon = points.first().lon

        for (i in 0 until points.lastIndex) {
            val a = points[i]
            val b = points[i + 1]
            val ax = (a.lon - userLon) * cosLat
            val ay = a.lat - userLat
            val bx = (b.lon - userLon) * cosLat
            val by = b.lat - userLat

            val dx = bx - ax
            val dy = by - ay
            val lenSq = dx * dx + dy * dy
            val t = if (lenSq <= 1e-18) {
                0.0
            } else {
                ((-ax * dx - ay * dy) / lenSq).coerceIn(0.0, 1.0)
            }
            val px = ax + t * dx
            val py = ay + t * dy
            val sqDist = px * px + py * py
            if (sqDist < bestSqDist) {
                bestSqDist = sqDist
                bestSegIndex = i
                bestFraction = t
                bestProjLat = a.lat + t * (b.lat - a.lat)
                bestProjLon = a.lon + t * (b.lon - a.lon)
            }
        }

        val nearestPoint = GeoPoint(bestProjLat, bestProjLon)
        val crossTrackMiles = GeoUtils.haversineMiles(userLat, userLon, bestProjLat, bestProjLon)
        val crossTrackFeet = crossTrackMiles * FEET_PER_MILE

        val segStartMiles = points[bestSegIndex].cumulativeMiles
        val segEndMiles = points[bestSegIndex + 1].cumulativeMiles
        val completedMiles = (segStartMiles + bestFraction * (segEndMiles - segStartMiles))
            .coerceIn(0.0, track.totalDistanceMiles)
        val remainingMiles = max(0.0, track.totalDistanceMiles - completedMiles)
        val progress = if (track.totalDistanceMiles > 1e-6) {
            (completedMiles / track.totalDistanceMiles).toFloat().coerceIn(0f, 1f)
        } else {
            1f
        }

        val endPt = points.last()
        val distToFinishMiles = GeoUtils.haversineMiles(userLat, userLon, endPt.lat, endPt.lon)
        val distToFinishFeet = distToFinishMiles * FEET_PER_MILE

        val status = when {
            distToFinishFeet <= FINISH_RADIUS_FEET && (progress >= 0.80f || track.totalDistanceMiles <= 0.15) ->
                GpxRouteStatus.FINISHED
            crossTrackFeet > OFF_ROUTE_THRESHOLD_FEET && bestSegIndex == 0 && progress < 0.02f ->
                GpxRouteStatus.APPROACHING_START
            crossTrackFeet > OFF_ROUTE_THRESHOLD_FEET ->
                GpxRouteStatus.OFF_ROUTE
            else ->
                GpxRouteStatus.ON_TRACK
        }

        val targetGeo = findLookaheadPoint(points, bestSegIndex, completedMiles + LOOKAHEAD_MILES)
        val navTarget = when (status) {
            GpxRouteStatus.OFF_ROUTE -> nearestPoint
            GpxRouteStatus.APPROACHING_START -> GeoPoint(points.first().lat, points.first().lon)
            GpxRouteStatus.FINISHED -> GeoPoint(endPt.lat, endPt.lon)
            else -> targetGeo
        }

        val bearingToTarget = GeoUtils.bearingDegrees(userLat, userLon, navTarget.lat, navTarget.lon)
        val cardinal = GeoUtils.cardinalDirection(bearingToTarget)

        val (instruction, cueDistMiles) = computeTurnCue(
            points = points,
            nearestSegmentIndex = bestSegIndex,
            completedMiles = completedMiles,
            remainingMiles = remainingMiles,
            status = status,
            cardinal = cardinal,
            crossTrackMiles = crossTrackMiles
        )

        return previousState.copy(
            track = track,
            userLocation = userGeo,
            userHeadingDegrees = userHeadingDegrees,
            routeStatus = status,
            distanceCompletedMiles = completedMiles,
            distanceRemainingMiles = remainingMiles,
            offRouteFeet = crossTrackFeet,
            bearingToTargetDegrees = bearingToTarget,
            bearingCardinal = cardinal,
            targetPoint = navTarget,
            nearestSegmentIndex = bestSegIndex,
            nearestPointOnTrack = nearestPoint,
            progressFraction = if (status == GpxRouteStatus.FINISHED) 1f else progress,
            turnInstruction = instruction,
            distanceToCueMiles = cueDistMiles
        )
    }

    private fun findLookaheadPoint(
        points: List<GpxTrackPoint>,
        startSegmentIndex: Int,
        targetCumulativeMiles: Double
    ): GeoPoint {
        if (targetCumulativeMiles >= points.last().cumulativeMiles) {
            return GeoPoint(points.last().lat, points.last().lon)
        }
        var idx = startSegmentIndex.coerceIn(0, points.lastIndex - 1)
        while (idx < points.lastIndex && points[idx + 1].cumulativeMiles < targetCumulativeMiles) {
            idx++
        }
        val a = points[idx]
        val b = points[idx + 1]
        val segSpan = b.cumulativeMiles - a.cumulativeMiles
        val frac = if (segSpan > 1e-7) {
            ((targetCumulativeMiles - a.cumulativeMiles) / segSpan).coerceIn(0.0, 1.0)
        } else {
            0.0
        }
        return GeoPoint(
            lat = a.lat + frac * (b.lat - a.lat),
            lon = a.lon + frac * (b.lon - a.lon)
        )
    }

    private fun computeTurnCue(
        points: List<GpxTrackPoint>,
        nearestSegmentIndex: Int,
        completedMiles: Double,
        remainingMiles: Double,
        status: GpxRouteStatus,
        cardinal: String,
        crossTrackMiles: Double
    ): Pair<String, Double?> {
        when (status) {
            GpxRouteStatus.FINISHED -> return "Route complete!" to 0.0
            GpxRouteStatus.APPROACHING_START ->
                return "Head $cardinal to trail start" to crossTrackMiles.coerceAtLeast(0.0)
            GpxRouteStatus.OFF_ROUTE -> return "Return $cardinal to trail" to crossTrackMiles
            else -> Unit
        }

        if (remainingMiles <= 0.08) {
            return "Finish ahead" to remainingMiles
        }

        // Look up to 0.25 miles ahead for a meaningful directional turn
        val maxLookaheadMiles = completedMiles + 0.25
        var idx = nearestSegmentIndex.coerceIn(0, points.lastIndex - 1)
        while (idx + 2 <= points.lastIndex && points[idx + 1].cumulativeMiles <= maxLookaheadMiles) {
            val prev = points[idx]
            val vertex = points[idx + 1]
            val next = points[idx + 2]
            val segInLen = vertex.cumulativeMiles - prev.cumulativeMiles
            val segOutLen = next.cumulativeMiles - vertex.cumulativeMiles
            if (segInLen >= 0.004 && segOutLen >= 0.004) {
                val b1 = GeoUtils.bearingDegrees(prev.lat, prev.lon, vertex.lat, vertex.lon)
                val b2 = GeoUtils.bearingDegrees(vertex.lat, vertex.lon, next.lat, next.lon)
                val signedTurn = ((b2 - b1 + 540.0) % 360.0) - 180.0
                val absTurn = abs(signedTurn)
                if (absTurn >= 30.0) {
                    val distToTurn = (vertex.cumulativeMiles - completedMiles).coerceAtLeast(0.0)
                    val dirLabel = when {
                        absTurn >= 115.0 && signedTurn > 0 -> "Sharp switchback right"
                        absTurn >= 115.0 && signedTurn < 0 -> "Sharp switchback left"
                        absTurn >= 55.0 && signedTurn > 0 -> "Turn right"
                        absTurn >= 55.0 && signedTurn < 0 -> "Turn left"
                        signedTurn > 0 -> "Keep right"
                        else -> "Keep left"
                    }
                    return dirLabel to distToTurn
                }
            }
            idx++
        }

        return "Continue $cardinal along track" to remainingMiles
    }
}
