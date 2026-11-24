package com.example.data.local

import android.content.Context
import androidx.core.content.edit
import com.example.domain.model.MeasurementUnit

class AppPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var measurementUnit: MeasurementUnit
        get() {
            val raw = prefs.getString(KEY_MEASUREMENT_UNIT, null)
            return MeasurementUnit.entries.firstOrNull { it.name == raw } ?: MeasurementUnit.IMPERIAL
        }
        set(value) {
            prefs.edit { putString(KEY_MEASUREMENT_UNIT, value.name) }
        }

    var loadOfflineMapsByDefault: Boolean
        get() = prefs.getBoolean(KEY_LOAD_OFFLINE_BY_DEFAULT, false)
        set(value) {
            prefs.edit { putBoolean(KEY_LOAD_OFFLINE_BY_DEFAULT, value) }
        }

    var defaultOfflineRegionId: String?
        get() = prefs.getString(KEY_DEFAULT_OFFLINE_REGION_ID, null)
        set(value) {
            prefs.edit {
                if (value != null) {
                    putString(KEY_DEFAULT_OFFLINE_REGION_ID, value)
                } else {
                    remove(KEY_DEFAULT_OFFLINE_REGION_ID)
                }
            }
        }

    var openRecordingScreenOnStart: Boolean
        get() = prefs.getBoolean(KEY_OPEN_RECORDING_SCREEN_ON_START, false)
        set(value) {
            prefs.edit { putBoolean(KEY_OPEN_RECORDING_SCREEN_ON_START, value) }
        }

    var voiceCuesEnabled: Boolean
        get() = prefs.getBoolean(KEY_VOICE_CUES_ENABLED, true)
        set(value) {
            prefs.edit { putBoolean(KEY_VOICE_CUES_ENABLED, value) }
        }

    var autoSyncToHealthConnect: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SYNC_HEALTH_CONNECT, true)
        set(value) {
            prefs.edit { putBoolean(KEY_AUTO_SYNC_HEALTH_CONNECT, value) }
        }

    private companion object {
        const val PREFS_NAME = "singletrack_prefs"
        const val KEY_MEASUREMENT_UNIT = "measurement_unit"
        const val KEY_LOAD_OFFLINE_BY_DEFAULT = "load_offline_by_default"
        const val KEY_DEFAULT_OFFLINE_REGION_ID = "default_offline_region_id"
        const val KEY_OPEN_RECORDING_SCREEN_ON_START = "open_recording_screen_on_start"
        const val KEY_VOICE_CUES_ENABLED = "voice_cues_enabled"
        const val KEY_AUTO_SYNC_HEALTH_CONNECT = "auto_sync_health_connect"
    }
}
