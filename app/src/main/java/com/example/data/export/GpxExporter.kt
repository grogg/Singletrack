package com.example.data.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.domain.model.RecordedRide
import java.io.File
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale

object GpxExporter {

    fun buildSuggestedFileName(ride: RecordedRide): String {
        val cleanTitle = ride.title
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "singletrack_ride" }
            .take(40)
        val sdf = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US)
        val dateStamp = sdf.format(Date(ride.startTimeMillis))
        return "${cleanTitle}_${dateStamp}.gpx"
    }

    fun buildGpxXml(ride: RecordedRide): String {
        val sb = StringBuilder(4096)
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append(
            "<gpx version=\"1.1\" creator=\"Singletrack\" " +
                "xmlns=\"http://www.topografix.com/GPX/1/1\" " +
                "xmlns:gpxtpx=\"http://www.garmin.com/xmlschemas/TrackPointExtension/v1\" " +
                "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
                "xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 " +
                "http://www.topografix.com/GPX/1/1/gpx.xsd\">\n"
        )
        sb.append("  <metadata>\n")
        sb.append("    <name>${escapeXml(ride.title)}</name>\n")
        val desc = buildString {
            if (!ride.trailName.isNullOrBlank()) {
                append("Trail: ${ride.trailName}. ")
            }
            append(
                String.format(
                    Locale.US,
                    "Distance: %.2f mi, Avg Speed: %.1f mph, Elevation Gain: %.0f ft",
                    ride.distanceMiles,
                    ride.avgSpeedMph,
                    ride.elevationGainFeet
                )
            )
            if (ride.avgCadenceRpm != null) {
                append(", Avg Cadence: ${ride.avgCadenceRpm} rpm")
            }
            if (!ride.sensorName.isNullOrBlank()) {
                append(" (Sensor: ${ride.sensorName})")
            }
        }
        sb.append("    <desc>${escapeXml(desc)}</desc>\n")
        sb.append("    <time>${Instant.ofEpochMilli(ride.startTimeMillis)}</time>\n")
        sb.append("  </metadata>\n")

        sb.append("  <trk>\n")
        sb.append("    <name>${escapeXml(ride.title)}</name>\n")
        sb.append("    <type>mountain_biking</type>\n")
        sb.append("    <trkseg>\n")

        if (ride.samples.isNotEmpty()) {
            ride.samples.forEach { sample ->
                sb.append(
                    String.format(
                        Locale.US,
                        "      <trkpt lat=\"%.6f\" lon=\"%.6f\">\n",
                        sample.lat,
                        sample.lon
                    )
                )
                sample.altitudeMeters?.let { alt ->
                    sb.append(String.format(Locale.US, "        <ele>%.1f</ele>\n", alt))
                }
                sb.append("        <time>${Instant.ofEpochMilli(sample.timestampMillis)}</time>\n")
                if (sample.cadenceRpm != null) {
                    sb.append("        <extensions>\n")
                    sb.append("          <gpxtpx:TrackPointExtension>\n")
                    sb.append("            <gpxtpx:cad>${sample.cadenceRpm}</gpxtpx:cad>\n")
                    sb.append("          </gpxtpx:TrackPointExtension>\n")
                    sb.append("        </extensions>\n")
                }
                sb.append("      </trkpt>\n")
            }
        } else if (ride.routePoints.isNotEmpty()) {
            val totalPoints = ride.routePoints.size
            val spanMillis = (ride.endTimeMillis - ride.startTimeMillis).coerceAtLeast(1000L)
            ride.routePoints.forEachIndexed { idx, pt ->
                val ts = if (totalPoints > 1) {
                    ride.startTimeMillis + (spanMillis * idx / (totalPoints - 1))
                } else {
                    ride.startTimeMillis
                }
                sb.append(
                    String.format(
                        Locale.US,
                        "      <trkpt lat=\"%.6f\" lon=\"%.6f\">\n",
                        pt.lat,
                        pt.lon
                    )
                )
                sb.append("        <time>${Instant.ofEpochMilli(ts)}</time>\n")
                sb.append("      </trkpt>\n")
            }
        }

        sb.append("    </trkseg>\n")
        sb.append("  </trk>\n")
        sb.append("</gpx>\n")
        return sb.toString()
    }

    fun writeGpxToCacheFile(context: Context, ride: RecordedRide): File {
        val dir = File(context.cacheDir, "gpx_exports").apply { mkdirs() }
        // Keep cache clean by removing older transient export files
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 24 * 60 * 60 * 1000L }
            ?.forEach { it.delete() }
        val file = File(dir, buildSuggestedFileName(ride))
        file.writeText(buildGpxXml(ride), Charsets.UTF_8)
        return file
    }

    fun buildShareGpxIntent(context: Context, ride: RecordedRide): Intent {
        val file = writeGpxToCacheFile(context, ride)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/gpx+xml"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "${ride.title} (GPX)")
            putExtra(
                Intent.EXTRA_TEXT,
                String.format(
                    Locale.US,
                    "%s — %.2f mi, %.0f ft climb (Singletrack GPX)",
                    ride.title,
                    ride.distanceMiles,
                    ride.elevationGainFeet
                )
            )
            clipData = ClipData.newRawUri("GPX Track", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(sendIntent, "Share Ride GPX")
    }

    fun writeGpxToUri(context: Context, uri: Uri, ride: RecordedRide) {
        val xml = buildGpxXml(ride)
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(xml.toByteArray(Charsets.UTF_8))
            stream.flush()
        } ?: error("Unable to open destination output stream")
    }

    private fun escapeXml(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
