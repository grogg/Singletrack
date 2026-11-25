package com.example

import android.Manifest
import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.data.export.GpxTrackParser
import com.example.data.local.GpxTrackStorage
import com.example.domain.model.RideRecordingStatus
import com.example.ui.viewmodel.TrailViewModel
import org.junit.Assert.assertEquals
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

    @Test
    fun `starting gpx navigation automatically starts ride recording when idle`() {
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

            val state = viewModel.uiState.value
            assertTrue(state.gpxNavigation.isNavigating)
            assertEquals(RideRecordingStatus.RECORDING, state.activeRide.status)
            assertEquals("Captain Ahab Loop", state.activeRide.targetTrailName)
        } finally {
            viewModel.stopGpxNavigation()
            viewModel.discardRide()
            store.clear()
            storage.clearActiveTrack()
        }
    }

    @Test
    fun `starting gpx navigation preserves an already active or paused ride`() {
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
            viewModel.pauseRide()
            ShadowLooper.idleMainLooper()

            viewModel.startGpxNavigation()
            ShadowLooper.idleMainLooper()

            val afterPausedNav = viewModel.uiState.value
            assertTrue(afterPausedNav.gpxNavigation.isNavigating)
            assertEquals(RideRecordingStatus.PAUSED, afterPausedNav.activeRide.status)
            assertEquals("Existing Ride", afterPausedNav.activeRide.targetTrailName)
            assertEquals(originalStartTime, afterPausedNav.activeRide.startTimeMillis)
        } finally {
            viewModel.stopGpxNavigation()
            viewModel.discardRide()
            store.clear()
            storage.clearActiveTrack()
        }
    }
}
