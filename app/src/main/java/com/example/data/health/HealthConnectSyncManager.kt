package com.example.data.health

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseRoute
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Velocity
import com.example.domain.model.RecordedRide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

enum class HealthConnectAvailability {
    AVAILABLE,
    UPDATE_REQUIRED,
    UNAVAILABLE
}

data class HealthConnectStatus(
    val availability: HealthConnectAvailability,
    val permissionsGranted: Boolean
)

class HealthConnectSyncManager(private val context: Context) {

    companion object {
        val REQUIRED_PERMISSIONS: Set<String> = setOf(
            HealthPermission.getWritePermission(ExerciseSessionRecord::class),
            HealthPermission.PERMISSION_WRITE_EXERCISE_ROUTE,
            HealthPermission.getWritePermission(DistanceRecord::class),
            HealthPermission.getWritePermission(SpeedRecord::class)
        )

        val CORE_PERMISSIONS: Set<String> = setOf(
            HealthPermission.getWritePermission(ExerciseSessionRecord::class),
            HealthPermission.getWritePermission(DistanceRecord::class)
        )
    }

    fun getAvailability(): HealthConnectAvailability {
        return when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                HealthConnectAvailability.UPDATE_REQUIRED
            else -> HealthConnectAvailability.UNAVAILABLE
        }
    }

    private fun getClientOrNull(): HealthConnectClient? {
        return if (getAvailability() == HealthConnectAvailability.AVAILABLE) {
            HealthConnectClient.getOrCreate(context)
        } else {
            null
        }
    }

    suspend fun getStatus(): HealthConnectStatus = withContext(Dispatchers.IO) {
        val availability = getAvailability()
        if (availability != HealthConnectAvailability.AVAILABLE) {
            return@withContext HealthConnectStatus(availability, permissionsGranted = false)
        }
        val client = getClientOrNull()
            ?: return@withContext HealthConnectStatus(availability, permissionsGranted = false)
        val granted = try {
            client.permissionController.getGrantedPermissions()
        } catch (_: Exception) {
            emptySet()
        }
        HealthConnectStatus(
            availability = availability,
            permissionsGranted = granted.containsAll(CORE_PERMISSIONS)
        )
    }

    suspend fun syncRide(ride: RecordedRide): Result<String?> = withContext(Dispatchers.IO) {
        val client = getClientOrNull()
            ?: return@withContext Result.failure(
                IllegalStateException("Health Connect is not available on this device.")
            )

        try {
            val granted = client.permissionController.getGrantedPermissions()
            if (!granted.contains(HealthPermission.getWritePermission(ExerciseSessionRecord::class))) {
                return@withContext Result.failure(
                    SecurityException("Health Connect exercise permission is not granted.")
                )
            }

            val startInstant = Instant.ofEpochMilli(ride.startTimeMillis)
            val endInstant = Instant.ofEpochMilli(
                ride.endTimeMillis.coerceAtLeast(ride.startTimeMillis + 1000L)
            )
            val zoneRules = ZoneId.systemDefault().rules
            val startOffset = zoneRules.getOffset(startInstant)
            val endOffset = zoneRules.getOffset(endInstant)

            val canWriteRoute = granted.contains(HealthPermission.PERMISSION_WRITE_EXERCISE_ROUTE)
            val exerciseRoute = if (canWriteRoute && ride.samples.size >= 2) {
                val sortedSamples = ride.samples
                    .filter { it.timestampMillis in ride.startTimeMillis..ride.endTimeMillis }
                    .distinctBy { it.timestampMillis }
                    .sortedBy { it.timestampMillis }
                if (sortedSamples.size >= 2) {
                    ExerciseRoute(
                        route = sortedSamples.map { sample ->
                            ExerciseRoute.Location(
                                time = Instant.ofEpochMilli(sample.timestampMillis),
                                latitude = sample.lat,
                                longitude = sample.lon,
                                altitude = sample.altitudeMeters?.let { Length.meters(it) }
                            )
                        }
                    )
                } else {
                    null
                }
            } else {
                null
            }

            val notes = buildString {
                if (!ride.trailName.isNullOrBlank()) {
                    append("Trail: ${ride.trailName}. ")
                }
                append(
                    String.format(
                        Locale.US,
                        "Distance: %.2f mi, Avg: %.1f mph, Elev Gain: %.0f ft (Recorded with Singletrack)",
                        ride.distanceMiles,
                        ride.avgSpeedMph,
                        ride.elevationGainFeet
                    )
                )
            }

            val recordsToInsert = mutableListOf<Record>()

            val sessionRecord = ExerciseSessionRecord(
                startTime = startInstant,
                startZoneOffset = startOffset,
                endTime = endInstant,
                endZoneOffset = endOffset,
                exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
                title = ride.title,
                notes = notes,
                exerciseRoute = exerciseRoute,
                metadata = Metadata.activelyRecorded(
                    device = Device(type = Device.TYPE_PHONE),
                    clientRecordId = "singletrack_session_${ride.id}"
                )
            )
            recordsToInsert.add(sessionRecord)

            val canWriteDistance = granted.contains(
                HealthPermission.getWritePermission(DistanceRecord::class)
            )
            if (canWriteDistance && ride.distanceMiles > 0.0) {
                recordsToInsert.add(
                    DistanceRecord(
                        startTime = startInstant,
                        startZoneOffset = startOffset,
                        endTime = endInstant,
                        endZoneOffset = endOffset,
                        distance = Length.meters(ride.distanceMiles * 1609.344),
                        metadata = Metadata.activelyRecorded(
                            device = Device(type = Device.TYPE_PHONE),
                            clientRecordId = "singletrack_distance_${ride.id}"
                        )
                    )
                )
            }

            val canWriteSpeed = granted.contains(
                HealthPermission.getWritePermission(SpeedRecord::class)
            )
            val speedSamples = ride.samples
                .filter { it.speedMps != null && it.speedMps >= 0f && it.timestampMillis in ride.startTimeMillis..ride.endTimeMillis }
                .distinctBy { it.timestampMillis }
                .sortedBy { it.timestampMillis }
            if (canWriteSpeed && speedSamples.isNotEmpty()) {
                recordsToInsert.add(
                    SpeedRecord(
                        startTime = startInstant,
                        startZoneOffset = startOffset,
                        endTime = endInstant,
                        endZoneOffset = endOffset,
                        samples = speedSamples.map { sample ->
                            SpeedRecord.Sample(
                                time = Instant.ofEpochMilli(sample.timestampMillis),
                                speed = Velocity.metersPerSecond((sample.speedMps ?: 0f).toDouble())
                            )
                        },
                        metadata = Metadata.activelyRecorded(
                            device = Device(type = Device.TYPE_PHONE),
                            clientRecordId = "singletrack_speed_${ride.id}"
                        )
                    )
                )
            }

            val response = client.insertRecords(recordsToInsert)
            Result.success(response.recordIdsList.firstOrNull())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun buildInstallHealthConnectIntent(): Intent {
        val uri =
            "market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding"
                .toUri()
        return Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.android.vending")
            putExtra("overlay", true)
            putExtra("callerId", context.packageName)
        }
    }

    fun buildOpenHealthConnectSettingsIntent(): Intent {
        return Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
    }

    fun buildOpenGoogleFitIntent(): Intent? {
        return context.packageManager.getLaunchIntentForPackage("com.google.android.apps.fitness")
    }
}
