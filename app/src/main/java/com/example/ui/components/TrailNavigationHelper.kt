package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.net.toUri
import com.example.domain.model.Trail
import java.util.Locale

object TrailNavigationHelper {

    fun buildDirectionsUris(trail: Trail): List<Uri> {
        val lat = String.format(Locale.US, "%.6f", trail.centerLat)
        val lon = String.format(Locale.US, "%.6f", trail.centerLon)
        val encodedLabel = Uri.encode(trail.name)
        return listOf(
            "geo:0,0?q=$lat,$lon($encodedLabel)".toUri(),
            "google.navigation:q=$lat,$lon".toUri(),
            "https://www.google.com/maps/dir/?api=1&destination=$lat,$lon".toUri()
        )
    }

    fun buildDirectionsIntent(context: Context, trail: Trail): Intent {
        val packageManager = context.packageManager
        val uris = buildDirectionsUris(trail)
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
        val intent = buildDirectionsIntent(context, trail)
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
