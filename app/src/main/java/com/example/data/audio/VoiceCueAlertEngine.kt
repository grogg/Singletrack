package com.example.data.audio

import com.example.domain.model.GpxNavigationState
import com.example.domain.model.GpxRouteStatus
import com.example.domain.model.MeasurementUnit
import com.example.domain.model.RideRecordingStatus
import com.example.ui.components.FormatUtils
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

class VoiceCueAlertEngine {
    private var wasOffRoute = false
    private var lastOffRouteAlertMillis = -1L
    private var lastAnnouncedDistanceMilestone = 0

    fun evaluateGpxOffRouteCue(
        isEnabled: Boolean,
        previousState: GpxNavigationState,
        newState: GpxNavigationState,
        unit: MeasurementUnit,
        nowMillis: Long = System.currentTimeMillis()
    ): String? {
        if (!newState.isNavigating || newState.routeStatus != GpxRouteStatus.OFF_ROUTE) {
            wasOffRoute = false
            return null
        }

        val enteredOffRoute = previousState.routeStatus != GpxRouteStatus.OFF_ROUTE || !wasOffRoute
        val elapsedSinceLast = if (lastOffRouteAlertMillis < 0L) {
            Long.MAX_VALUE
        } else {
            nowMillis - lastOffRouteAlertMillis
        }

        val shouldAlert = when {
            enteredOffRoute -> elapsedSinceLast >= OFF_ROUTE_REENTRY_COOLDOWN_MS
            else -> elapsedSinceLast >= OFF_ROUTE_REPEAT_INTERVAL_MS
        }

        wasOffRoute = true
        if (!isEnabled || !shouldAlert) {
            return null
        }

        lastOffRouteAlertMillis = nowMillis
        return formatOffRouteCue(
            offRouteFeet = newState.offRouteFeet,
            bearingCardinal = newState.bearingCardinal,
            unit = unit
        )
    }

    fun evaluateDistanceMilestoneCue(
        isEnabled: Boolean,
        rideStatus: RideRecordingStatus,
        distanceMiles: Double,
        elapsedSeconds: Long,
        unit: MeasurementUnit
    ): String? {
        if (rideStatus != RideRecordingStatus.RECORDING || !distanceMiles.isFinite()) {
            return null
        }
        val distanceInUnit = FormatUtils.distanceValue(distanceMiles, unit)
        val completedMilestones = floor(distanceInUnit + MILESTONE_EPSILON).toInt()
        if (completedMilestones <= lastAnnouncedDistanceMilestone || completedMilestones < 1) {
            return null
        }

        lastAnnouncedDistanceMilestone = completedMilestones
        if (!isEnabled) {
            return null
        }
        return formatDistanceMilestoneCue(
            milestone = completedMilestones,
            unit = unit,
            elapsedSeconds = elapsedSeconds
        )
    }

    fun syncMilestoneBaseline(distanceMiles: Double, unit: MeasurementUnit) {
        if (!distanceMiles.isFinite() || distanceMiles <= 0.0) {
            lastAnnouncedDistanceMilestone = 0
            return
        }
        val distanceInUnit = FormatUtils.distanceValue(distanceMiles, unit)
        lastAnnouncedDistanceMilestone = floor(distanceInUnit + MILESTONE_EPSILON).toInt().coerceAtLeast(0)
    }

    fun resetRideMilestones() {
        lastAnnouncedDistanceMilestone = 0
    }

    fun resetGpxState() {
        wasOffRoute = false
        lastOffRouteAlertMillis = -1L
    }

    companion object {
        const val OFF_ROUTE_REENTRY_COOLDOWN_MS = 15_000L
        const val OFF_ROUTE_REPEAT_INTERVAL_MS = 45_000L
        private const val MILESTONE_EPSILON = 1e-6

        fun formatOffRouteCue(
            offRouteFeet: Double?,
            bearingCardinal: String?,
            unit: MeasurementUnit
        ): String {
            val distancePart = offRouteFeet
                ?.takeIf { it.isFinite() && it > 0.0 }
                ?.let { feet ->
                    if (unit == MeasurementUnit.METRIC) {
                        val meters = feet * FormatUtils.FEET_TO_METERS
                        if (meters >= 1000.0) {
                            val km = meters / 1000.0
                            String.format(Locale.US, "%.1f kilometers from trail", km)
                        } else {
                            val roundedMeters = meters.roundToInt().coerceAtLeast(1)
                            val unitLabel = if (roundedMeters == 1) "meter" else "meters"
                            "$roundedMeters $unitLabel from trail"
                        }
                    } else {
                        if (feet >= 1000.0) {
                            val miles = feet / 5280.0
                            String.format(Locale.US, "%.1f miles from trail", miles)
                        } else {
                            val roundedFeet = feet.roundToInt().coerceAtLeast(1)
                            val unitLabel = if (roundedFeet == 1) "foot" else "feet"
                            "$roundedFeet $unitLabel from trail"
                        }
                    }
                }

            val spokenBearing = expandCardinalDirection(bearingCardinal)
            return buildString {
                append("Off route.")
                if (distancePart != null) {
                    append(' ')
                    append(distancePart)
                    append('.')
                }
                if (spokenBearing != null) {
                    append(" Head ")
                    append(spokenBearing)
                    if (distancePart == null) {
                        append(" to trail")
                    }
                    append('.')
                } else if (distancePart == null) {
                    append(" Return to trail.")
                }
            }
        }

        fun formatDistanceMilestoneCue(
            milestone: Int,
            unit: MeasurementUnit,
            elapsedSeconds: Long
        ): String {
            val unitWord = when (unit) {
                MeasurementUnit.IMPERIAL -> if (milestone == 1) "mile" else "miles"
                MeasurementUnit.METRIC -> if (milestone == 1) "kilometer" else "kilometers"
            }
            return if (elapsedSeconds > 0L) {
                "$milestone $unitWord completed, time ${formatSpokenDuration(elapsedSeconds)}."
            } else {
                "$milestone $unitWord completed."
            }
        }

        fun formatSpokenDuration(totalSeconds: Long): String {
            val clamped = totalSeconds.coerceAtLeast(0L)
            val hours = clamped / 3600L
            val minutes = (clamped % 3600L) / 60L
            val seconds = clamped % 60L

            val parts = mutableListOf<String>()
            if (hours > 0L) {
                parts.add("$hours ${if (hours == 1L) "hour" else "hours"}")
            }
            if (minutes > 0L) {
                parts.add("$minutes ${if (minutes == 1L) "minute" else "minutes"}")
            }
            if (seconds > 0L || parts.isEmpty()) {
                parts.add("$seconds ${if (seconds == 1L) "second" else "seconds"}")
            }
            return parts.joinToString(" ")
        }

        fun expandCardinalDirection(cardinal: String?): String? {
            return when (cardinal?.trim()?.uppercase(Locale.US)) {
                "N" -> "north"
                "NE" -> "northeast"
                "E" -> "east"
                "SE" -> "southeast"
                "S" -> "south"
                "SW" -> "southwest"
                "W" -> "west"
                "NW" -> "northwest"
                else -> null
            }
        }
    }
}
