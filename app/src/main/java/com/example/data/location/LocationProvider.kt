package com.example.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

sealed class DeviceLocationResult {
    data class Success(val lat: Double, val lon: Double, val label: String) : DeviceLocationResult()
    data object PermissionMissing : DeviceLocationResult()
    data object Unavailable : DeviceLocationResult()
}

class LocationProvider(private val context: Context) {
    private val fusedClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(resolveLabel: Boolean = true): DeviceLocationResult {
        if (!hasLocationPermission()) {
            return DeviceLocationResult.PermissionMissing
        }

        val lastKnown = withTimeoutOrNull(600L) {
            fetchLastKnownLocation()
        }
        val isRecentAccurateLastKnown = lastKnown != null &&
            (System.currentTimeMillis() - lastKnown.time) in 0L..MAX_CACHED_FIX_AGE_MS &&
            (!lastKnown.hasAccuracy() || lastKnown.accuracy <= 100f)

        val current = if (isRecentAccurateLastKnown) {
            lastKnown
        } else {
            withTimeoutOrNull(4500L) {
                fetchCurrentFix()
            } ?: lastKnown
        }

        if (current == null) {
            return DeviceLocationResult.Unavailable
        }

        val label = if (resolveLabel) {
            reverseGeocodeLabel(current.latitude, current.longitude)
        } else {
            String.format(Locale.US, "%.3f°, %.3f°", current.latitude, current.longitude)
        }
        return DeviceLocationResult.Success(
            lat = current.latitude,
            lon = current.longitude,
            label = label
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun fetchCurrentFix(): Location? = suspendCancellableCoroutine { cont ->
        val cts = CancellationTokenSource()
        cont.invokeOnCancellation { cts.cancel() }

        val priority = if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }

        fusedClient.getCurrentLocation(priority, cts.token)
            .addOnSuccessListener { location ->
                if (cont.isActive) cont.resume(location)
            }
            .addOnFailureListener {
                if (cont.isActive) cont.resume(null)
            }
    }

    @SuppressLint("MissingPermission")
    private suspend fun fetchLastKnownLocation(): Location? = suspendCancellableCoroutine { cont ->
        fusedClient.lastLocation
            .addOnSuccessListener { location ->
                if (cont.isActive) cont.resume(location)
            }
            .addOnFailureListener {
                if (cont.isActive) cont.resume(null)
            }
    }

    suspend fun reverseGeocodeLabel(lat: Double, lon: Double): String = withContext(Dispatchers.IO) {
        try {
            if (Geocoder.isPresent()) {
                val geocoder = Geocoder(context, Locale.getDefault())
                val formatted = withTimeoutOrNull(2000L) {
                    @Suppress("DEPRECATION")
                    val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        suspendCancellableCoroutine { cont ->
                            geocoder.getFromLocation(lat, lon, 1) { list ->
                                if (cont.isActive) cont.resume(list)
                            }
                        }
                    } else {
                        geocoder.getFromLocation(lat, lon, 1)
                    }
                    val first = addresses?.firstOrNull()
                    if (first != null) {
                        val city = first.locality ?: first.subAdminArea ?: first.featureName
                        val state = first.adminArea
                        listOfNotNull(city, state).distinct().joinToString(", ")
                    } else {
                        null
                    }
                }
                if (!formatted.isNullOrBlank()) return@withContext formatted
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            // Fall through to coordinate label
        }
        String.format(Locale.US, "%.3f°, %.3f°", lat, lon)
    }

    private companion object {
        const val MAX_CACHED_FIX_AGE_MS = 120_000L
    }
}
