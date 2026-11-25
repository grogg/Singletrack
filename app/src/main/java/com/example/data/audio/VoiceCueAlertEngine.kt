package com.example.data.audio

import com.example.domain.model.GpxNavigationState
import com.example.domain.model.GpxRouteStatus
import com.example.domain.model.MeasurementUnit
import com.example.domain.model.RideRecordingStatus
import com.example.ui.components.FormatUtils
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

data class GpxVoiceCue(
    val text: String,
    val interrupt: Boolean
)

class VoiceCueAlertEngine {
    private var wasOffRoute = false
    private var lastOffRouteAlertMillis = -1L
    private var lastAnnouncedDistanceMilestone = 0
    private var lastSpokenStatus: GpxRouteStatus = GpxRouteStatus.IDLE
    private var lastSpokenTurnBucket: String? = null
    private var lastSpokenTurnStage: Int = 0
    private var announcedInitialStart = false
    private var announcedFinish = false

    fun evaluateGpxNavigationCue(
        offRouteAlertsEnabled: Boolean,
        fullVoiceNavEnabled: Boolean,
        previousState: GpxNavigationState,
        newState: GpxNavigationState,
        unit: MeasurementUnit,
        nowMillis: Long = System.currentTimeMillis()
    ): GpxVoiceCue? {
        if (!newState.isNavigating) {
            resetGpxState()
            return null
        }

        if (newState.routeStatus == GpxRouteStatus.OFF_ROUTE) {
            val offRouteCue = evaluateGpxOffRouteCue(
                isEnabled = offRouteAlertsEnabled || fullVoiceNavEnabled,
                previousState = previousState,
                newState = newState,
                unit = unit,
                nowMillis = nowMillis
            )
            lastSpokenStatus = GpxRouteStatus.OFF_ROUTE
            return offRouteCue?.let { GpxVoiceCue(text = it, interrupt = true) }
        }

        val returningFromOffRoute = wasOffRoute || previousState.routeStatus == GpxRouteStatus.OFF_ROUTE
        wasOffRoute = false

        if (!fullVoiceNavEnabled) {
            lastSpokenStatus = newState.routeStatus
            return null
        }

        return when (newState.routeStatus) {
            GpxRouteStatus.IDLE -> {
                if (!announcedInitialStart) {
                    announcedInitialStart = true
                    lastSpokenStatus = GpxRouteStatus.IDLE
                    val trackName = newState.track?.name?.takeIf { it.isNotBlank() }
                    val text = if (trackName != null) {
                        "Voice navigation started for $trackName."
                    } else {
                        "Voice navigation started."
                    }
                    GpxVoiceCue(text = text, interrupt = false)
                } else {
                    null
                }
            }

            GpxRouteStatus.APPROACHING_START -> {
                if (lastSpokenStatus != GpxRouteStatus.APPROACHING_START) {
                    lastSpokenStatus = GpxRouteStatus.APPROACHING_START
                    lastSpokenTurnBucket = "APPROACHING_START"
                    lastSpokenTurnStage = 1
                    val spokenCardinal = expandCardinalDirection(newState.bearingCardinal)
                    val distText = newState.distanceToCueMiles
                        ?.takeIf { it.isFinite() && it > 0.01 }
                        ?.let { formatCueDistance(it, unit) }
                    val text = buildString {
                        if (spokenCardinal != null) {
                            append("Head $spokenCardinal to trail start")
                        } else {
                            append("Head to trail start")
                        }
                        if (distText != null) {
                            append(", $distText")
                        }
                        append('.')
                    }
                    GpxVoiceCue(text = text, interrupt = false)
                } else {
                    null
                }
            }

            GpxRouteStatus.FINISHED -> {
                if (!announcedFinish && lastSpokenStatus != GpxRouteStatus.FINISHED) {
                    announcedFinish = true
                    lastSpokenStatus = GpxRouteStatus.FINISHED
                    lastSpokenTurnBucket = "FINISHED"
                    lastSpokenTurnStage = 2
                    GpxVoiceCue(
                        text = "Route complete! You have reached the finish.",
                        interrupt = true
                    )
                } else {
                    null
                }
            }

            GpxRouteStatus.ON_TRACK -> {
                lastSpokenStatus = GpxRouteStatus.ON_TRACK
                val instruction = newState.turnInstruction.trim()
                val isContinue = instruction.startsWith("Continue ", ignoreCase = true)
                val bucket = if (isContinue) "CONTINUE" else instruction
                val distMiles = newState.distanceToCueMiles?.takeIf { it.isFinite() && it >= 0.0 }

                val stage = when {
                    isContinue -> 1
                    distMiles != null && distMiles <= IMMINENT_TURN_MILES -> 2
                    else -> 1
                }

                val bucketChanged = bucket != lastSpokenTurnBucket
                val escalatedToImminent = !isContinue && bucket == lastSpokenTurnBucket && stage > lastSpokenTurnStage
                if (!returningFromOffRoute && !bucketChanged && !escalatedToImminent) {
                    return null
                }

                lastSpokenTurnBucket = bucket
                lastSpokenTurnStage = stage

                val body = formatTurnInstructionForVoice(
                    instruction = instruction,
                    isContinue = isContinue,
                    cardinal = newState.bearingCardinal,
                    distanceMiles = distMiles,
                    stage = stage,
                    unit = unit
                )

                val text = if (returningFromOffRoute) {
                    "Back on track. $body"
                } else {
                    body
                }
                GpxVoiceCue(text = text, interrupt = returningFromOffRoute || stage == 2)
            }

            GpxRouteStatus.OFF_ROUTE -> null
        }
    }

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
        lastSpokenStatus = GpxRouteStatus.IDLE
        lastSpokenTurnBucket = null
        lastSpokenTurnStage = 0
        announcedInitialStart = false
        announcedFinish = false
    }

    companion object {
        const val OFF_ROUTE_REENTRY_COOLDOWN_MS = 15_000L
        const val OFF_ROUTE_REPEAT_INTERVAL_MS = 45_000L
        const val IMMINENT_TURN_MILES = 0.05
        private const val MILESTONE_EPSILON = 1e-6

        fun formatTurnInstructionForVoice(
            instruction: String,
            isContinue: Boolean,
            cardinal: String?,
            distanceMiles: Double?,
            stage: Int,
            unit: MeasurementUnit
        ): String {
            if (isContinue) {
                val spokenCardinal = expandCardinalDirection(cardinal)
                return if (spokenCardinal != null) {
                    "Continue $spokenCardinal along track."
                } else {
                    "Continue along track."
                }
            }

            if (instruction.equals("Finish ahead", ignoreCase = true)) {
                val distText = distanceMiles
                    ?.takeIf { it > 0.005 }
                    ?.let { formatCueDistance(it, unit) }
                return if (distText != null) {
                    "Finish ahead in $distText."
                } else {
                    "Finish ahead."
                }
            }

            if (stage >= 2) {
                return "$instruction ahead."
            }

            val distText = distanceMiles
                ?.takeIf { it > 0.005 }
                ?.let { formatCueDistance(it, unit) }
            return if (distText != null) {
                "In $distText, ${instruction.replaceFirstChar { it.lowercase(Locale.US) }}."
            } else {
                "$instruction."
            }
        }

        fun formatCueDistance(miles: Double, unit: MeasurementUnit): String {
            return if (unit == MeasurementUnit.METRIC) {
                val meters = miles * FormatUtils.MILES_TO_KM * 1000.0
                if (meters >= 1000.0) {
                    String.format(Locale.US, "%.1f kilometers", meters / 1000.0)
                } else {
                    val roundedMeters = ((meters / 10.0).roundToInt() * 10).coerceAtLeast(10)
                    "$roundedMeters meters"
                }
            } else {
                val feet = miles * 5280.0
                if (feet >= 1000.0) {
                    String.format(Locale.US, "%.1f miles", miles)
                } else {
                    val roundedFeet = ((feet / 50.0).roundToInt() * 50).coerceAtLeast(50)
                    "$roundedFeet feet"
                }
            }
        }

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
