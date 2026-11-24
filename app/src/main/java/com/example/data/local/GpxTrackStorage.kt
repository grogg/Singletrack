package com.example.data.local

import android.content.Context
import com.example.data.export.GpxTrackParser
import com.example.domain.model.GpxTrack
import com.example.domain.model.GpxWaypoint
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class GpxTrackStorage(private val context: Context) {
    private val storageLock = Any()

    private fun file(): File = File(context.filesDir, FILE_NAME)
    private fun tempFile(): File = File(context.filesDir, "$FILE_NAME.tmp")

    fun saveActiveTrack(track: GpxTrack) {
        synchronized(storageLock) {
            val root = JSONObject().apply {
                put("id", track.id)
                put("name", track.name)
                put("fileName", track.fileName)
                put("isReversed", track.isReversed)
                put("loadedAtMillis", track.loadedAtMillis)

                val pointsArr = JSONArray()
                for (pt in track.points) {
                    val ptObj = JSONObject().apply {
                        put("lat", pt.lat)
                        put("lon", pt.lon)
                        if (pt.elevationFeet != null && pt.elevationFeet.isFinite()) {
                            put("eleFt", pt.elevationFeet)
                        }
                    }
                    pointsArr.put(ptObj)
                }
                put("points", pointsArr)

                val wptArr = JSONArray()
                for (wpt in track.waypoints) {
                    val wptObj = JSONObject().apply {
                        put("lat", wpt.lat)
                        put("lon", wpt.lon)
                        put("name", wpt.name)
                        if (!wpt.description.isNullOrBlank()) {
                            put("desc", wpt.description)
                        }
                    }
                    wptArr.put(wptObj)
                }
                put("waypoints", wptArr)
            }

            val tmp = tempFile()
            val target = file()
            tmp.writeText(root.toString())
            if (target.exists()) {
                target.delete()
            }
            if (!tmp.renameTo(target)) {
                target.writeText(root.toString())
                tmp.delete()
            }
        }
    }

    fun loadActiveTrack(): GpxTrack? {
        synchronized(storageLock) {
            val target = file()
            if (!target.exists()) return null
            return runCatching {
                val root = JSONObject(target.readText())
                val id = root.optString("id").takeIf { it.isNotBlank() } ?: return@runCatching null
                val name = root.optString("name").takeIf { it.isNotBlank() } ?: "GPX Route"
                val fileName = root.optString("fileName").takeIf { it.isNotBlank() } ?: "route.gpx"
                val isReversed = root.optBoolean("isReversed", false)
                val loadedAtMillis = root.optLong("loadedAtMillis", System.currentTimeMillis())

                val pointsArr = root.optJSONArray("points") ?: return@runCatching null
                val rawPointsFeet = ArrayList<Triple<Double, Double, Double?>>(pointsArr.length())
                for (i in 0 until pointsArr.length()) {
                    val pt = pointsArr.optJSONObject(i) ?: continue
                    val lat = pt.optDouble("lat", Double.NaN)
                    val lon = pt.optDouble("lon", Double.NaN)
                    if (!lat.isFinite() || !lon.isFinite()) continue
                    val eleFt = if (pt.has("eleFt") && !pt.isNull("eleFt")) {
                        pt.optDouble("eleFt", Double.NaN).takeIf { it.isFinite() }
                    } else {
                        null
                    }
                    rawPointsFeet.add(Triple(lat, lon, eleFt))
                }
                if (rawPointsFeet.isEmpty()) return@runCatching null

                val wptArr = root.optJSONArray("waypoints")
                val waypoints = mutableListOf<GpxWaypoint>()
                if (wptArr != null) {
                    for (i in 0 until wptArr.length()) {
                        val w = wptArr.optJSONObject(i) ?: continue
                        val lat = w.optDouble("lat", Double.NaN)
                        val lon = w.optDouble("lon", Double.NaN)
                        if (!lat.isFinite() || !lon.isFinite()) continue
                        waypoints.add(
                            GpxWaypoint(
                                lat = lat,
                                lon = lon,
                                name = w.optString("name", "Waypoint ${i + 1}"),
                                description = w.optString("desc").takeIf { it.isNotBlank() }
                            )
                        )
                    }
                }

                GpxTrackParser.buildTrackFromFeet(
                    id = id,
                    name = name,
                    fileName = fileName,
                    rawPointsFeet = rawPointsFeet,
                    waypoints = waypoints,
                    isReversed = isReversed,
                    loadedAtMillis = loadedAtMillis
                )
            }.getOrNull()
        }
    }

    fun clearActiveTrack() {
        synchronized(storageLock) {
            file().delete()
            tempFile().delete()
        }
    }

    companion object {
        private const val FILE_NAME = "active_gpx_track.json"
    }
}
