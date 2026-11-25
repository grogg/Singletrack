package com.example

import android.Manifest
import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.data.export.GpxTrackParser
import com.example.data.local.GpxTrackStorage
import com.example.data.local.SingletrackDatabase
import com.example.domain.model.RecordedRide
import com.example.domain.model.RideRecordingStatus
import com.example.ui.viewmodel.TrailViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrailViewModelRobolectricTest {

    private fun awaitSavedRide(
        viewModel: TrailViewModel,
        title: String,
        timeoutMs: Long = 10_000L
    ): RecordedRide {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper()
            val match = viewModel.uiState.value.recordedRides.firstOrNull { it.title == title }
            if (match != null) return match
            Thread.sleep(25L)
        }
        return viewModel.uiState.value.recordedRides.first { it.title == title }
    }

    @Test
    fun `starting gpx navigation starts ride recording and stopping navigation finishes and saves it`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)

        val storage = GpxTrackStorage(app)
        val track = GpxTrackParser.buildTrack(
            id = "auto-record-track",
            name = "Captain Ahab Loop",
            fileName = "ahab.gpx",
            rawPoints = listOf(
                Triple(38.5250, -109.5930, 1280.0),
                Triple(38.5290, -109.5970, 1340.0)
            )
        )
        storage.saveActiveTrack(track)

        val store = ViewModelStore()
        val viewModel = ViewModelProvider(
            store,
            ViewModelProvider.AndroidViewModelFactory(app)
        )[TrailViewModel::class.java]

        try {
            val deadline = System.currentTimeMillis() + 10_000L
            while (viewModel.uiState.value.gpxNavigation.track == null && System.currentTimeMillis() < deadline) {
                ShadowLooper.idleMainLooper()
                Thread.sleep(25L)
            }
            assertNotNull(
                "Expected saved GPX track to load",
                viewModel.uiState.value.gpxNavigation.track
            )
            assertEquals(RideRecordingStatus.IDLE, viewModel.uiState.value.activeRide.status)

            viewModel.startGpxNavigation()
            ShadowLooper.idleMainLooper()

            val startedState = viewModel.uiState.value
            assertTrue(startedState.gpxNavigation.isNavigating)
            assertEquals(RideRecordingStatus.RECORDING, startedState.activeRide.status)
            assertEquals("Captain Ahab Loop", startedState.activeRide.targetTrailName)

            viewModel.stopGpxNavigation()
            val savedRide = awaitSavedRide(viewModel, "Captain Ahab Loop Ride")

            val stoppedState = viewModel.uiState.value
            assertFalse(stoppedState.gpxNavigation.isNavigating)
            assertEquals(RideRecordingStatus.IDLE, stoppedState.activeRide.status)
            assertEquals("Captain Ahab Loop Ride", savedRide.title)
            assertEquals("Captain Ahab Loop", savedRide.trailName)
        } finally {
            viewModel.stopGpxNavigation()
            viewModel.discardRide()
            store.clear()
            storage.clearActiveTrack()
            Thread { SingletrackDatabase.getInstance(app).clearAllTables() }.apply {
                start()
                join()
            }
        }
    }

    @Test
    fun `starting gpx navigation preserves an already active or paused ride and stopping saves it`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)

        val storage = GpxTrackStorage(app)
        val track = GpxTrackParser.buildTrack(
            id = "preserve-ride-track",
            name = "Hymasa Trail",
            fileName = "hymasa.gpx",
            rawPoints = listOf(
                Triple(38.5320, -109.5910, 1210.0),
                Triple(38.5360, -109.5960, 1270.0)
            )
        )
        storage.saveActiveTrack(track)

        val store = ViewModelStore()
        val viewModel = ViewModelProvider(
            store,
            ViewModelProvider.AndroidViewModelFactory(app)
        )[TrailViewModel::class.java]

        try {
            val deadline = System.currentTimeMillis() + 10_000L
            while (viewModel.uiState.value.gpxNavigation.track == null && System.currentTimeMillis() < deadline) {
                ShadowLooper.idleMainLooper()
                Thread.sleep(25L)
            }
            assertNotNull(viewModel.uiState.value.gpxNavigation.track)

            viewModel.startRide(trailName = "Existing Ride")
            ShadowLooper.idleMainLooper()
            val originalStartTime = viewModel.uiState.value.activeRide.startTimeMillis

            viewModel.startGpxNavigation()
            ShadowLooper.idleMainLooper()

            val afterRecordingNav = viewModel.uiState.value
            assertTrue(afterRecordingNav.gpxNavigation.isNavigating)
            assertEquals(RideRecordingStatus.RECORDING, afterRecordingNav.activeRide.status)
            assertEquals("Existing Ride", afterRecordingNav.activeRide.targetTrailName)
            assertEquals(originalStartTime, afterRecordingNav.activeRide.startTimeMillis)

            viewModel.stopGpxNavigation()
            val savedActive = awaitSavedRide(viewModel, "Existing Ride Ride")
            assertEquals(RideRecordingStatus.IDLE, viewModel.uiState.value.activeRide.status)
            assertEquals("Existing Ride", savedActive.trailName)

            viewModel.startRide(trailName = "Paused Ride")
            viewModel.pauseRide()
            ShadowLooper.idleMainLooper()
            val pausedStartTime = viewModel.uiState.value.activeRide.startTimeMillis

            viewModel.startGpxNavigation()
            ShadowLooper.idleMainLooper()

            val afterPausedNav = viewModel.uiState.value
            assertTrue(afterPausedNav.gpxNavigation.isNavigating)
            assertEquals(RideRecordingStatus.PAUSED, afterPausedNav.activeRide.status)
            assertEquals("Paused Ride", afterPausedNav.activeRide.targetTrailName)
            assertEquals(pausedStartTime, afterPausedNav.activeRide.startTimeMillis)

            viewModel.stopGpxNavigation()
            val savedPaused = awaitSavedRide(viewModel, "Paused Ride Ride")
            assertFalse(viewModel.uiState.value.gpxNavigation.isNavigating)
            assertEquals(RideRecordingStatus.IDLE, viewModel.uiState.value.activeRide.status)
            assertEquals("Paused Ride", savedPaused.trailName)
        } finally {
            viewModel.stopGpxNavigation()
            viewModel.discardRide()
            store.clear()
            storage.clearActiveTrack()
            Thread { SingletrackDatabase.getInstance(app).clearAllTables() }.apply {
                start()
                join()
            }
        }
    }

    @Test
    fun `clearing idle gpx track does not finish an unrelated active ride`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)

        val storage = GpxTrackStorage(app)
        val track = GpxTrackParser.buildTrack(
            id = "idle-clear-track",
            name = "Porcupine Rim",
            fileName = "porcupine.gpx",
            rawPoints = listOf(
                Triple(38.5700, -109.4900, 1450.0),
                Triple(38.5740, -109.4940, 1410.0)
            )
        )
        storage.saveActiveTrack(track)

        val store = ViewModelStore()
        val viewModel = ViewModelProvider(
            store,
            ViewModelProvider.AndroidViewModelFactory(app)
        )[TrailViewModel::class.java]

        try {
            val deadline = System.currentTimeMillis() + 10_000L
            while (viewModel.uiState.value.gpxNavigation.track == null && System.currentTimeMillis() < deadline) {
                ShadowLooper.idleMainLooper()
                Thread.sleep(25L)
            }
            assertNotNull(viewModel.uiState.value.gpxNavigation.track)

            viewModel.startRide(trailName = "Standalone Ride")
            ShadowLooper.idleMainLooper()

            viewModel.clearGpxTrack()
            ShadowLooper.idleMainLooper()

            val state = viewModel.uiState.value
            assertEquals(null, state.gpxNavigation.track)
            assertEquals(RideRecordingStatus.RECORDING, state.activeRide.status)
            assertEquals("Standalone Ride", state.activeRide.targetTrailName)
        } finally {
            viewModel.discardRide()
            store.clear()
            storage.clearActiveTrack()
            Thread { SingletrackDatabase.getInstance(app).clearAllTables() }.apply {
                start()
                join()
            }
        }
    }
}
