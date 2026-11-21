package com.example.data.repository

import com.example.data.local.GeometrySerializer
import com.example.data.local.OfflineTrailDao
import com.example.data.local.RideEntity
import com.example.domain.model.RecordedRide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class RideRepository(
    private val dao: OfflineTrailDao
) {
    fun observeRecordedRides(): Flow<List<RecordedRide>> {
        return dao.observeRides().map { list ->
            list.map { it.toDomain() }
        }
    }

    suspend fun saveRide(ride: RecordedRide) = withContext(Dispatchers.IO) {
        dao.insertRide(ride.toEntity())
    }

    suspend fun markRideSynced(rideId: String, healthConnectSessionId: String?) =
        withContext(Dispatchers.IO) {
            dao.markRideSynced(rideId, healthConnectSessionId)
        }

    suspend fun deleteRide(rideId: String) = withContext(Dispatchers.IO) {
        dao.deleteRideById(rideId)
    }

    private fun RideEntity.toDomain(): RecordedRide = RecordedRide(
        id = id,
        title = title,
        trailName = trailName,
        startTimeMillis = startTimeMillis,
        endTimeMillis = endTimeMillis,
        durationSeconds = durationSeconds,
        distanceMiles = distanceMiles,
        avgSpeedMph = avgSpeedMph,
        maxSpeedMph = maxSpeedMph,
        elevationGainFeet = elevationGainFeet,
        routePoints = GeometrySerializer.decode(routeGeometryEncoded),
        samples = GeometrySerializer.decodeSamples(samplesEncoded),
        syncedToHealthConnect = syncedToHealthConnect,
        healthConnectSessionId = healthConnectSessionId
    )

    private fun RecordedRide.toEntity(): RideEntity = RideEntity(
        id = id,
        title = title,
        trailName = trailName,
        startTimeMillis = startTimeMillis,
        endTimeMillis = endTimeMillis,
        durationSeconds = durationSeconds,
        distanceMiles = distanceMiles,
        avgSpeedMph = avgSpeedMph,
        maxSpeedMph = maxSpeedMph,
        elevationGainFeet = elevationGainFeet,
        routeGeometryEncoded = GeometrySerializer.encode(routePoints),
        samplesEncoded = GeometrySerializer.encodeSamples(samples),
        syncedToHealthConnect = syncedToHealthConnect,
        healthConnectSessionId = healthConnectSessionId
    )
}
