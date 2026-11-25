package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.view.WindowManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import com.example.data.export.GpxExporter
import com.example.data.export.GpxTrackParser
import com.example.data.health.HealthConnectAvailability
import com.example.data.health.HealthConnectStatus
import com.example.data.local.AppPreferences
import com.example.data.local.GpxTrackStorage
import com.example.data.remote.SingletrackImageLoader
import com.example.data.repository.GpxNavigationEngine
import com.example.domain.model.ActiveRideMetrics
import com.example.domain.model.BleSensorUiState
import com.example.domain.model.GeoPoint
import com.example.domain.model.GpxNavigationState
import com.example.domain.model.GpxWaypoint
import com.example.domain.model.MeasurementUnit
import com.example.domain.model.RecordedRide
import com.example.domain.model.Trail
import com.example.domain.model.TrailDifficulty
import com.example.ui.components.TrailNavigationHelper
import com.example.ui.components.UnitSettingsDialog
import com.example.ui.screens.LiveRideCockpitScreen
import com.example.ui.screens.RideScreen
import com.example.ui.theme.SingletrackTheme
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SingletrackAppRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `app name matches Singletrack MTB`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Singletrack MTB", appName)
    }

    @Test
    fun `gpx exporter creates FileProvider share intent`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val ride = RecordedRide(
            id = "r1",
            title = "Morning Trail Loop",
            trailName = "Ridge Trail",
            startTimeMillis = 1700000000000L,
            endTimeMillis = 1700000600000L,
            durationSeconds = 600L,
            distanceMiles = 3.4,
            avgSpeedMph = 11.2,
            maxSpeedMph = 18.5,
            elevationGainFeet = 420.0,
            routePoints = listOf(GeoPoint(38.57, -109.55), GeoPoint(38.58, -109.54))
        )

        val chooser = GpxExporter.buildShareGpxIntent(context, ride)
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION")
        val inner = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertNotNull(inner)
        assertEquals("application/gpx+xml", inner?.type)
        assertTrue((inner?.flags ?: 0) and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun `activities restrict orientation to portrait in manifest`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        @Suppress("DEPRECATION")
        val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_ACTIVITIES.toLong())
            )
        } else {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_ACTIVITIES)
        }
        val mainActivity = pkgInfo.activities?.firstOrNull { it.name == MainActivity::class.java.name }
        assertNotNull(mainActivity)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, mainActivity?.screenOrientation)
        @Suppress("DEPRECATION")
        assertEquals(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
            (mainActivity?.softInputMode ?: 0) and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST
        )
    }

    @Test
    fun `activities enable edge-to-edge display cutout mode`() {
        val controller = Robolectric.buildActivity(PermissionsRationaleActivity::class.java).setup()
        val activity = controller.get()
        val cutoutMode = activity.window.attributes.layoutInDisplayCutoutMode
        assertTrue(
            "Expected shortEdges or always cutout mode, got $cutoutMode",
            cutoutMode == WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES ||
                cutoutMode == WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        )
    }

    @Test
    fun `trail navigation helper builds geo navigation and share links`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val trail = Trail(
            osmKey = "way/42",
            osmType = "way",
            osmId = 42L,
            name = "Slickrock Practice Loop",
            difficulty = TrailDifficulty.INTERMEDIATE,
            rawDifficultyTag = "mtb:scale=2",
            surface = "Rock",
            centerLat = 38.57331,
            centerLon = -109.51944,
            distanceFromUserMiles = 1.8,
            bearingDegrees = 45.0,
            bearingCardinal = "NE",
            lengthMiles = 2.1,
            operatorName = null,
            networkName = null,
            description = null,
            oneway = null,
            uphillScale = null,
            isDesignatedMtb = true
        )

        val uris = TrailNavigationHelper.buildDirectionsUris(trail)
        assertEquals("geo", uris[0].scheme)
        assertEquals("google.navigation", uris[1].scheme)
        assertEquals("https", uris[2].scheme)
        val intent = TrailNavigationHelper.buildDirectionsIntent(context, trail)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertTrue(TrailNavigationHelper.buildShareDirectionsUrl(trail).contains("destination=38.57331,-109.51944"))
    }

    @Test
    fun `manifest does not declare foreground service or notification permissions`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        @Suppress("DEPRECATION")
        val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(
                    (PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES).toLong()
                )
            )
        } else {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES
            )
        }
        val requested = pkgInfo.requestedPermissions?.toSet().orEmpty()
        assertFalse(requested.contains(Manifest.permission.POST_NOTIFICATIONS))
        assertFalse(requested.contains("android.permission.FOREGROUND_SERVICE"))
        assertFalse(requested.contains("android.permission.FOREGROUND_SERVICE_LOCATION"))
        val services = pkgInfo.services.orEmpty()
        assertFalse(services.any { it.name.contains("RideRecordingService") })
        assertFalse(services.any { it.foregroundServiceType != 0 })
    }

    @Test
    fun `shared coil image loader initializes bounded memory and disk caches`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val loader = SingletrackImageLoader.get(context)
        assertNotNull(loader.memoryCache)
        assertNotNull(loader.diskCache)
    }

    @Test
    fun `gpx track storage round trips and clears active track`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = GpxTrackStorage(context)
        storage.clearActiveTrack()

        val track = GpxTrackParser.buildTrack(
            id = "stored-1",
            name = "Amasa Back Loop",
            fileName = "amasa.gpx",
            rawPoints = listOf(
                Triple(38.5320, -109.5910, 1210.0),
                Triple(38.5360, -109.5960, 1270.0),
                Triple(38.5400, -109.5920, 1245.0)
            ),
            waypoints = listOf(GpxWaypoint(38.5360, -109.5960, "Summit Lookout", "Scenic view")),
            isReversed = true
        )

        storage.saveActiveTrack(track)
        val restored = storage.loadActiveTrack()
        assertNotNull(restored)
        assertEquals("stored-1", restored?.id)
        assertEquals("Amasa Back Loop", restored?.name)
        assertEquals("amasa.gpx", restored?.fileName)
        assertEquals(3, restored?.points?.size)
        assertEquals(1, restored?.waypoints?.size)
        assertTrue(restored?.isReversed == true)
        assertEquals(track.totalDistanceMiles, restored?.totalDistanceMiles ?: 0.0, 0.001)

        storage.clearActiveTrack()
        assertNull(storage.loadActiveTrack())
    }

    @Test
    fun `ride screen displays gpx track navigator at bottom and triggers select and navigate actions`() {
        var selectClicked = false
        var navigateClicked = false
        var reverseClicked = false
        val toggledAudioValues = mutableListOf<Boolean>()

        val track = GpxTrackParser.buildTrack(
            id = "ui-track-1",
            name = "Whole Enchilada Sample",
            fileName = "enchilada.gpx",
            rawPoints = listOf(
                Triple(38.5700, -109.5500, 1200.0),
                Triple(38.5750, -109.5450, 1260.0)
            )
        )
        val idleNavState = GpxNavigationEngine.idleState(track)

        composeRule.setContent {
            SingletrackTheme {
                RideScreen(
                    activeRide = ActiveRideMetrics(),
                    recordedRides = emptyList(),
                    measurementUnit = MeasurementUnit.IMPERIAL,
                    bleSensorState = BleSensorUiState(),
                    healthConnectStatus = HealthConnectStatus(
                        availability = HealthConnectAvailability.UNAVAILABLE,
                        permissionsGranted = false
                    ),
                    syncingRideId = null,
                    hasLocationPermission = true,
                    onOpenSettings = {},
                    onOpenRecordingScreen = {},
                    onRequestLocationPermission = {},
                    onConnectHealthConnect = {},
                    onStartRide = {},
                    onPauseRide = {},
                    onResumeRide = {},
                    onFinishRide = {},
                    onDiscardRide = {},
                    onOpenRideDetail = {},
                    onShareRideGpx = {},
                    onSyncRide = {},
                    onDeleteRide = {},
                    gpxNavigation = idleNavState,
                    onSelectGpxFile = { selectClicked = true },
                    onStartGpxNavigation = { navigateClicked = true },
                    onReverseGpxTrack = { reverseClicked = true },
                    voiceCuesEnabled = true,
                    onToggleVoiceCues = { enabled -> toggledAudioValues.add(enabled) }
                )
            }
        }

        composeRule.onNodeWithTag("ride_screen_title")
            .assertIsDisplayed()
            .assertTextEquals("Ride Tracker")
        composeRule.onNodeWithText("Sync Rides with Google Fit").assertDoesNotExist()
        composeRule.onNodeWithText("Health Connect Unavailable").assertDoesNotExist()

        composeRule.onNodeWithTag("ride_voice_cues_button").performClick()

        composeRule.onNodeWithTag("ride_screen_list")
            .performScrollToNode(hasTestTag("gpx_track_navigator_section"))
        composeRule.onNodeWithTag("gpx_track_navigator_section").assertIsDisplayed()
        composeRule.onNodeWithTag("gpx_voice_cues_button").assertIsDisplayed().performClick()

        composeRule.onNodeWithTag("ride_screen_list")
            .performTouchInput { swipeUp() }

        composeRule.onNodeWithTag("gpx_live_guidance_panel").assertIsDisplayed()
        composeRule.onNodeWithTag("gpx_toggle_navigation_button").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("gpx_reverse_track_button").performClick()
        composeRule.onNodeWithTag("ride_screen_list").performTouchInput { swipeUp() }
        composeRule.onNodeWithTag("gpx_select_file_button").performClick()

        assertTrue(navigateClicked)
        assertTrue(reverseClicked)
        assertTrue(selectClicked)
        assertEquals(listOf(false, false), toggledAudioValues)
    }

    @Test
    fun `settings dialog displays google fit sync above about section and handles auto sync toggle`() {
        val toggledAutoSync = mutableListOf<Boolean>()

        composeRule.setContent {
            SingletrackTheme {
                UnitSettingsDialog(
                    currentUnit = MeasurementUnit.IMPERIAL,
                    onSelectUnit = {},
                    healthConnectStatus = HealthConnectStatus(
                        availability = HealthConnectAvailability.AVAILABLE,
                        permissionsGranted = true
                    ),
                    autoSyncEnabled = false,
                    onToggleAutoSync = { enabled -> toggledAutoSync.add(enabled) },
                    onDismiss = {}
                )
            }
        }

        val googleFitNode = composeRule.onNodeWithTag("settings_google_fit_section")
        val aboutNode = composeRule.onNodeWithTag("settings_about_section")
        googleFitNode.performScrollTo()
        googleFitNode.assertIsDisplayed()

        val googleFitY = googleFitNode.fetchSemanticsNode().positionInRoot.y
        val aboutY = aboutNode.fetchSemanticsNode().positionInRoot.y
        assertTrue(
            "Expected Google Fit sync ($googleFitY) to appear above About ($aboutY)",
            googleFitY < aboutY
        )

        aboutNode.performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("auto_sync_switch").performScrollTo().performClick()
        assertEquals(listOf(true), toggledAutoSync)
    }

    @Test
    fun `app preferences persist settings including auto sync and voice cues`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = AppPreferences(context)
        assertFalse(prefs.gpxFullVoiceNavigationEnabled)
        prefs.measurementUnit = MeasurementUnit.METRIC
        prefs.voiceCuesEnabled = false
        prefs.gpxFullVoiceNavigationEnabled = true
        prefs.autoSyncToHealthConnect = false
        prefs.defaultOfflineRegionId = "region_moab"

        val reloaded = AppPreferences(context)
        assertEquals(MeasurementUnit.METRIC, reloaded.measurementUnit)
        assertFalse(reloaded.voiceCuesEnabled)
        assertTrue(reloaded.gpxFullVoiceNavigationEnabled)
        assertFalse(reloaded.autoSyncToHealthConnect)
        assertEquals("region_moab", reloaded.defaultOfflineRegionId)
    }

    @Test
    fun `settings dialog places full voice GPX navigation below spoken audio cues and toggles it`() {
        val toggledFullVoiceNav = mutableListOf<Boolean>()

        composeRule.setContent {
            SingletrackTheme {
                UnitSettingsDialog(
                    currentUnit = MeasurementUnit.IMPERIAL,
                    onSelectUnit = {},
                    voiceCuesEnabled = true,
                    gpxFullVoiceNavigationEnabled = false,
                    onToggleGpxFullVoiceNavigation = { enabled -> toggledFullVoiceNav.add(enabled) },
                    onDismiss = {}
                )
            }
        }

        val voiceCuesCard = composeRule.onNodeWithTag("settings_voice_cues_card")
        val fullVoiceCard = composeRule.onNodeWithTag("settings_gpx_full_voice_nav_card")
        val fullVoiceSwitch = composeRule.onNodeWithTag("settings_gpx_full_voice_nav_switch")

        fullVoiceCard.performScrollTo().assertIsDisplayed()
        voiceCuesCard.performScrollTo().assertIsDisplayed()

        val voiceCuesY = voiceCuesCard.fetchSemanticsNode().positionInRoot.y
        val fullVoiceY = fullVoiceCard.fetchSemanticsNode().positionInRoot.y
        assertTrue(
            "Expected Full voice navigation ($fullVoiceY) below Spoken Audio Cues ($voiceCuesY)",
            fullVoiceY > voiceCuesY
        )

        fullVoiceSwitch.performScrollTo().performClick()
        assertEquals(listOf(true), toggledFullVoiceNav)
    }

    @Test
    fun `live ride cockpit centers Ride Computer and READY pill horizontally`() {
        composeRule.setContent {
            SingletrackTheme {
                LiveRideCockpitScreen(
                    activeRide = ActiveRideMetrics(),
                    measurementUnit = MeasurementUnit.IMPERIAL,
                    bleSensorState = BleSensorUiState(),
                    hasLocationPermission = true,
                    onClose = {},
                    onOpenSettings = {},
                    onRequestLocationPermission = {},
                    onStartRide = {},
                    onPauseRide = {},
                    onResumeRide = {},
                    onFinishRide = {},
                    onDiscardRide = {}
                )
            }
        }

        val titleBounds = composeRule.onNodeWithTag("cockpit_header_title")
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val statusBounds = composeRule.onNodeWithTag("cockpit_status_pill")
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val elapsedBounds = composeRule.onNodeWithTag("cockpit_elapsed_time")
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot

        val elapsedCenterX = (elapsedBounds.left + elapsedBounds.right) / 2f
        val titleCenterX = (titleBounds.left + titleBounds.right) / 2f
        val statusCenterX = (statusBounds.left + statusBounds.right) / 2f

        assertTrue(
            "Expected title center ($titleCenterX) to match elapsed center ($elapsedCenterX)",
            abs(titleCenterX - elapsedCenterX) < 2f
        )
        assertTrue(
            "Expected status pill center ($statusCenterX) to match elapsed center ($elapsedCenterX)",
            abs(statusCenterX - elapsedCenterX) < 2f
        )
    }
}
