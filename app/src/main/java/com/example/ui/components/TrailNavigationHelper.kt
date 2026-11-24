package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.net.toUri
import com.example.domain.model.Trail
import java.util.Locale

object TrailNavigationHelper {

    fun buildDirectionsUris(trail: Trail): List<Uri> =
        buildDirectionsUris(trail.centerLat, trail.centerLon, trail.name)

    fun buildDirectionsUris(lat: Double, lon: Double, label: String): List<Uri> {
        val latStr = String.format(Locale.US, "%.6f", lat)
        val lonStr = String.format(Locale.US, "%.6f", lon)
        val encodedLabel = Uri.encode(label)
        return listOf(
            "geo:0,0?q=$latStr,$lonStr($encodedLabel)".toUri(),
            "google.navigation:q=$latStr,$lonStr".toUri(),
            "https://www.google.com/maps/dir/?api=1&destination=$latStr,$lonStr".toUri()
        )
    }

    fun buildDirectionsIntent(context: Context, trail: Trail): Intent =
        buildDirectionsIntent(context, trail.centerLat, trail.centerLon, trail.name)

    fun buildDirectionsIntent(context: Context, lat: Double, lon: Double, label: String): Intent {
        val packageManager = context.packageManager
        val uris = buildDirectionsUris(lat, lon, label)
        for (uri in uris) {
            val candidate = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_DEFAULT)
            if (candidate.resolveActivity(packageManager) != null) {
                return candidate
            }
        }
        return Intent(Intent.ACTION_VIEW, uris.last())
    }

    fun buildShareDirectionsUrl(trail: Trail): String {
        return String.format(
            Locale.US,
            "https://www.google.com/maps/dir/?api=1&destination=%.5f,%.5f",
            trail.centerLat,
            trail.centerLon
        )
    }

    fun openTrailDirections(context: Context, trail: Trail) {
        openCoordinateDirections(context, trail.centerLat, trail.centerLon, trail.name)
    }

    fun openCoordinateDirections(context: Context, lat: Double, lon: Double, label: String) {
        val intent = buildDirectionsIntent(context, lat, lon, label)
        val launched = runCatching {
            context.startActivity(intent)
        }.isSuccess
        if (!launched) {
            Toast.makeText(
                context,
                "No navigation or browser app available to open directions.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
